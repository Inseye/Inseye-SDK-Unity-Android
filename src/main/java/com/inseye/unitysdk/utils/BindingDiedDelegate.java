/*
 * Last edit: 21.10.2024, 11:46
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk.utils;

import android.content.ComponentName;

public interface BindingDiedDelegate {
    void onBindingDied(ComponentName name);
}
