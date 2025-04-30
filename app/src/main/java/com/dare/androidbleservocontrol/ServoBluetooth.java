package com.dare.androidbleservocontrol;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * This class assumes all permissions are granted and BLE is turned on by the time it instantiates
 *
 */
public class ServoBluetooth {
    // I suspect the reason filtering by service uuid fails because it isn't advertised


    private final String TARGET_NAME = "MayWindTunnel";
    private BluetoothDevice targetDevice;

    private Context context;

    private final ScanSettings scanSetting = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED) // use balanced mode for longer scan period
            .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
            //.setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH) // callback as soon as we find a match
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) // maximize scan range
            .build();
    // android only allows 5 scans per 30s. If limit is exceeded, no results will return
    // After 30s need to restart a new scan

    private final ScanFilter scanFilter = new ScanFilter.Builder()
            .setDeviceName(TARGET_NAME)
            .build();

    private final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter(); // abstraction over device's ble hardware
    private final BluetoothLeScanner bleScanner = bluetoothAdapter.getBluetoothLeScanner();


    private final ScanCallback scanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            Log.d("ServoBluetooth", "Found device: "+result.getDevice().getName());
            if (result.getDevice() != null) {
                targetDevice = result.getDevice();
                connectToDevice(targetDevice);
            }


        }
    };

    private final ServoBluetoothGattCallback gattCallback;

    private boolean isScanning = false;

    public ServoBluetooth(Context context, ServoStateCallback servoStateCallback) {
        this.context = context;
        gattCallback = new ServoBluetoothGattCallback(servoStateCallback);
        startScan();
    }
    @SuppressLint("MissingPermission")
    public void startScan() {
        Log.d("ServoBluetooth", "Scan started");
        bleScanner.startScan(List.of(scanFilter), scanSetting, scanCallback);
        isScanning = true;
    }

    @SuppressLint("MissingPermission")
    public void stopScan() {
        bleScanner.stopScan(scanCallback);
        isScanning = false;
    }

    @SuppressLint("MissingPermission")
    public void connectToDevice(BluetoothDevice device) {
        targetDevice = device;
        stopScan();
        // IMPORTANT!!!!! the 4th param, transport, must be included to indicate a BLE device connection!
        // ESP32S3 cannot handle classical bluetooth, so request will otherwise be rejected
        targetDevice.connectGatt(context, true, gattCallback, BluetoothDevice.TRANSPORT_LE);
        // autoconnect = false -> it times out after trying to connect for 10-30 seconds
        // autoconnect = true -> will try to reconnect if connection was lost
    }

    public BluetoothGatt getGatt() {
        return gattCallback.gatt;
    }

    @SuppressLint("MissingPermission")
    public void startServiceDiscovery() {
        gattCallback.gatt.discoverServices();
    }

    @SuppressLint("MissingPermission")
    public void startPositionListen() {
        gattCallback.startServoPositionNotify();
    }

    @SuppressLint("MissingPermission")
    public void readPosition() {
        gattCallback.startServoPositionRead();
    }

    public void updateServoPosition(int targetPosition) {
        gattCallback.startServoPositionWrite(targetPosition);
    }



}
