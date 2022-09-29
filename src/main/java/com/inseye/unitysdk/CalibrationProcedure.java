package com.inseye.unitysdk;

import android.os.Debug;
import android.os.RemoteException;
import android.util.Log;

import com.inseye.shared.IByteSerializer;
import com.inseye.shared.communication.ActionResult;
import com.inseye.shared.communication.CalibrationPoint;
import com.inseye.shared.communication.CalibrationPointResponse;
import com.inseye.shared.communication.ICalibrationCallback;
import com.sun.jna.Pointer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import kotlin.NotImplementedError;

public class CalibrationProcedure {

    private final static int CALIBRATION_STATUS_ONGOING = 1;
    private final static int CALIBRATION_STATUS_FINISHED_SUCCESSFULLY = 2;
    private final static int CALIBRATION_STATUS_FINISHED_FAILED = 3;

    private static final IByteSerializer<CalibrationPoint> CALIBRATION_POINT_SERIALIZER = new IByteSerializer<CalibrationPoint>() {
        // this serializer implementation must much much struct layout from UnitySDK C# code
        // in class Inseye.Internal.CalibrationPointRequest
        @Override
        public int getSizeInBytes() {
            // 2 floats (4 bytes each)
            return 8;
        }

        @Override
        public void readFromBuffer(CalibrationPoint object, ByteBuffer buffer) {
            throw new NotImplementedError();
        }

        @Override
        public void writeToBuffer(CalibrationPoint object, ByteBuffer buffer) {
            buffer.putFloat(object.x);
            buffer.putFloat(object.y);
        }
    };

    private static final IByteSerializer<CalibrationPointResponse> CALIBRATION_RESPONSE_SERIALIZER = new IByteSerializer<CalibrationPointResponse>() {
        // this serializer implementation must much much struct layout from UnitySDK C# code
        // in class Inseye.Internal.CalibrationPointResponse
        @Override
        public int getSizeInBytes() {
            // 2 floats (4 bytes each) 1 longs (8 bytes each)
            return 16;
        }

        @Override
        public void readFromBuffer(CalibrationPointResponse object, ByteBuffer buffer) {
            object.x = buffer.getFloat();
            object.y = buffer.getFloat();
            object.displayStartMs = buffer.getLong();
        }

        @Override
        public void writeToBuffer(CalibrationPointResponse object, ByteBuffer buffer) {
            throw new NotImplementedError();
        }
    };

    private final ByteBuffer buffer = ByteBuffer.wrap(new byte[Math.max(CALIBRATION_POINT_SERIALIZER.getSizeInBytes(), CALIBRATION_RESPONSE_SERIALIZER.getSizeInBytes())]).order(ByteOrder.LITTLE_ENDIAN);
    private final Pointer calibrationPointRequestPointer;
    private final Pointer calibrationPointResponsePointer;
    private final Pointer calibrationStatusPointer;
    private int calibrationStatus;
    private final ICalibrationCallback calibrationCallback = new ICalibrationCallback.Stub() {

        @Override
        public CalibrationPointResponse showNextCalibrationPoint(CalibrationPoint nextPoint) throws RemoteException {
            CalibrationPointResponse calibrationPointResponse = new CalibrationPointResponse(0, 0, 0, System.currentTimeMillis());
            synchronized (buffer) {
                // read displayed calibration point
                calibrationPointResponsePointer.read(0, buffer.array(), 0, CALIBRATION_RESPONSE_SERIALIZER.getSizeInBytes());
                buffer.position(0);
                CALIBRATION_RESPONSE_SERIALIZER.readFromBuffer(calibrationPointResponse, buffer);
                Log.d(UnitySDK.TAG, "displayed point x: " + calibrationPointResponse.x + " y: " + calibrationPointResponse.y + "tStart: ");
                // write new calibration point
                setCalibrationPoint(nextPoint);
            }
            return calibrationPointResponse;
        }

        @Override
        public void finishCalibration(ActionResult calibrationResult) throws RemoteException {
            if (calibrationResult.successful)
                setStatus(CALIBRATION_STATUS_FINISHED_SUCCESSFULLY);
            else {
                UnitySDK.setErrorMessage(calibrationResult.errorMessage);
                setStatus(CALIBRATION_STATUS_FINISHED_FAILED);
            }
        }
    };

    public CalibrationProcedure(long calibrationPointRequestPointer, long calibrationPointResponsePointer, long calibrationStatusPointer) {
        this.calibrationPointRequestPointer = new Pointer(calibrationPointRequestPointer);
        this.calibrationPointResponsePointer = new Pointer(calibrationPointResponsePointer);
        this.calibrationStatusPointer = new Pointer(calibrationStatusPointer);
        setStatus(CALIBRATION_STATUS_ONGOING);
    }

    public void setCalibrationPoint(CalibrationPoint calibrationPoint) {
        Log.d(UnitySDK.TAG, "next calibration point - x: " + calibrationPoint.x + " y: " + calibrationPoint.y);
        synchronized (buffer) {
            buffer.position(0);
            CALIBRATION_POINT_SERIALIZER.writeToBuffer(calibrationPoint, buffer);
            calibrationPointRequestPointer.write(0, buffer.array(), 0, CALIBRATION_POINT_SERIALIZER.getSizeInBytes());
        }
    }

    public ICalibrationCallback getCalibrationCallback() {
        return calibrationCallback;
    }

    public boolean isCalibrationFinished() {
        return calibrationStatus != CALIBRATION_STATUS_ONGOING;
    }

    private void setStatus(int status) {
        calibrationStatus = status;
        calibrationStatusPointer.setInt(0, status);
    }
}
