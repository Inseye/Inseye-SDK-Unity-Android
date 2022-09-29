package com.inseye.unitysdk;

public class ErrorCodes {
    // universal codes
    public static final int Successful = 0;
    public static final int UnknownErrorCheckErrorMessage = 1;
    public static final int SDKIsNotConnectedToService = 2;
    // initialization error codes
    public static final int SDKAlreadyConnected = 10;
    public static final int FailedToBindToService = 11;
    // calibration error codes
    public static final int AnotherCalibrationIsOngoing = 20;
    // reading gaze data
    public static final int NoValidGazeAvailable = 30;
    private ErrorCodes() {}
}
