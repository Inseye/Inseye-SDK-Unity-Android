/*
 * Last edit: 06.11.2023, 10:30
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
import android.util.Log;

import com.inseye.shared.R;
import com.inseye.shared.communication.ActionResult;
import com.inseye.shared.communication.Eye;
import com.inseye.shared.communication.IServiceCalibrationCallback;
import com.inseye.shared.communication.ISharedService;
import com.inseye.shared.communication.IntActionResult;
import com.inseye.shared.communication.TrackerAvailability;
import com.inseye.shared.communication.Version;
import com.inseye.shared.utils.PluggableServiceConnection;
import com.sun.jna.Pointer;
import com.unity3d.player.UnityPlayer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class UnitySDK {
    public static final String TAG = "AndroidUnitySDK";
    private static final SDKState sdkState = new SDKState();
    private static String errorMessage = "";
    private static ISharedService sharedService;
    private static EyeTrackerEventListener eventListener;
    private static CalibrationProcedure calibrationProcedure;
    private final static Object waitForServiceConnectionLock = new Object();
    private static final PluggableServiceConnection connection = new PluggableServiceConnection();

    static {
        resetConnectionObject();
    }


    /**
     * Called by UnitySDK to initialize SDK
     *
     * @return one of ErrorCodes
     */
    public static int initialize(String listenerGameObjectName, long timeout) throws Exception {
        Log.d(TAG, "initialize, timeout = " + timeout + "callback object name = " + listenerGameObjectName);
        sdkState.setUnityListener(listenerGameObjectName);
        eventListener = new EyeTrackerEventListener(listenerGameObjectName);

        if (sdkState.isInState(SDKState.CONNECTED)) {
            return ErrorCodes.SDKAlreadyConnected;
        }
        Activity currentActivity = UnityPlayer.currentActivity;
        try {
            CompletableFuture<ISharedService> future = new CompletableFuture<>();
            connection.setServiceConnectedDelegate((name, service) -> {
                future.complete(ISharedService.Stub.asInterface(service));
            });
            connection.setNullBindingDelegate((name -> {
                future.completeExceptionally(new Exception("Null binding exception"));
            }));
            boolean connectedSuccessfully = currentActivity.getApplicationContext()
                    .bindService(createBindToServiceIntent(currentActivity),
                            connection, Context.BIND_AUTO_CREATE);
            if (!connectedSuccessfully)
                return ErrorCodes.FailedToBindToService;
            try {
                sharedService = future.get(timeout, TimeUnit.MILLISECONDS);
            } catch (TimeoutException timeoutException) {
                return ErrorCodes.InitializationTimeout;
            }
            finally {
                resetConnectionObject();
            }
            connection.setServiceDisconnectedDelegate((name) -> {
                // service is temporarily disconnected, but should reconnect in the future
                sdkState.setState(SDKState.NOT_CONNECTED);
                sharedService = null;
                if (null != eventListener)
                    eventListener.setTrackerAvailability(TrackerAvailability.Disconnected);
            });
            connection.setServiceConnectedDelegate((name, service) ->
            {
                sharedService = ISharedService.Stub.asInterface(service);
                sdkState.setState(SDKState.CONNECTED);
                try {
                    // NOTE: no idea how can it throw at this point
                    if (null != eventListener)
                        eventListener.setTrackerAvailability(sharedService.getTrackerAvailability());
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            });
            connection.setBindingDiedDelegate((name) -> {
                // service disconnected, and will not reconnect without action
                sdkState.setState(SDKState.NOT_CONNECTED);
                sharedService = null;
                if (null != eventListener)
                    eventListener.setTrackerAvailability(TrackerAvailability.Disconnected);
                // unbind based on Android documentation
                Activity unityActivity = UnityPlayer.currentActivity;
                unityActivity.getApplicationContext().unbindService(connection);
                // try to rebind immediately
                unityActivity.getApplicationContext().bindService(createBindToServiceIntent(unityActivity),
                        connection, Context.BIND_AUTO_CREATE);
            });
            connection.setNullBindingDelegate((name) -> {
                // service returned null binding and will not return anything else (probably)
                sdkState.setState(SDKState.NOT_CONNECTED);
                sharedService = null;
                Activity unityActivity = UnityPlayer.currentActivity;
                unityActivity.getApplicationContext().unbindService(connection);
            });
            return ErrorCodes.Successful;
        } catch (Exception e) {
            return HandleException(e);
        } finally {
            if (!sdkState.isInState(SDKState.CONNECTED)) {
                eventListener = null;
                sdkState.clearUnityGameObject();
            }
        }
    }

    public static int dispose() {
        Log.d(TAG, "dispose");
        try {
            if (sdkState.isInState(SDKState.NOT_CONNECTED))
                return ErrorCodes.Successful;
            sdkState.setState(SDKState.NOT_CONNECTED);
            Log.d(TAG, "UnitySDK unbound from service");
            UnityPlayer.currentActivity.getApplicationContext().unbindService(connection);
        } catch (Exception e) {
            return HandleException(e);
        } finally {
            eventListener = null;
            sdkState.clearUnityGameObject();
        }
        return ErrorCodes.Successful;
    }

    /**
     * Called by UnitySDK to get gaze data udp socket
     *
     * @return eye tracker availability
     */
    public static int getEyeTrackerAvailability() throws Exception {
        Log.d(TAG, "getEyeTrackerAvailability");
        if (!sdkState.isInState(SDKState.CONNECTED))
            throw new Exception("SDK is not connected to service");
        return sharedService.getTrackerAvailability().value;
    }

    /**
     * Called by UnitySDK to get gaze data udp socket
     *
     * @param portIntPointer pointer to in where port can be written
     * @return on of ErrorCode values
     */
    public static int getEyeTrackingDataStreamPort(long portIntPointer) {
        Log.d(TAG, "getEyeTrackingDataStreamPort");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.SDKIsNotConnectedToService;
        try {
            IntActionResult portResult = sharedService.startStreamingGazeData();
            sdkState.addState(SDKState.ATTACHED_TO_GAZE_DATA_STREAM);
            if (!portResult.success) {
                setErrorMessage(portResult.errorMessage);
                return ErrorCodes.UnknownErrorCheckErrorMessage;
            } else {
                Pointer pointer = new Pointer(portIntPointer);
                pointer.setInt(0, portResult.value);
                return ErrorCodes.Successful;
            }
        } catch (Exception exception) {
            Log.e(TAG, "Unhandled exception occurred while attempting to get access to shared memory", exception);
            return HandleException(exception);
        }
    }

    /**
     * Called by UnitySDK to inform service that client no longer need gaze stream
     *
     * @return one of ErrorCode values
     */
    public static int stopEyeTrackingDataStream() {
        Log.d(TAG, "stopEyeTrackingDataStream");
        if (sdkState.isInState(SDKState.ATTACHED_TO_GAZE_DATA_STREAM)) {
            try {
                sharedService.stopStreamingGazeData();
                return ErrorCodes.Successful;
            } catch (Exception exception) {
                Log.e(TAG, "An error occurred when stopping eye tracking data stream.");
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
    public static int subscribeToEvents() {
        Log.d(TAG, "subscribeToEvents");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.SDKIsNotConnectedToService;
        if (sdkState.isInState(SDKState.SUBSCRIBED_TO_EVENTS))
            return ErrorCodes.AlreadySubscribedToEvents;
        try {
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
        Log.d(TAG, "unsubscribeFromEvents");
        if (!sdkState.isInState(SDKState.SUBSCRIBED_TO_EVENTS))
            return ErrorCodes.Successful;
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.Successful;
        try {
            sharedService.unsubscribeFromEyetrackerEvents();
            sdkState.removeState(SDKState.SUBSCRIBED_TO_EVENTS);
            Log.i(TAG, "Unsubscribed from hardware events");
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to unsubscribe from hardware events");
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
    public static int startCalibrationProcedure(long calibrationRequestPointer, long calibrationResponsePointer, long calibrationStatusPointer, long pointIndexPointer) throws RemoteException {
        Log.d(TAG, "startCalibrationProcedure");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.SDKIsNotConnectedToService;
        if (sdkState.isInState(SDKState.CALIBRATING))
            return ErrorCodes.AnotherCalibrationIsOngoing;
        calibrationProcedure = new CalibrationProcedure(calibrationRequestPointer, calibrationResponsePointer, calibrationStatusPointer, pointIndexPointer);
        ICalibrationStatusListener listener = (oldStatus, newStatus) -> {
            if (newStatus == CalibrationStatus.FinishedSuccessfully || newStatus == CalibrationStatus.FinishedFailed)
                sdkState.removeState(SDKState.CALIBRATING);
        };
        calibrationProcedure.setCalibrationStatusListener(listener);
        ActionResult actionResult = new ActionResult();
        IServiceCalibrationCallback serviceCallback = sharedService.startCalibrationProcedure(actionResult, calibrationProcedure.getCalibrationCallback());
        if (!actionResult.successful) {
            setErrorMessage(actionResult.errorMessage);
            return ErrorCodes.UnknownErrorCheckErrorMessage;
        }
        calibrationProcedure.setServiceCalibrationCallback(serviceCallback);
        sdkState.addState(SDKState.CALIBRATING);
        return ErrorCodes.Successful;
    }


    /**
     * Called by UnitySDK to inform calibration procured that client is ready to display calibration point
     *
     * @return one of ErrorCode values
     */
    public static int setReadyToDisplayCalibrationPoint() {
        Log.d(TAG, "setReadyToDisplayCalibrationPoint");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.SDKIsNotConnectedToService;
        if (!sdkState.isInState(SDKState.CALIBRATING))
            return ErrorCodes.NoCalibrationIsOngoing;
        ActionResult result = calibrationProcedure.markReadyForPointDisplay();
        if (result.successful)
            return ErrorCodes.Successful;
        else if (!result.errorMessage.equals("")) {
            setErrorMessage(result.errorMessage);
            return ErrorCodes.UnknownErrorCheckErrorMessage;
        } else
            return ErrorCodes.UnknownError;
    }

    /**
     * Called by UnitySKD to abort current calibration
     *
     * @return status code
     */
    public static int abortCalibrationProcedure() {
        Log.d(TAG, "abortCalibrationProcedure");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.SDKIsNotConnectedToService;
        if (!sdkState.isInState(SDKState.CALIBRATING))
            return ErrorCodes.NoCalibrationIsOngoing;
        if (null != calibrationProcedure && !calibrationProcedure.isCalibrationFinished()) {
            sdkState.removeState(SDKState.CALIBRATING);
            ActionResult actionResult = calibrationProcedure.abortCalibration();
            if (!actionResult.successful) {
                setErrorMessage(actionResult.errorMessage);
                return ErrorCodes.UnknownErrorCheckErrorMessage;
            }
        }
        return ErrorCodes.Successful;
    }

    /**
     * Called by UnitySKD to abort current calibration
     *
     * @return string serialized versions of firmware and service separated with '\n'
     */
    public static String getVersions() throws Exception {
        Log.d(TAG, "getVersions");
        if (!sdkState.isInState(SDKState.CONNECTED))
            throw new Exception("SDK is not connected to service");
        Version serviceVersion = new Version();
        Version firmwareVersion = new Version();
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
        Log.d(TAG, "getDominantEye");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return Eye.BOTH.value;
        return sharedService.getDominantEye().value;
    }

    private static int HandleException(Exception exc) {
        if (null != exc.getMessage()) {
            setErrorMessage(exc.getMessage());
            Log.e(TAG, exc.getMessage());
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
            Log.d(TAG, "Default handler for: onServiceConnected");
        });
        connection.setBindingDiedDelegate((componentName) -> {
            Log.d(TAG, "Default handler for: onBindingDied");
        });
        connection.setNullBindingDelegate((componentName) -> {
            Log.d(TAG, "Default handler for: onNullBinding");
        });
        connection.setServiceDisconnectedDelegate((componentName) -> {
            Log.d(TAG, "Default handler for: onServiceDisconnected");
        });
    }

}
