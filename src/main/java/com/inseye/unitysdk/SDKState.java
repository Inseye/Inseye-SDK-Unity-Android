/*
 * Last edit: 06.11.2023, 10:57
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */
package com.inseye.unitysdk;

import android.util.Log;

import com.unity3d.player.UnityPlayer;

public class SDKState {
    private String listenerGameObjectName;
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
        updateUnityListener();
    }

    public void removeState(ConstSDKState sdkState) {
        value &= (~sdkState.value);
        updateUnityListener();
    }

    public void setState(ConstSDKState sdkState) {
        value = sdkState.value;
        updateUnityListener();
    }

    public void setUnityListener(String gameObjectName) throws Exception {
        listenerGameObjectName = gameObjectName;
        updateUnityListener();
    }

    public void clearUnityGameObject() {
        listenerGameObjectName = null;
    }

    private void updateUnityListener() {
        Log.i(UnitySDK.TAG, "Current state: " + value);
        if (listenerGameObjectName == null)
            return;
        UnityPlayer.UnitySendMessage(listenerGameObjectName, "InvokeSDKStateChanged", Integer.toString(value));
    }

}
