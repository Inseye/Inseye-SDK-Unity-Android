/*
 * Last edit: 06.10.2023, 10:54
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
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.inseye.shared.R;
import com.inseye.shared.communication.ActionResult;
import com.inseye.shared.communication.Eye;
import com.inseye.shared.communication.IEyetrackerEventListener;
import com.inseye.shared.communication.IServiceCalibrationCallback;
import com.inseye.shared.communication.ISharedService;
import com.inseye.shared.communication.IntActionResult;
import com.inseye.shared.communication.StringActionResult;
import com.inseye.shared.communication.TrackerAvailability;
import com.inseye.shared.communication.Version;
import com.sun.jna.Pointer;
import com.unity3d.player.UnityPlayer;

import java.util.Arrays;

public class UnitySDK {
    public static final String TAG = "AndroidUnitySDK";
    private static final SDKState sdkState = new SDKState();
    private static String errorMessage = "";
    private static ISharedService sharedService;
    private static CalibrationProcedure calibrationProcedure;
    private final static Object waitForServiceConnectionLock = new Object();

    /**
     * Called by UnitySDK to initialize SDK
     *
     * @return one of ErrorCodes
     */
    public static int initialize(long stateIntPointer, long timeout) throws Exception {
        Log.d(TAG, "initialize, timeout = " + timeout + " pointer = " + stateIntPointer);
        sdkState.addUnityPointer(stateIntPointer);
        if (sdkState.isInState(SDKState.CONNECTED)) {
            return ErrorCodes.SDKAlreadyConnected;
        }
        Activity currentActivity = UnityPlayer.currentActivity;
        Resources res = currentActivity.getResources();

        Intent serviceIntent = new Intent();
        ComponentName component = new ComponentName(res.getString(R.string.service_package_name), res.getString(R.string.service_class_name));
        serviceIntent.setComponent(component);

        boolean connectedSuccessfully = currentActivity.getApplicationContext().bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
        if (!connectedSuccessfully)
            return ErrorCodes.FailedToBindToService;
        synchronized (waitForServiceConnectionLock) {
            try {
                if (!sdkState.isInState(SDKState.CONNECTED)) {
                    Log.d(TAG, "Service is not connect, waiting.");
                    waitForServiceConnectionLock.wait(timeout); // TODO: test in real world how long timeout is manageable
                }
                if (!sdkState.isInState(SDKState.CONNECTED)) {
                    sdkState.clearUnityPointer(stateIntPointer);
                    Log.e(TAG, "Failed to initialize SKD after timeout.");
                    return ErrorCodes.InitializationTimeout;
                }
            } catch (Exception e) {
                sdkState.clearUnityPointer(stateIntPointer);
                return HandleException(e);
            }
        }
        return ErrorCodes.Successful;
    }

    public static int dispose(long stateIntPointer) {
        Log.d(TAG, "dispose");
        if (sdkState.isInState(SDKState.NOT_CONNECTED))
            return ErrorCodes.Successful;
        if (sdkState.getListenersCount() > 1)
            return ErrorCodes.Successful;
        sdkState.setState(SDKState.NOT_CONNECTED);
        sdkState.clearUnityPointer(stateIntPointer);
        try {
            Log.d(TAG, "UnitySDK unbound from service");
            UnityPlayer.currentActivity.getApplicationContext().unbindService(connection);
        } catch (Exception e) {
            return HandleException(e);
        }
        return ErrorCodes.Successful;
    }

    public static int getEyeTrackerAvailability() throws Exception {
        Log.d(TAG, "getEyeTrackerAvailability");
        if (!sdkState.isInState(SDKState.CONNECTED))
            throw new Exception("SDK is not connected to service");
        return sharedService.getTrackerAvailability().ordinal();
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
            }
            finally {
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

    /**
     * Called from UnitySDK to get information if eye tracker is calibrated
     *
     * @return true if eye tracker is calibrated according to service
     */
    public static boolean isEyeTrackerCalibrated() throws RemoteException {
        Log.d(TAG, "isEyeTrackerCalibrated");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return false;
        return sharedService.isCalibrated();
    }


    private static final ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            sharedService = ISharedService.Stub.asInterface(service);
            synchronized (waitForServiceConnectionLock) {
                sdkState.setState(SDKState.CONNECTED);
                waitForServiceConnectionLock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
            sdkState.setState(SDKState.NOT_CONNECTED);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            // TODO: Maybe inform unity that binding died
            Log.d(TAG, "onBindingDied");
            sdkState.setState(SDKState.NOT_CONNECTED);
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.d(TAG, "onNullBinding");
            sdkState.setState(SDKState.NOT_CONNECTED);
        }
    };

    private final static IEyetrackerEventListener eventListener = new IEyetrackerEventListener.Stub() {
        private static final String gameObjectName = "AndroidMessageListener";

        @Override
        public void handleTrackerAvailabilityChanged(TrackerAvailability availability) {
            Log.d(TAG, "handleTrackerAvailabilityChanged: " + availability.toString());
            UnityPlayer.UnitySendMessage(gameObjectName, "InvokeEyeTrackerAvailabilityChanged", Integer.toString(availability.ordinal()));
        }
    };

    private static int HandleException(Exception exc) {
        if (null != exc.getMessage()) {
            setErrorMessage(exc.getMessage());
            Log.e(TAG, exc.getMessage());
            return ErrorCodes.UnknownErrorCheckErrorMessage;
        }
        return ErrorCodes.UnknownError;
    }

}
