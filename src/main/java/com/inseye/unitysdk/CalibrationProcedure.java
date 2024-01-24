/*
 * Last edit: 26.04.2023, 14:29
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk;

import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;

import com.inseye.shared.IByteSerializer;
import com.inseye.shared.communication.ActionResult;
import com.inseye.shared.communication.CalibrationPoint;
import com.inseye.shared.communication.CalibrationPointResponse;
import com.inseye.shared.communication.ICalibrationCallback;
import com.inseye.shared.communication.IServiceCalibrationCallback;
import com.sun.jna.Pointer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import kotlin.NotImplementedError;

public class CalibrationProcedure {

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
    private final Pointer pointIndexPointer;
    private int pointIndex;
    private String errorMessage;
    private CalibrationStatus calibrationStatus;
    private ICalibrationStatusListener calibrationListener;
    private IServiceCalibrationCallback serviceCalibrationCallback;
    private final ICalibrationCallback calibrationCallback = new ICalibrationCallback.Stub() { // calibration callback invoked by service

        @Override
        public CalibrationPointResponse showNextCalibrationPoint(CalibrationPoint nextPoint) throws RemoteException {
            CalibrationPointResponse calibrationPointResponse = new CalibrationPointResponse(0, 0, 0, System.currentTimeMillis());
            synchronized (buffer) {
                // read displayed calibration point
                calibrationPointResponsePointer.read(0, buffer.array(), 0, CALIBRATION_RESPONSE_SERIALIZER.getSizeInBytes());
                buffer.position(0);
                CALIBRATION_RESPONSE_SERIALIZER.readFromBuffer(calibrationPointResponse, buffer);
                Log.d(UnitySDK.TAG, "displayed point x: " + calibrationPointResponse.x + " y: " + calibrationPointResponse.y + " tStart: " + calibrationPointResponse.displayStartMs);
                // write new calibration point
                setCalibrationPoint(nextPoint);
            }
            return calibrationPointResponse;
        }

        @Override
        public void finishCalibration(ActionResult calibrationResult) {
            if (isCalibrationFinished())
                return;
            if (calibrationResult.successful)
                setStatus(CalibrationStatus.FinishedSuccessfully, null);
            else {
                setStatus(CalibrationStatus.FinishedFailed, calibrationResult.errorMessage);
            }
        }
    };

    CalibrationProcedure(long calibrationPointRequestPointer, long calibrationPointResponsePointer, long calibrationStatusPointer, long pointIndexPointer) {
        this.calibrationPointRequestPointer = new Pointer(calibrationPointRequestPointer);
        this.calibrationPointResponsePointer = new Pointer(calibrationPointResponsePointer);
        this.calibrationStatusPointer = new Pointer(calibrationStatusPointer);
        this.pointIndexPointer = new Pointer(pointIndexPointer);
        this.pointIndex = 0;
        this.calibrationStatus = CalibrationStatus.Ongoing;
        setStatus(CalibrationStatus.Ongoing, null);
    }

    private void setCalibrationPoint(CalibrationPoint calibrationPoint) {
        pointIndex++;
        Log.d(UnitySDK.TAG, "next calibration point - x: " + calibrationPoint.x + " y: " + calibrationPoint.y + " index: " + pointIndex);
        synchronized (buffer) {
            buffer.position(0);
            CALIBRATION_POINT_SERIALIZER.writeToBuffer(calibrationPoint, buffer);
            calibrationPointRequestPointer.write(0, buffer.array(), 0, CALIBRATION_POINT_SERIALIZER.getSizeInBytes());
            pointIndexPointer.setInt(0, pointIndex);
        }
    }

    void setServiceCalibrationCallback(IServiceCalibrationCallback serviceCalibrationCallback) {
        this.serviceCalibrationCallback = serviceCalibrationCallback;
    }

    /*
     * Called from Unity.
     */
    public void markReadyForPointDisplay()  {
        if (null == this.serviceCalibrationCallback)
            throw new RuntimeException("ServiceCalibrationCallback is null!");
        CalibrationPoint initialCalibrationPoint = new CalibrationPoint();
        ActionResult result;
        try {
            result = this.serviceCalibrationCallback.readyToRecieveCalibrationPoint(initialCalibrationPoint);
            if (!result.successful)
                setStatus(CalibrationStatus.FinishedFailed, result.errorMessage);
            else
                setCalibrationPoint(initialCalibrationPoint);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void onServiceDisconnected() {
        if (isCalibrationFinished())
            return;
        setStatus(CalibrationStatus.FinishedFailed, "Service disconnected.");
    }

    /*
     * Called from Unity.
     */
    public void abortCalibration() {
        if (isCalibrationFinished())
            return;
        if (null == this.serviceCalibrationCallback)
            throw new RuntimeException("ServiceCalibrationCallback is null!");
        String optionalErrorMessage = null;
        try {
            ActionResult result = serviceCalibrationCallback.abortCalibrationProcedure();
            if (!result.successful)
                optionalErrorMessage = result.errorMessage;
            else
                optionalErrorMessage = "Aborted by user.";
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        finally {
            if (!isCalibrationFinished())
                setStatus(CalibrationStatus.FinishedFailed, optionalErrorMessage);
        }
    }
    /*
     * Called from Unity.
     */
    public String readOptionalErrorMessage() {
        return errorMessage;
    }

    ICalibrationCallback getCalibrationCallback() {
        return calibrationCallback;
    }

    boolean isCalibrationFinished() {
        return calibrationStatus == CalibrationStatus.FinishedSuccessfully || calibrationStatus == CalibrationStatus.FinishedFailed;
    }
    void setCalibrationStatusListener(ICalibrationStatusListener listener) {
        calibrationListener = listener;
    }
    private void setStatus(CalibrationStatus newStatus, @Nullable String errorMessage) {
        if (calibrationStatus == CalibrationStatus.FinishedFailed || calibrationStatus == CalibrationStatus.FinishedSuccessfully)
            throw new RuntimeException("Attempt to change status of already finished calibration was made.");
        CalibrationStatus oldStatus = calibrationStatus;
        calibrationStatus = newStatus;
        calibrationStatusPointer.setInt(0, newStatus.intValue);
        this.errorMessage = errorMessage;
        if (null != calibrationListener)
            calibrationListener.CalibrationStatusChanged(oldStatus, newStatus);
    }
}
