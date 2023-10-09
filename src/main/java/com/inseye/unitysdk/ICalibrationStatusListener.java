/*
 * Last edit: 03.11.2022, 18:02
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk;

public interface ICalibrationStatusListener {
    void CalibrationStatusChanged(CalibrationStatus oldStatus, CalibrationStatus newStatus);
}
