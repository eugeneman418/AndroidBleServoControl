package com.dare.androidbleservocontrol;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * This class handles all the communication logic (async) once ServoBluetooth has established connection
 */
public class ServoBluetoothGattCallback extends BluetoothGattCallback {

    private final String SERVICE_UUID = "14eff093-8234-4b9c-89ee-6929ad5222e1";
    private final String CHARACTERISTIC_UUID = "ed011481-be35-4609-b0d3-1f57019b2af2";

    private boolean isConnected;
    public BluetoothGatt gatt;

    private int servoPosition;

    // true when a onCharacteristicsRead triggers, indicates that a fresh value is available

    // writeAvailable indicates whether we've received an ACK (or error) or previous write request
    // no point in writing again if previous request hasn't been delivered yet
    private boolean writeAvailable = false; // false until connection is established

    private ServoStateCallback servoStateCallback;

    ServoBluetoothGattCallback(ServoStateCallback servoStateCallback) {
        this.servoStateCallback = servoStateCallback;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.w("ServoBluetoothGattCallback", "Successfully connected to "+ gatt.getDevice().getName());
                bindGatt(gatt);
                //IMPORTANT: Need to call discover services even if UUID is known in advance, because services need to be loaded into gatt locally
                //IMPORTANT: Run service discovery in main thread to avoid deadlocks
                servoStateCallback.onConnect(gatt.getDevice());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w("BluetoothGattCallback", "Successfully disconnected from "+ gatt.getDevice().getName());
                servoStateCallback.onDisconnect(gatt.getDevice());
                unbindGatt();
                gatt.close();
                gatt.disconnect();

            }
        } else {
            Log.e("BluetoothGattCallback", "Error " + status +" encountered for " +
                    gatt.getDevice().getName() +", Disconnecting...");
            unbindGatt();
            gatt.close();
            gatt.disconnect();
        }
    }

    @Override
    public void onCharacteristicRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            servoPosition = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0); // read from offset 0 in byte array an uint16 (2 bytes)

            Log.d("ServoBluetoothGattCallback", "Servo position = " + servoPosition);
            servoStateCallback.onPositionRead(servoPosition);
        }
        else if (status == BluetoothGatt.GATT_READ_NOT_PERMITTED) {
            Log.e("ServoBluetoothGattCallback", "Read not permitted for characteristics: " + characteristic.getUuid());
        }
        else {
            Log.e("ServoBluetoothGattCallback", "Error " + status + " when reading characteristics " + characteristic.getUuid());
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.i("BluetoothGattCallback", "Wrote to characteristic " + characteristic.getUuid());
        }
        else if (status == BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH) {
            Log.e("BluetoothGattCallback", "Write exceeded connection ATT MTU!");
        }
        else if (status == BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
            Log.e("BluetoothGattCallback", "Write not permitted for characteristic " + characteristic.getUuid());
        }
        else {
            Log.e("BluetoothGattCallback", "Characteristic write failed for " + characteristic.getUuid() + " error: " + status);
        }
        writeAvailable = true;

    }

    @SuppressLint("MissingPermission")
    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        Log.w("ServoBluetoothGattCallback", "Discovered " + gatt.getServices().size() + " services for "+ gatt.getDevice().getName());
        //startServoPositionRead();
        //startServoPositionWrite(21);
        servoStateCallback.onServiceDiscover(gatt.getServices());
    }

    @SuppressLint("MissingPermission")
    @Override
    public void onCharacteristicChanged(
            BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        servoPosition = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0); // read from offset 0 in byte array an uint16 (2 bytes)
        Log.d("ServoBluetoothGattCallback", "Servo position = " + servoPosition);
        servoStateCallback.onPositionChange(servoPosition);
    }


    private void bindGatt(BluetoothGatt gatt) {
        this.gatt = gatt;
        isConnected = true;
        writeAvailable = true;
    }
    private void unbindGatt() {
        this.gatt = null;
        isConnected = false;
        writeAvailable = false;
    }

    public boolean isConnected() {
        return isConnected;
    }

    public boolean isWriteAvailable() {
        return writeAvailable;
    }

    public int getServoExpectedPosition() { // never know for sure until the peripheral tells us
        return servoPosition;
    }


    /**
     * start request for 1 servo position read
     */
    @SuppressLint("MissingPermission")
    void startServoPositionRead() {
        if (gatt != null) {
            BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
            if (service == null) {
                Log.e("ServoBluetoothCallback", "Service "+ SERVICE_UUID+ " is not found");
            } else {
                BluetoothGattCharacteristic characteristics = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
                if (characteristics == null) {
                    Log.e("ServoBluetoothCallback", "Characteristics "+ CHARACTERISTIC_UUID + " is not found");
                } else gatt.readCharacteristic(characteristics);

            }

        }
        else {
            Log.e("ServoBluetoothGattCallback", "Cannot read because GATT is null");
        }

    }

    /**
     * IMPORTANT: position will be parsed into an uint16
     * @param position
     */
    @SuppressLint("MissingPermission")
    void startServoPositionWrite(int position) {
        if (gatt != null) {
            BluetoothGattCharacteristic characteristics = gatt.getService(UUID.fromString(SERVICE_UUID)).getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
            characteristics.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE); // actually this will still generate a callback from phone's bt stack
            characteristics.setValue(position, BluetoothGattCharacteristic.FORMAT_UINT16, 0); // i think this is little endian?
            gatt.writeCharacteristic(characteristics); // sets characteristics values and write it back
            writeAvailable = false;
        }
        else {
            Log.e("ServoBluetoothGattCallback", "Cannot write because GATT is null");
        }
    }

    @SuppressLint("MissingPermission")
    void startServoPositionNotify() {
        BluetoothGattCharacteristic characteristics = gatt.getService(UUID.fromString(SERVICE_UUID)).getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
        gatt.setCharacteristicNotification(characteristics, true);
    }

    @SuppressLint("MissingPermission")
    void stopServoPositionNotify() {
        BluetoothGattCharacteristic characteristics = gatt.getService(UUID.fromString(SERVICE_UUID)).getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
        gatt.setCharacteristicNotification(characteristics, false);
    }


}