/*
 * Last edit: 15.11.2023, 12:32
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk;

public final class Log {
    public enum Level {
        VERBOSE(android.util.Log.VERBOSE),
        DEBUG(android.util.Log.DEBUG),
        INFO(android.util.Log.INFO),
        ERROR(android.util.Log.ERROR),
        WARN(android.util.Log.WARN);
        public final int value;

        Level(int value) {
            this.value = value;
        }
    }
    private static final String TAG = "AndroidUnitySDK";
    public static Level CurrentLevel = Level.ERROR;
    public static void i(String message) {
        if (CurrentLevel.value <= Level.INFO.value)
            android.util.Log.i(TAG, message);
    }

    public static void d(String message) {
        if (CurrentLevel.value <= Level.DEBUG.value)
            // log as info to allow debug messages in release versions of the library
            android.util.Log.i(TAG, message);
    }

    public static void e(String message) {
        if (CurrentLevel.value <= Level.ERROR.value)
            android.util.Log.e(TAG, message);
    }

    public static void e(String message, Exception e) {
        if (CurrentLevel.value <= Level.ERROR.value)
            android.util.Log.e(TAG, message, e);
    }
}
