/*
 * Last edit: 21.10.2024, 11:49
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.inseye.unitysdk.utils.BindingDiedDelegate;
import com.inseye.unitysdk.utils.IPluggableServiceConnection;
import com.inseye.unitysdk.utils.NullBindingDelegate;
import com.inseye.unitysdk.utils.ServiceConnectedDelegate;
import com.inseye.unitysdk.utils.ServiceDisconnectedDelegate;

public class PluggableServiceConnection implements ServiceConnection, IPluggableServiceConnection {

    private ServiceConnectedDelegate serviceConnectedDelegate;
    private ServiceDisconnectedDelegate serviceDisconnectedDelegate;
    private BindingDiedDelegate bindingDiedDelegate;
    private NullBindingDelegate nullBindingDelegate;

    @Override
    public void setServiceConnectedDelegate(@Nullable ServiceConnectedDelegate delegate) {
        serviceConnectedDelegate = delegate;
    }

    @Override
    public void setServiceDisconnectedDelegate(@Nullable ServiceDisconnectedDelegate delegate) {
        serviceDisconnectedDelegate = delegate;
    }

    @Override
    public void setBindingDiedDelegate(@Nullable BindingDiedDelegate delegate) {
        bindingDiedDelegate = delegate;
    }

    @Override
    public void setNullBindingDelegate(@Nullable NullBindingDelegate delegate) {
        nullBindingDelegate = delegate;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (null != serviceConnectedDelegate)
            serviceConnectedDelegate.onServiceConnected(name, service);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (null != serviceDisconnectedDelegate)
            serviceDisconnectedDelegate.onServiceDisconnected(name);
    }

    @Override
    public void onBindingDied(ComponentName name) {
        if (null != bindingDiedDelegate)
            bindingDiedDelegate.onBindingDied(name);
    }

    @Override
    public void onNullBinding(ComponentName name) {
        if (null != nullBindingDelegate)
            nullBindingDelegate.onNullBinding(name);
    }
}
