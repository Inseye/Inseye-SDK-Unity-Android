/*
 * Last edit: 26.09.2022, 13:09
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk;

public final class SDKError extends Exception {
    public final int errorCode;
    public SDKError(int errorCode) {
        super();
        this.errorCode = errorCode;
    }

    public SDKError(String errorMessage) {
        super(errorMessage);
        errorCode = ErrorCodes.UnknownErrorCheckErrorMessage;
    }
}
