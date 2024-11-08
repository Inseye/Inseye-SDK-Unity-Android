/*
 * Last edit: 21.10.2024, 11:43
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk.utils;
import android.content.ServiceConnection;
import androidx.annotation.Nullable;


public interface IPluggableServiceConnection extends ServiceConnection {
    void setServiceConnectedDelegate(@Nullable ServiceConnectedDelegate delegate);
    void setServiceDisconnectedDelegate(@Nullable ServiceDisconnectedDelegate delegate);
    void setBindingDiedDelegate(@Nullable BindingDiedDelegate delegate);
    void setNullBindingDelegate(@Nullable NullBindingDelegate delegate);

}
