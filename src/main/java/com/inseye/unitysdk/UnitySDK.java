/*
 * Last edit: 28.11.2023, 16:59
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.inseye.shared.R;
import com.inseye.shared.communication.ActionResult;
import com.inseye.shared.communication.Eye;
import com.inseye.shared.communication.IServiceCalibrationCallback;
import com.inseye.shared.communication.ISharedService;
import com.inseye.shared.communication.IntActionResult;
import com.inseye.shared.communication.TrackerAvailability;
import com.inseye.shared.communication.Version;
import com.inseye.shared.utils.IPluggableServiceConnection;
import com.inseye.shared.utils.PluggableServiceConnection;
import com.inseye.unitysdk.tests.ServiceConnectionProxy;
import com.sun.jna.Pointer;
import com.unity3d.player.UnityPlayer;

public class UnitySDK {
    public static final String TAG = "AndroidUnitySDK";
    private static final SDKState sdkState = new SDKState();
    private static String errorMessage = "";
    @Nullable
    private static ISharedService sharedService;
    @Nullable
    private static EyeTrackerEventListener eventListener;
    private static CalibrationProcedure calibrationProcedure;
    private static IPluggableServiceConnection connection = new PluggableServiceConnection();
    private static final Object lockObject = new Object();

    static {
        resetConnectionObject();
    }

    /*
     * Called by UnitySDKTestProxy to inject proxy for test purposes.
     *
     * @return Service proxy
     */
    public static ServiceConnectionProxy injectServiceProxy() {
        Log.i("injectServiceProxy");
        if (connection instanceof ServiceConnectionProxy) {
            Log.i("returning already injected service proxy");
            return (ServiceConnectionProxy) connection;
        }
        ServiceConnectionProxy proxy = new ServiceConnectionProxy(connection, sharedService);
        connection = proxy;
        sharedService = proxy;
        Log.i("injected service proxy");
        return proxy;
    }

    public static void setLoggingLevel(int level) {
        if (level == Log.Level.VERBOSE.value)
            Log.CurrentLevel = Log.Level.VERBOSE;
        else if (level == Log.Level.INFO.value) {
            Log.CurrentLevel = Log.Level.INFO;
        } else if (level == Log.Level.DEBUG.value) {
            Log.CurrentLevel = Log.Level.DEBUG;
        } else if (level == Log.Level.WARN.value) {
            Log.CurrentLevel = Log.Level.WARN;
        } else {
            Log.CurrentLevel = Log.Level.ERROR;
        }
    }

    /*
     * Called from UnitySDKTestProxy to remove proxy.
     *
     */
    public static void revokeServiceProxy() {

        if (!(connection instanceof ServiceConnectionProxy))
            return;
        ServiceConnectionProxy proxy = (ServiceConnectionProxy) connection;
        sharedService = proxy.getSharedService();
        connection = proxy.getServiceConnection();
        Log.i("removed service proxy");
    }

    /**
     * Called by UnitySDK to initialize SDK
     *
     * @return one of ErrorCodes
     */
    public static int initialize(long statePointer, long timeout) throws Exception {
        Log.d("initialize, timeout = " + timeout + "state pointer = " + statePointer);
        sdkState.setUnityPointer(statePointer);

        if (sdkState.isInState(SDKState.CONNECTED)) {
            return ErrorCodes.SDKAlreadyConnected;
        }
        Activity currentActivity = UnityPlayer.currentActivity;
        sharedService = null;
        try {
            connection.setServiceConnectedDelegate((name, service) -> {
                Log.i("Service connected during initialization.");
                synchronized (lockObject) {
                    sharedService = ISharedService.Stub.asInterface(service);
                    lockObject.notifyAll();
                }
            });
            connection.setNullBindingDelegate((name -> {
                Log.e("Service connected during initialization with null binding.");
                synchronized (lockObject) {
                    sharedService = null;
                    lockObject.notifyAll();
                }
            }));
            boolean connectedSuccessfully = currentActivity.getApplicationContext()
                    .bindService(createBindToServiceIntent(currentActivity),
                            connection, Context.BIND_AUTO_CREATE);
            if (!connectedSuccessfully)
                return ErrorCodes.FailedToBindToService;
            try {
                synchronized (lockObject) {
                    if (null == sharedService)
                        lockObject.wait(timeout);
                    if (null == sharedService)
                        return ErrorCodes.InitializationTimeout;
                }
            } catch (Exception exception) {
                return HandleException(exception);
            } finally {
                resetConnectionObject();
            }
            connection.setServiceDisconnectedDelegate((name) -> {
                Log.d("Service disconnected.");
                // service is temporarily disconnected, but should reconnect in the future
                sdkState.setState(SDKState.NOT_CONNECTED);
                sharedService = null;
                if (null != eventListener)
                    eventListener.setTrackerAvailability(TrackerAvailability.Disconnected);
                if (null != calibrationProcedure)
                    calibrationProcedure.onServiceDisconnected();
            });
            connection.setServiceConnectedDelegate((name, service) ->
            {
                Log.d("Service reconnected.");
                sharedService = ISharedService.Stub.asInterface(service);
                sdkState.setState(SDKState.CONNECTED);
                try {
                    // NOTE: no idea how can it throw at this point
                    if (null != eventListener) {
                        assert sharedService != null;
                        eventListener.setTrackerAvailability(sharedService.getTrackerAvailability());
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
            connection.setBindingDiedDelegate((name) -> {
                Log.d("Service service binding died.");
                // service disconnected, and will not reconnect without action
                sdkState.setState(SDKState.NOT_CONNECTED);
                sharedService = null;
                if (null != eventListener)
                    eventListener.setTrackerAvailability(TrackerAvailability.Disconnected);
                if (null != calibrationProcedure)
                    calibrationProcedure.onServiceDisconnected();
                // unbind based on Android documentation
                Activity unityActivity = UnityPlayer.currentActivity;
                unityActivity.getApplicationContext().unbindService(connection);
                // try to rebind immediately
                unityActivity.getApplicationContext().bindService(createBindToServiceIntent(unityActivity),
                        connection, Context.BIND_AUTO_CREATE);
            });
            connection.setNullBindingDelegate((name) -> {
                Log.e("Service service returned null binding.");
                // service returned null binding and will not return anything else (probably)
                sdkState.setState(SDKState.NOT_CONNECTED);
                sharedService = null;
                Activity unityActivity = UnityPlayer.currentActivity;
                unityActivity.getApplicationContext().unbindService(connection);
            });
            sdkState.setState(SDKState.CONNECTED);
            return ErrorCodes.Successful;
        } catch (Exception e) {
            return HandleException(e);
        } finally {
            if (!sdkState.isInState(SDKState.CONNECTED)) {
                sdkState.clearUnityPointer();
            }
        }
    }

    public static int dispose() {
        Log.d("dispose");
        try {
            if (sdkState.isInState(SDKState.NOT_CONNECTED))
                return ErrorCodes.Successful;
            assert sharedService != null;
            sharedService.unsubscribeFromEyetrackerEvents();
            sdkState.setState(SDKState.NOT_CONNECTED);
            Log.d("UnitySDK unbound from service");
            UnityPlayer.currentActivity.getApplicationContext().unbindService(connection);
        } catch (Exception e) {
            return HandleException(e);
        } finally {
            eventListener = null;
            sdkState.clearUnityPointer();
        }
        return ErrorCodes.Successful;
    }

    /**
     * Called by UnitySDK to get gaze data udp socket
     *
     * @return eye tracker availability
     */
    public static int getEyeTrackerAvailability() throws Exception {
        Log.d("getEyeTrackerAvailability");
        if (!sdkState.isInState(SDKState.CONNECTED))
            throw new Exception("SDK is not connected to service");
        assert sharedService != null;
        return sharedService.getTrackerAvailability().value;
    }

    /**
     * Called by UnitySDK to get gaze data udp socket
     *
     * @param portIntPointer pointer to in where port can be written
     * @return on of ErrorCode values
     */
    public static int getEyeTrackingDataStreamPort(long portIntPointer) {
        Log.d("getEyeTrackingDataStreamPort");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.SDKIsNotConnectedToService;
        try {
            assert sharedService != null;
            IntActionResult portResult = sharedService.startStreamingGazeData();
            if (!portResult.success) {
                setErrorMessage(portResult.errorMessage);
                return ErrorCodes.UnknownErrorCheckErrorMessage;
            } else {
                sdkState.addState(SDKState.ATTACHED_TO_GAZE_DATA_STREAM);
                Pointer pointer = new Pointer(portIntPointer);
                pointer.setInt(0, portResult.value);
                return ErrorCodes.Successful;
            }
        } catch (Exception exception) {
            Log.e("Unhandled exception occurred while attempting to get access to shared memory", exception);
            return HandleException(exception);
        }
    }

    /**
     * Called by UnitySDK to inform service that client no longer need gaze stream
     *
     * @return one of ErrorCode values
     */
    public static int stopEyeTrackingDataStream() {
        Log.d("stopEyeTrackingDataStream");
        if (sdkState.isInState(SDKState.ATTACHED_TO_GAZE_DATA_STREAM)) {
            try {
                assert sharedService != null;
                sharedService.stopStreamingGazeData();
                return ErrorCodes.Successful;
            } catch (Exception exception) {
                Log.e("An error occurred when stopping eye tracking data stream.");
                exception.printStackTrace();
                return HandleException(exception);
            } finally {
                sdkState.removeState(SDKState.ATTACHED_TO_GAZE_DATA_STREAM);
            }
        }
        return ErrorCodes.Successful;
    }

    /**
     * Called by UnitySDK to open events channel
     *
     * @return one of ErrorCode values
     */
    public static int subscribeToEvents(String listenerObjectName) {
        Log.d("subscribeToEvents");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.SDKIsNotConnectedToService;
        if (sdkState.isInState(SDKState.SUBSCRIBED_TO_EVENTS))
            return ErrorCodes.AlreadySubscribedToEvents;
        try {
            assert sharedService != null;
            eventListener = new EyeTrackerEventListener(listenerObjectName);
            sharedService.subscribeToEyetrackerEvents(eventListener);
            sdkState.addState(SDKState.SUBSCRIBED_TO_EVENTS);
        } catch (RemoteException e) {
            return HandleException(e);
        }
        return ErrorCodes.Successful;
    }

    /**
     * Called by UnitySDK to close event channel
     *
     * @return one of ErrorCode values
     */
    public static int unsubscribeFromEvents() {
        Log.d("unsubscribeFromEvents");
        if (!sdkState.isInState(SDKState.SUBSCRIBED_TO_EVENTS))
            return ErrorCodes.Successful;
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.Successful;
        try {
            assert sharedService != null;
            sharedService.unsubscribeFromEyetrackerEvents();
            sdkState.removeState(SDKState.SUBSCRIBED_TO_EVENTS);
            Log.i("Unsubscribed from hardware events");
        } catch (RemoteException e) {
            Log.e("Failed to unsubscribe from hardware events");
            return HandleException(e);
        }
        return ErrorCodes.Successful;
    }

    /**
     * Called by UnitySDK to begin calibration procedure
     *
     * @param calibrationRequestPointer  pointer to struct where java can write points to display in unity
     * @param calibrationResponsePointer pointer to struct from which java can read status of display
     * @param calibrationStatusPointer   pointer to int where status calibration status can be updated
     * @param pointIndexPointer          pointer to int that is incremented each time a new point is presented
     * @return one of ErrorCode values
     */
    public static CalibrationProcedure startCalibrationProcedure(long calibrationRequestPointer, long calibrationResponsePointer, long calibrationStatusPointer, long pointIndexPointer) throws Exception {
        Log.d("startCalibrationProcedure");
        if (!sdkState.isInState(SDKState.CONNECTED))
            throw new Exception("SDK is not connected to the service.");
        if (sdkState.isInState(SDKState.CALIBRATING))
            throw new Exception("SDK is calibrating");
        calibrationProcedure = new CalibrationProcedure(calibrationRequestPointer, calibrationResponsePointer, calibrationStatusPointer, pointIndexPointer);
        ICalibrationStatusListener listener = (oldStatus, newStatus) -> {
            if (newStatus == CalibrationStatus.FinishedSuccessfully || newStatus == CalibrationStatus.FinishedFailed)
                sdkState.removeState(SDKState.CALIBRATING);
        };
        calibrationProcedure.setCalibrationStatusListener(listener);
        ActionResult actionResult = new ActionResult();
        assert sharedService != null;
        IServiceCalibrationCallback serviceCallback = sharedService.startCalibrationProcedure(actionResult, calibrationProcedure.getCalibrationCallback());
        if (!actionResult.successful) {
            throw new Exception(actionResult.errorMessage);
        }
        calibrationProcedure.setServiceCalibrationCallback(serviceCallback);
        sdkState.addState(SDKState.CALIBRATING);
        return calibrationProcedure;
    }

    /**
     * Called by UnitySKD to abort current calibration
     *
     * @return string serialized versions of firmware and service separated with '\n'
     */
    public static String getVersions() throws Exception {
        Log.d("getVersions");
        if (!sdkState.isInState(SDKState.CONNECTED))
            throw new Exception("SDK is not connected to service");
        Version serviceVersion = new Version();
        Version firmwareVersion = new Version();
        assert sharedService != null;
        sharedService.getVersions(serviceVersion, firmwareVersion);
        return serviceVersion.toString() + '\n' + firmwareVersion;
    }

    /**
     * Called by UnitySDK to get last error message
     *
     * @return last error message
     */
    public static String getLastErrorMessage() {
        return errorMessage;
    }

    public static void setErrorMessage(String errorMessage) {
        UnitySDK.errorMessage = errorMessage;
    }

    /**
     * Called by UnitySKD to abort current calibration
     *
     * @return int representation of dominant eye
     */
    public static int getDominantEye() throws RemoteException {
        Log.d("getDominantEye");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return Eye.BOTH.value;
        assert sharedService != null;
        return sharedService.getDominantEye().value;
    }

    private static int HandleException(Exception exc) {
        if (null != exc.getMessage()) {
            setErrorMessage(exc.getMessage());
            Log.e(exc.getMessage());
            return ErrorCodes.UnknownErrorCheckErrorMessage;
        }
        return ErrorCodes.UnknownError;
    }

    private static Intent createBindToServiceIntent(Activity activity) {
        Resources res = activity.getResources();
        Intent serviceIntent = new Intent();
        ComponentName component = new ComponentName(res.getString(R.string.service_package_name), res.getString(R.string.service_class_name));
        serviceIntent.setComponent(component);
        return serviceIntent;
    }

    private static void resetConnectionObject() {
        connection.setServiceConnectedDelegate((name, service) -> {
            Log.i("Default handler for: onServiceConnected");
        });
        connection.setBindingDiedDelegate((componentName) -> {
            Log.i("Default handler for: onBindingDied");
        });
        connection.setNullBindingDelegate((componentName) -> {
            Log.i("Default handler for: onNullBinding");
        });
        connection.setServiceDisconnectedDelegate((componentName) -> {
            Log.i("Default handler for: onServiceDisconnected");
        });
    }

}
