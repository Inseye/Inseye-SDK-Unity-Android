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
