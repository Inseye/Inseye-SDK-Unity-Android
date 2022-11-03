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
