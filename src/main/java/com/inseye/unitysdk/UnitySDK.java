package com.inseye.unitysdk;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.IBinder;
import android.util.Log;

import com.inseye.shared.IntentLogger;
import com.unity3d.player.UnityPlayer;

public class UnitySDK {

    private static final String TAG = "AndroidUnitySDK";
    public static void Initialize() {
        Activity currentActivity = UnityPlayer.currentActivity;
        Resources res = currentActivity.getResources();
        Intent serviceBindIntent = new Intent(res.getString(com.inseye.shared.R.string.service_action_name));
        serviceBindIntent = serviceBindIntent.setPackage(res.getString(com.inseye.shared.R.string.service_package_name));
        IntentLogger.LogIntent(TAG, serviceBindIntent);
        if (currentActivity.getApplicationContext().bindService(serviceBindIntent, connection, Context.BIND_AUTO_CREATE))
            Log.d(TAG, "Successfully bound to service.");
        else
            Log.e(TAG, "Failed to bind to service.");
    }

    public static void Dispose() {
        UnityPlayer.currentActivity.getApplicationContext().unbindService(connection);
    }

    private static final ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Connected to service");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Disconnected from service");
        }
    };

}
