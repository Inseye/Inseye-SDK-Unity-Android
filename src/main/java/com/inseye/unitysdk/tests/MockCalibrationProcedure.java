/*
 * Last edit: 28.11.2023, 16:59
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk.tests;

import android.os.IBinder;
import android.os.RemoteException;

import androidx.annotation.Nullable;

import com.inseye.shared.communication.ActionResult;
import com.inseye.shared.communication.CalibrationPoint;
import com.inseye.shared.communication.ICalibrationCallback;
import com.inseye.shared.communication.IServiceCalibrationCallback;
import com.inseye.unitysdk.Log;

class MockCalibrationProcedure implements IServiceCalibrationCallback {

    @Nullable
    private ICalibrationCallback calibrationCallback;
    private ActionResult onReadyToReceiveCalibrationPoint, onAbortCalibrationProcedure;
    private CalibrationPoint calibrationPointOnReady;
    MockCalibrationProcedure() {
        onReadyToReceiveCalibrationPoint = ActionResult.success();
        calibrationPointOnReady = new CalibrationPoint(0, 0);
        onAbortCalibrationProcedure = ActionResult.success();
    }

    public void setCalibrationCallback(ICalibrationCallback calibrationCallback)
    {
        Log.i("Calibration callback set to: " + calibrationCallback);
        this.calibrationCallback = calibrationCallback;
    }
    public void setReadyToReceiveCalibrationPointResponses(ActionResult actionResult, CalibrationPoint initialPoint) {
        onReadyToReceiveCalibrationPoint = actionResult;
        calibrationPointOnReady = initialPoint;
    }

    public void setAbortCalibrationProcedureResponse(ActionResult actionResult) {
        onAbortCalibrationProcedure = actionResult;
    }

    @Nullable
    public ICalibrationCallback getUserCalibrationCallback() {
        return calibrationCallback;
    }

    @Override
    public ActionResult readyToRecieveCalibrationPoint(CalibrationPoint initialCalibrationPoint) {
        initialCalibrationPoint.x = calibrationPointOnReady.x;
        initialCalibrationPoint.y = calibrationPointOnReady.y;
        return onReadyToReceiveCalibrationPoint;
    }

    @Override
    public ActionResult abortCalibrationProcedure() {
        return onAbortCalibrationProcedure;
    }



    @Override
    public IBinder asBinder() {
        throw new RuntimeException("Not implemented method");
    }
}
