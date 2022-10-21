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
import com.inseye.shared.communication.CalibrationPoint;
import com.inseye.shared.communication.GazeData;
import com.inseye.shared.communication.IEyetrackerEventListener;
import com.inseye.shared.communication.ISharedService;
import com.inseye.shared.communication.IntActionResult;
import com.inseye.shared.communication.TrackerAvailability;
import com.sun.jna.Pointer;
import com.unity3d.player.UnityPlayer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UnitySDK {
    public static final String TAG = "AndroidUnitySDK";
    private static final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[GazeData.SERIALIZER.getSizeInBytes()]);
    private static final GazeData gazeData = new GazeData();
    private static final SDKState sdkState = new SDKState();
    private static String errorMessage = "";
    private static ISharedService sharedService;
    private static CalibrationProcedure calibrationProcedure;
    private final static Object waitForServiceConnectionLock = new Object();

    /**
     * Initializes SDK
     *
     * @return one of ErrorCodes
     */
    public static int initialize() {
        Log.d(TAG, "initialize");
        if (sdkState.isInState(SDKState.CONNECTED)) {
            return ErrorCodes.SDKAlreadyConnected;
        }
        Activity currentActivity = UnityPlayer.currentActivity;
        Resources res = currentActivity.getResources();

        Intent serviceIntent = new Intent();
        ComponentName component = new ComponentName(res.getString(R.string.service_package_name), res.getString(R.string.service_class_name));
        serviceIntent.setComponent(component);

        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        boolean connectedSuccessfully = currentActivity.getApplicationContext().bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
        if (!connectedSuccessfully)
            return ErrorCodes.FailedToBindToService;
        synchronized (waitForServiceConnectionLock) {
            try {
                waitForServiceConnectionLock.wait(1000); // TODO: test in real world how long timeout is manageable
            } catch (InterruptedException e) {
                return ErrorCodes.SDKIsNotConnectedToService;
            }
        }
        return ErrorCodes.Successful;
    }

    public static int dispose() {
        Log.d(TAG, "dispose");
        if (sdkState.isInState(SDKState.NOT_CONNECTED))
            return ErrorCodes.Successful;
        sdkState.setState(SDKState.NOT_CONNECTED);
        try {
            UnityPlayer.currentActivity.getApplicationContext().unbindService(connection);
        }
        catch (Exception e)
        {
            setErrorMessage(e.getMessage());
            return ErrorCodes.UnknownErrorCheckErrorMessage;
        }
        return ErrorCodes.Successful;
    }

    public static int getEyetrackerAvailability() throws Exception {
        Log.d(TAG, "getEyetrackerAvailability");
        if (!sdkState.isInState(SDKState.CONNECTED))
            throw new Exception("SDK is not connected to service");
        return sharedService.getTrackerAvailability().ordinal();
    }

    public static int getEyetrackingDataStreamPort(long portIntPointer) {
        Log.d(TAG, "getEyetrackingDataStreamPort");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.SDKIsNotConnectedToService;
        try {
            IntActionResult portResult = sharedService.startStreamingGazeData();
            sdkState.addState(SDKState.ATTACHED_TO_GAZE_DATA_STREAM);
            if (!portResult.success) {
                errorMessage = portResult.errorMessage;
                return ErrorCodes.UnknownErrorCheckErrorMessage;
            }
            else {
                Pointer pointer = new Pointer(portIntPointer);
                pointer.setInt(0, portResult.value);
                return ErrorCodes.Successful;
            }
        } catch (RemoteException remoteException) {
            Log.e(TAG, "Unhandled exception occurred while attempting to get access to shared memory", remoteException);
            errorMessage = "Unhandled exception occurred while attempting to get access to port value, check logs with tag: " + TAG;
            return ErrorCodes.UnknownErrorCheckErrorMessage;
        }
    }

    /**
     * Called by UnitySDK to open events channel
     * @return one of ErrorCode values
     */
    public static int subscribeToEvents() {
        Log.d(TAG, "subscribeToEvents");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.SDKIsNotConnectedToService;
        if (!sdkState.isInState(SDKState.SUBSCRIBED_TO_EVENTS))
            return ErrorCodes.AlreadySubscribedToEvents;
        try {
            sharedService.subscribeToEyetrackerEvents(eventListener);
        }
        catch (RemoteException e) {
            setErrorMessage(e.getMessage());
            return ErrorCodes.UnknownErrorCheckErrorMessage;
        }
        return ErrorCodes.Successful;
    }

    public static int unsubscribeFromEvents()
    {
        Log.d(TAG, "unsubscribeFromEvents");
        if (!sdkState.isInState(SDKState.SUBSCRIBED_TO_EVENTS))
            return ErrorCodes.Successful;
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.Successful;
        try {
            sharedService.unsubscribeFromEyetrackerEvents();
            sdkState.removeState(SDKState.SUBSCRIBED_TO_EVENTS);
        }
        catch (RemoteException e)
        {
            setErrorMessage(e.getMessage());
            return ErrorCodes.UnknownErrorCheckErrorMessage;
        }
        return ErrorCodes.Successful;
    }
    /**
     * Called by UnitySDK to begin calibration procedure
     *
     * @param calibrationRequestPointer  pointer to struct where java can write points to display in unity
     * @param calibrationResponsePointer pointer to struct from which java can read status of display
     * @param calibrationStatusPointer pointer to int where status calibration status can be updated
     * @return one of ErrorCode values
     */
    public static int startCalibrationProcedure(long calibrationRequestPointer, long calibrationResponsePointer, long calibrationStatusPointer) throws RemoteException {
        Log.d(TAG, "startCalibrationProcedure");
        if (!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.SDKIsNotConnectedToService;
        if (sdkState.isInState(SDKState.CALIBRATING))
            return ErrorCodes.AnotherCalibrationIsOngoing;
        calibrationProcedure = new CalibrationProcedure(calibrationRequestPointer, calibrationResponsePointer, calibrationStatusPointer);
        CalibrationPoint initialPoint = new CalibrationPoint();
        ActionResult actionResult = sharedService.startCalibrationProcedure(calibrationProcedure.getCalibrationCallback(), initialPoint);
        if (!actionResult.successful) {
            setErrorMessage(actionResult.errorMessage);
            return ErrorCodes.UnknownErrorCheckErrorMessage;
        }
        calibrationProcedure.setCalibrationPoint(initialPoint);
        return ErrorCodes.Successful;
    }


    /**
     * Called by UnitySKD to abort current calibration
     * @return status code
     */
    public static int abortCalibrationProcedure() throws RemoteException {
        Log.d(TAG, "abortCalibrationProcedure");
        if(!sdkState.isInState(SDKState.CONNECTED))
            return ErrorCodes.SDKIsNotConnectedToService;
        if (null != calibrationProcedure && !calibrationProcedure.isCalibrationFinished()) {
            ActionResult actionResult = sharedService.abortCalibrationProcedure();
            if (!actionResult.successful) {
                setErrorMessage(actionResult.errorMessage);
                return ErrorCodes.UnknownErrorCheckErrorMessage;
            }
        }
        return ErrorCodes.Successful;
    }

    /**
     * Called by UnitySDK to get last error message
     * @return last error message
     */
    public static String getLastErrorMessage() {
        return errorMessage;
    }

    public static void setErrorMessage(String errorMessage) {
        UnitySDK.errorMessage = errorMessage;
    }
    private static final ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            synchronized (waitForServiceConnectionLock) {
                waitForServiceConnectionLock.notifyAll();
            }
            sharedService = ISharedService.Stub.asInterface(service);
            sdkState.setState(SDKState.CONNECTED);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
            sdkState.setState(SDKState.NOT_CONNECTED);
        }

    };

    private final static IEyetrackerEventListener eventListener = new IEyetrackerEventListener.Stub() {
        private static final String gameObjectName = "AndroidMessageListener";

        @Override
        public void handleTrackerAvailabilityChanged(TrackerAvailability availability) {
            UnityPlayer.UnitySendMessage(gameObjectName, "InvokeEyetrackerAvailabilityChanged", Integer.toString(availability.ordinal()));
        }
    };

}
