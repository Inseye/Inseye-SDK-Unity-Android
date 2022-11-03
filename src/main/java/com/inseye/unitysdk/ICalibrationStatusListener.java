package com.inseye.unitysdk;

public interface ICalibrationStatusListener {
    void CalibrationStatusChanged(CalibrationStatus oldStatus, CalibrationStatus newStatus);
}
