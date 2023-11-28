/*
 * Last edit: 28.11.2023, 16:59
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk.tests;

import android.os.RemoteException;

import com.inseye.shared.communication.ActionResult;
import com.inseye.shared.communication.CalibrationPoint;
import com.inseye.shared.communication.ICalibrationCallback;

/*
 * Object passed to Unity Test suite.
 */
public class UnityCalibrationProcedureProxy {

    private final MockCalibrationProcedure mockCalibrationProcedure;

    public UnityCalibrationProcedureProxy(MockCalibrationProcedure mockCalibrationProcedure) {
        this.mockCalibrationProcedure = mockCalibrationProcedure;
    }

    /*
     * Called from Unity.
     */
    public void setReadyToReceiveCalibrationPointResponse(String errorMessage, float x, float y) {
        ActionResult ar;
        if (errorMessage.isEmpty())
            ar = ActionResult.success();
        else
            ar = ActionResult.error(errorMessage);
        this.mockCalibrationProcedure.setReadyToReceiveCalibrationPointResponses(ar, new CalibrationPoint(x, y));
    }

    /*
     * Called from Unity.
     */
    public void setCalibrationAbortResponse(String errorMessage) {
        if (errorMessage.isEmpty())
            this.mockCalibrationProcedure.setAbortCalibrationProcedureResponse(ActionResult.success());
        else
            this.mockCalibrationProcedure.setAbortCalibrationProcedureResponse(ActionResult.error(errorMessage));
    }

    /*
     * Called from Unity.
     */
    public void finishCalibrationAsService(String errorMessage) throws RemoteException {
        ICalibrationCallback callback = this.mockCalibrationProcedure.getUserCalibrationCallback();
        if (null == callback)
            throw new RuntimeException("Callback is null.");
        if (errorMessage.isEmpty())
            callback.finishCalibration(ActionResult.success());
        else
            callback.finishCalibration(ActionResult.error(errorMessage));
    }

    /*
     * Called from Unity.
     */
    public void setNextCalibrationPointAsService(float x, float y) throws RemoteException {
        ICalibrationCallback callback = this.mockCalibrationProcedure.getUserCalibrationCallback();
        if (null == callback)
            throw new RuntimeException("Callback is null.");
        callback.showNextCalibrationPoint(new CalibrationPoint(x, y));
    }
}
