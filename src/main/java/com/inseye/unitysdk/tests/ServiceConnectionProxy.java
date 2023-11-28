/*
 * Last edit: 26.11.2023, 09:38
 * Copyright (c) Inseye Inc.
 *
 * This file is part of Inseye Software Development Kit subject to Inseye SDK License
 * See  https://github.com/Inseye/Licenses/blob/master/SDKLicense.txt.
 * All other rights reserved.
 */

package com.inseye.unitysdk.tests;

import android.content.ComponentName;
import android.content.res.Resources;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.inseye.shared.communication.ActionResult;
import com.inseye.shared.communication.BinaryStreamActionResult;
import com.inseye.shared.communication.Eye;
import com.inseye.shared.communication.ICalibrationCallback;
import com.inseye.shared.communication.IEyetrackerEventListener;
import com.inseye.shared.communication.IServiceCalibrationCallback;
import com.inseye.shared.communication.ISharedService;
import com.inseye.shared.communication.IntActionResult;
import com.inseye.shared.communication.TrackerAvailability;
import com.inseye.shared.communication.Version;
import com.inseye.shared.utils.BindingDiedDelegate;
import com.inseye.shared.utils.IPluggableServiceConnection;
import com.inseye.shared.utils.NullBindingDelegate;
import com.inseye.shared.utils.ServiceConnectedDelegate;
import com.inseye.shared.utils.ServiceDisconnectedDelegate;
import com.inseye.unitysdk.Log;
import com.inseye.unitysdk.UnitySDK;
import com.unity3d.player.UnityPlayer;
import com.inseye.shared.R;

import java.io.FileDescriptor;

public class ServiceConnectionProxy implements IPluggableServiceConnection, ISharedService, IBinder {

    static class GazeDataSourceMockArguments {
        public final int port;

        public GazeDataSourceMockArguments(int port) {
            this.port = port;
        }
    }

    private IBinder binder;
    private ISharedService serviceImplementation;
    @Nullable
    private GazeDataSourceMockArguments gazeDataSourceMockArguments;
    private final IPluggableServiceConnection serviceConnection;
    private static final ComponentName componentName;

    static {
        Resources res = UnityPlayer.currentActivity.getResources();
        componentName = new ComponentName(res.getString(R.string.service_package_name), res.getString(R.string.service_class_name));
    }

    public ServiceConnectionProxy(IPluggableServiceConnection serviceConnection, ISharedService serviceImplementation) {
        this.serviceConnection = serviceConnection;
        if (null != serviceImplementation)
            binder = serviceImplementation.asBinder();
        this.serviceImplementation = serviceImplementation;
    }

    public ISharedService getSharedService() {
        return serviceImplementation;
    }

    public IPluggableServiceConnection getServiceConnection() {
        return serviceConnection;
    }

    public void proxyServiceDisconnect() {
        this.serviceConnection.onServiceDisconnected(componentName);
    }

    public void proxyServiceConnect() {
        if (null == binder)
            Log.e("Binder is null");
        this.serviceConnection.onServiceConnected(componentName, binder);
    }

    /*
     * Replaces:
     * - startStreamingGazeData
     * - stopStreamingGazeData
     * - isStreamingGazeData
     * with fake implementation that never fails
     */
    public void enableMockServiceGazeDataSource(int portReturnedFromStartStreamingGazeData) {
        Log.i("Enabled mock service gaze data source: " + portReturnedFromStartStreamingGazeData);
        gazeDataSourceMockArguments = new GazeDataSourceMockArguments(portReturnedFromStartStreamingGazeData);
    }

    /*
     * Disables mock implementation enabled with 'enableMockServiceGazeDataSource'
     */
    public void disableMockServiceGazeDataSource() {
        gazeDataSourceMockArguments = null;
    }

    @Override
    public IntActionResult startStreamingGazeData() throws RemoteException {
        if (null != gazeDataSourceMockArguments)
        {
            Log.d("Returning mock gaze data source port: " + gazeDataSourceMockArguments.port);
            return IntActionResult.success(gazeDataSourceMockArguments.port);
        }
        return serviceImplementation.startStreamingGazeData();
    }

    @Override
    public void stopStreamingGazeData() throws RemoteException {
        if (null != gazeDataSourceMockArguments)
            return;
        serviceImplementation.stopStreamingRawData();
    }

    @Override
    public int isStreamingGazeData() throws RemoteException {
        if (null != gazeDataSourceMockArguments)
            return gazeDataSourceMockArguments.port;
        return serviceImplementation.isStreamingGazeData();
    }

    @Override
    public IServiceCalibrationCallback startCalibrationProcedure(ActionResult result, ICalibrationCallback clientInterface) throws RemoteException {
        return serviceImplementation.startCalibrationProcedure(result, clientInterface);
    }

    @Override
    public ActionResult subscribeToEyetrackerEvents(IEyetrackerEventListener listener) throws RemoteException {
        return serviceImplementation.subscribeToEyetrackerEvents(listener);
    }

    @Override
    public void unsubscribeFromEyetrackerEvents() throws RemoteException {
        serviceImplementation.unsubscribeFromEyetrackerEvents();
    }

    @Override
    public TrackerAvailability getTrackerAvailability() throws RemoteException {
        return serviceImplementation.getTrackerAvailability();
    }

    @Override
    public void getVersions(Version serviceVersion, Version firmwareVersion) throws RemoteException {
        serviceImplementation.getVersions(serviceVersion, firmwareVersion);
    }

    @Override
    public Eye getDominantEye() throws RemoteException {
        return serviceImplementation.getDominantEye();
    }

    @Override
    public BinaryStreamActionResult startStreamingRawData(int requestedBinaryDataVersion) throws RemoteException {
        return serviceImplementation.startStreamingRawData(requestedBinaryDataVersion);
    }

    @Override
    public void stopStreamingRawData() throws RemoteException {
        serviceImplementation.stopStreamingRawData();
    }

    @Override
    public boolean isStreamingRawData() throws RemoteException {
        return serviceImplementation.isStreamingRawData();
    }

    @Override
    public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
        binder = iBinder;
        serviceImplementation = ISharedService.Stub.asInterface(iBinder);
        serviceConnection.onServiceConnected(componentName, this);
    }

    @Override
    public void onServiceDisconnected(ComponentName componentName) {
        serviceImplementation = null;
        binder = null;
        serviceConnection.onServiceDisconnected(componentName);
    }

    @Override
    public void onBindingDied(ComponentName name) {
        serviceImplementation = null;
        binder = null;
        serviceConnection.onBindingDied(name);
    }

    @Override
    public void onNullBinding(ComponentName name) {
        serviceImplementation = null;
        binder = null;
        serviceConnection.onNullBinding(name);
    }

    @Override
    public void setServiceConnectedDelegate(@Nullable ServiceConnectedDelegate delegate) {
        serviceConnection.setServiceConnectedDelegate(delegate);
    }

    @Override
    public void setServiceDisconnectedDelegate(@Nullable ServiceDisconnectedDelegate delegate) {
        serviceConnection.setServiceDisconnectedDelegate(delegate);
    }

    @Override
    public void setBindingDiedDelegate(@Nullable BindingDiedDelegate delegate) {
        serviceConnection.setBindingDiedDelegate(delegate);
    }

    @Override
    public void setNullBindingDelegate(@Nullable NullBindingDelegate delegate) {
        serviceConnection.setNullBindingDelegate(delegate);
    }

    @Nullable
    @Override
    public String getInterfaceDescriptor() throws RemoteException {
        return binder.getInterfaceDescriptor();
    }

    @Override
    public boolean pingBinder() {
        return binder.pingBinder();
    }

    @Override
    public boolean isBinderAlive() {
        return binder.isBinderAlive();
    }

    @Nullable
    @Override
    public IInterface queryLocalInterface(@NonNull String s) {
        return binder.queryLocalInterface(s);
    }

    @Override
    public void dump(@NonNull FileDescriptor fileDescriptor, @Nullable String[] strings) throws RemoteException {
        binder.dump(fileDescriptor, strings);
    }

    @Override
    public void dumpAsync(@NonNull FileDescriptor fileDescriptor, @Nullable String[] strings) throws RemoteException {
        binder.dumpAsync(fileDescriptor, strings);
    }

    @Override
    public boolean transact(int i, @NonNull Parcel parcel, @Nullable Parcel parcel1, int i1) throws RemoteException {
        return binder.transact(i, parcel, parcel1, i1);
    }

    @Override
    public void linkToDeath(@NonNull DeathRecipient deathRecipient, int i) throws RemoteException {
        binder.linkToDeath(deathRecipient, i);
    }

    @Override
    public boolean unlinkToDeath(@NonNull DeathRecipient deathRecipient, int i) {
        return binder.unlinkToDeath(deathRecipient, i);
    }

    @Override
    public IBinder asBinder() {
        return binder;
    }
}
