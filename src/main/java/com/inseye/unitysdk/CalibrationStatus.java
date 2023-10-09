/*
 * Last edit: 03.11.2022, 18:03
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk;

public enum CalibrationStatus {
    Ongoing(1),
    FinishedSuccessfully(2),
    FinishedFailed(3);
    public final int intValue;
    CalibrationStatus(int value) {
        intValue = value;
    }
}
