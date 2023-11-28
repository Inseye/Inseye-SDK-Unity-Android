/*
 * Last edit: 09.10.2023, 11:58
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */
package com.inseye.unitysdk;

import com.sun.jna.Pointer;

public class SDKState {
    private Pointer cSharpPointer;
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
        updatePointer();
    }

    public void removeState(ConstSDKState sdkState) {
        value &= (~sdkState.value);
        updatePointer();
    }

    public void setState(ConstSDKState sdkState) {
        value = sdkState.value;
        updatePointer();
    }

    public void setUnityPointer(long stateIntPointer) throws Exception {
        Pointer javaPointer = new Pointer(stateIntPointer);
        if (cSharpPointer != null)
            Log.e("CSharpPointer is not null, there is error in SDK logic.");
        cSharpPointer = new Pointer(stateIntPointer);
        updatePointer();
    }

    public void clearUnityPointer() {
        cSharpPointer = null;
    }

    private void updatePointer() {
        Log.i("Current state: " + value);
        if (cSharpPointer == null)
            return;
        cSharpPointer.setInt(0, value);
    }

}