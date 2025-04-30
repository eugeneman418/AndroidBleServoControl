package com.dare.androidbleservocontrol;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;

import java.util.List;

abstract public class ServoStateCallback {
    abstract void onDisconnect(BluetoothDevice device);
    abstract void onConnect(BluetoothDevice device);

    abstract void onServiceDiscover(List<BluetoothGattService> services);

    abstract void onPositionRead(int position);

    abstract void onPositionChange(int position);


}
