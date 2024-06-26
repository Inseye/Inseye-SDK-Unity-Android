/*
 * Last edit: 06.11.2023, 09:33
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk;
import android.os.RemoteException;

import com.inseye.shared.communication.IEyetrackerEventListener;
import com.inseye.shared.communication.TrackerAvailability;
import com.unity3d.player.UnityPlayer;

public class EyeTrackerEventListener extends IEyetrackerEventListener.Stub {
    private final String listenerGameObjectName;
    public EyeTrackerEventListener(String listenerGameObjectName) {
        this.listenerGameObjectName = listenerGameObjectName;
    }

    public void setTrackerAvailability(TrackerAvailability availability) {
        Log.d("handleTrackerAvailabilityChanged: " + availability.toString());
        UnityPlayer.UnitySendMessage(listenerGameObjectName, "InvokeEyeTrackerAvailabilityChanged", Integer.toString(availability.value));
    }
    @Override
    public void handleTrackerAvailabilityChanged(TrackerAvailability availability) throws RemoteException {
        setTrackerAvailability(availability);
    }
}
