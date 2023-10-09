/*
 * Last edit: 20.06.2023, 12:10
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

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
    public static final int NoCalibrationIsOngoing = 21;
    public static final int CalibrationTimeout = 22;
    // reading gaze data
    public static final int NoValidGazeAvailable = 30;
    // events
    public static final int AlreadySubscribedToEvents = 40;
    private ErrorCodes() {}
}
