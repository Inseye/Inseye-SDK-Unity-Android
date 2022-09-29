package com.inseye.unitysdk;

public class SDKState {
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
    }

    public void removeState(ConstSDKState sdkState) {
        value &= (~sdkState.value);
    }

    public void setState(ConstSDKState sdkState) {
        value = sdkState.value;
    }
}
