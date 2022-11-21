package com.inseye.unitysdk;

public class ErrorCodes {
    // universal codes
    public static final int Successful = 0;
    public static final int UnknownErrorCheckErrorMessage = 1;
    public static final int SDKIsNotConnectedToService = 2;
    public static final int UnknownError = 3;
    // initialization error codes
    public static final int SDKAlreadyConnected = 10;
    public static final int FailedToBindToService = 11;
    public static final int InitializationTimeout = 12;
    // calibration error codes
    public static final int AnotherCalibrationIsOngoing = 20;
    // reading gaze data
    public static final int NoValidGazeAvailable = 30;
    // events
    public static final int AlreadySubscribedToEvents = 40;
    private ErrorCodes() {}
}
