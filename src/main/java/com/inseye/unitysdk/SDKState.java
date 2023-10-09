/*
 * Last edit: 09.10.2023, 11:58
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */
package com.inseye.unitysdk;

import android.util.Log;

import com.sun.jna.Pointer;

import java.util.HashMap;

public class SDKState {
    private final HashMap<Long, Pointer> cSharpPointerToJavaPointer;
    private static class ConstSDKState {
        private final int value;
        ConstSDKState(int initialValue) {
            value = initialValue;
        }
    }
    
    public static final ConstSDKState NOT_CONNECTED = new ConstSDKState(0);
    public static final ConstSDKState CONNECTED = new ConstSDKState(1);
    public static final ConstSDKState CALIBRATING = new ConstSDKState(2);
    public static final ConstSDKState ATTACHED_TO_GAZE_DATA_STREAM = new ConstSDKState(4);
    public static final ConstSDKState SUBSCRIBED_TO_EVENTS = new ConstSDKState(8);
    private int value;
    public SDKState() {
        value = 0;
        cSharpPointerToJavaPointer = new HashMap<>();
    }

    public boolean isInState(ConstSDKState sdkState) {
        // case for NOT_CONNECTED
        if (sdkState.value == 0)
            return value == 0;
        // all other cases
        return (value & sdkState.value) == sdkState.value;
    }

    public void addState(ConstSDKState sdkState) {
        value |= sdkState.value;
        updatePointers();
    }

    public void removeState(ConstSDKState sdkState) {
        value &= (~sdkState.value);
        updatePointers();
    }

    public void setState(ConstSDKState sdkState) {
        value = sdkState.value;
        updatePointers();
    }

    public void addUnityPointer(long stateIntPointer) throws Exception {
        Pointer javaPointer = new Pointer(stateIntPointer);
        Long cSharpPointer = stateIntPointer;
        if (cSharpPointerToJavaPointer.containsKey(cSharpPointer))
            throw new Exception("Attempt to add duplicate pointer");
        cSharpPointerToJavaPointer.put(cSharpPointer, javaPointer);
        updatePointers();
    }

    public void clearUnityPointer(long stateIntPointer) {
        cSharpPointerToJavaPointer.remove(stateIntPointer);
    }

    private void updatePointers() {
        Log.i(UnitySDK.TAG, "Current state: " + value);
        if (cSharpPointerToJavaPointer.isEmpty())
            return;
        for (Pointer pointer : cSharpPointerToJavaPointer.values())
            pointer.setInt(0, value);
    }

    public int getListenersCount() {
        return cSharpPointerToJavaPointer.size();
    }
}
