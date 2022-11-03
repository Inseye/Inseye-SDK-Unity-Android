package com.inseye.unitysdk;

import android.util.Log;

import com.sun.jna.Pointer;

public class SDKState {
    private Pointer stateIntPointer;
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

    public void addUnityPointer(Pointer stateIntPointer) {
        this.stateIntPointer = stateIntPointer;
        updatePointer();
    }

    public void clearUnityPointer() {
        stateIntPointer = null;
    }

    private void updatePointer() {
        if (stateIntPointer == null)
            return;
        stateIntPointer.setInt(0, value);
        Log.i(UnitySDK.TAG, "Current state: " + value);
    }
}
