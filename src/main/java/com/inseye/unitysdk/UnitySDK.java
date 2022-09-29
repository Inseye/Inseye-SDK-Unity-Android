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

import com.inseye.shared.communication.ActionResult;
import com.inseye.shared.communication.CalibrationPoint;
import com.inseye.shared.communication.GazeData;
import com.inseye.shared.communication.ICalibrationCallback;
import com.inseye.shared.communication.ISharedService;
import com.inseye.shared.communication.IntActionResult;
import com.sun.jna.Pointer;
import com.unity3d.player.UnityPlayer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class UnitySDK {
    public static final String TAG = "AndroidUnitySDK";
    private static final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[GazeData.SERIALIZER.getSizeInBytes()]);
    private static final GazeData gazeData = new GazeData();
    private static float val = 0;
    private static SDKState sdkState = new SDKState();
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
        if (sdkState.isInState(SDKState.CONNECTED)) {
            return ErrorCodes.SDKAlreadyConnected;
        }
        Activity currentActivity = UnityPlayer.currentActivity;
        Resources res = currentActivity.getResources();
        Intent serviceBindIntent = new Intent(res.getString(com.inseye.shared.R.string.service_action_name));
        serviceBindIntent = serviceBindIntent.setPackage(res.getString(com.inseye.shared.R.string.service_package_name));
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        boolean connectedSuccessfully = currentActivity.getApplicationContext().bindService(serviceBindIntent, connection, Context.BIND_AUTO_CREATE);
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
        sdkState.setState(SDKState.NOT_CONNECTED);
        UnityPlayer.currentActivity.getApplicationContext().unbindService(connection);
        return ErrorCodes.Successful;
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

}
