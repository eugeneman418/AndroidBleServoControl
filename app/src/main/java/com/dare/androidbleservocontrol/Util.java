package com.dare.androidbleservocontrol;

import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public class Util {
    public static boolean hasPermission(Activity activity, String permissionType) {
        return ContextCompat.checkSelfPermission(activity, permissionType) == PackageManager.PERMISSION_GRANTED;
    }


    public static void requestPermissions(Activity activity, int request_code, String... permissions) {
        List<String> permissionsToRequest = new ArrayList<>();
        for (var permission: permissions) {
            if (!hasPermission(activity, permission))
                permissionsToRequest.add(permission);
        }
        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    activity,
                    permissionsToRequest.toArray(new String[0]),
                    request_code
            );
        }
    }

    public static byte[] intToUint16(int value, boolean littleEndian) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("Value out of range for uint16: " + value);
        }

        byte[] result = new byte[2];
        if (littleEndian) {
            result[0] = (byte) (value & 0xFF);        // Low byte first
            result[1] = (byte) ((value >> 8) & 0xFF); // High byte second
        } else {
            result[0] = (byte) ((value >> 8) & 0xFF); // High byte first
            result[1] = (byte) (value & 0xFF);        // Low byte second
        }
        return result;
    }

    /**
     * I think ESP32 (& most ble firmware) is little endian
     * @param value
     * @return
     */
    public static byte[] intToUint16LittleEndian(int value) {
        return intToUint16(value, true);
    }

    public static byte[] intToUint16bigEndian(int value) {
        return intToUint16(value, false);
    }


}
