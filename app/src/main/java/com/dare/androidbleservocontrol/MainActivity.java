package com.dare.androidbleservocontrol;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ComponentCaller;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattService;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    TextView servoPositionView;
    SeekBar servoControlBar;

    private final int PERMISSION_REQUEST_BLUETOOTH = 1;
    private final int ENABLE_REQUEST_BLUETOOTH = 2;

    private final int SERVO_CONTROL_BAR_RESOLUTION = 100;
    private final int SERVO_MIN_PULSE_WIDTH = 500;
    private final int SERVO_MAX_PULSE_WIDTH = 2500;


    private ServoBluetooth servoBluetooth;

    private boolean canWrite;


    private final Handler debounceHandler = new Handler();
    private Runnable updateRunnable;

    ServoStateCallback servoStateCallback = new ServoStateCallback() {

        @Override
        void onDisconnect(BluetoothDevice device) {
            runOnUiThread(() -> {
                servoPositionView.setText("Disconnected");
                servoControlBar.setEnabled(false);
                servoControlBar.setOnSeekBarChangeListener(null);
            });
            servoBluetooth.startScan(); //rescan for device

        }

        @Override
        void onConnect(BluetoothDevice device) {
            new Handler(Looper.getMainLooper()).post(servoBluetooth::startServiceDiscovery);
        }

        @Override
        void onServiceDiscover(List<BluetoothGattService> services) {
            servoBluetooth.readPosition();
            servoBluetooth.startPositionListen();
            canWrite = true;
            runOnUiThread(() -> {
                servoControlBar.setEnabled(true);
                servoControlBar.setOnSeekBarChangeListener(servoControlBarListener);
            });
        }

        @Override
        void onPositionRead(int position) {
            canWrite = true;
            runOnUiThread(() -> {
                servoPositionView.setText(String.valueOf(position));
                servoControlBar.setEnabled(true); // reenable control when response recieved, this makes sure servo only issues one command at any time
                // this update is 'forced' by peripheral, so disable debounce
                servoControlBar.setProgress(servoPositionToSeekBarProgress(position));
            });


        }

        @Override
        void onPositionChange(int position) {
            onPositionRead(position);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        servoPositionView = findViewById(R.id.servoPositionView);
        servoControlBar = findViewById(R.id.servoControlBar);

        servoPositionView.setText("Disconnected");
        servoControlBar.setEnabled(false);

        checkBleHardware();
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission(); // startBluetooth will be called in onRequestPermissionResult callback
        } else {
            startBluetooth();
        }
    }

    private boolean hasBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return Util.hasPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) &&
                    Util.hasPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT);
        }
        else {
            return Util.hasPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) &&
                    Util.hasPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Util.requestPermissions(this, PERMISSION_REQUEST_BLUETOOTH,
                    android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT);
        }
        else {
            Util.requestPermissions(this, PERMISSION_REQUEST_BLUETOOTH,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION);
        }
    }

    private void checkBleHardware() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Log.d("MainActivity", "BLE not supported");
            finish();
        }
    }

    @SuppressLint("MissingPermission")
    private void startBluetooth() {
        if (!BluetoothAdapter.getDefaultAdapter().isEnabled()) { // turn on bluetooth if it's off
            // servoBluetooth will then be set up in onActivityResult callback
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, ENABLE_REQUEST_BLUETOOTH);
        }
        else {
            servoBluetooth = new ServoBluetooth(this, servoStateCallback);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_BLUETOOTH) {
            // Note, permissions are granted sequentially, so we cannot start bluetooth until all permissions are granted
            if (hasBluetoothPermission()) startBluetooth(); // initialize bluetooth communciation

        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data, @NonNull ComponentCaller caller) {
        super.onActivityResult(requestCode, resultCode, data, caller);
        if (requestCode == ENABLE_REQUEST_BLUETOOTH) {
            if (resultCode == RESULT_OK) {
                // Bluetooth has been enabled
                Log.d("MainActivity", "Bluetooth enabled by user");
                servoBluetooth = new ServoBluetooth(this, servoStateCallback); // Initialize Bluetooth communication
            } else {
                // User denied to turn on Bluetooth
                Log.e("MainActivity", "Bluetooth not enabled, quitting");
                finish(); // Close the app if Bluetooth is required
            }
        }

    }

    private int servoPositionToSeekBarProgress(int servoPosition) {
        return SERVO_CONTROL_BAR_RESOLUTION *
                (servoPosition - SERVO_MIN_PULSE_WIDTH)
                /(SERVO_MAX_PULSE_WIDTH - SERVO_MIN_PULSE_WIDTH);
    }

    private int seekBarProgressToServoPosition(int progress) {
        return SERVO_MIN_PULSE_WIDTH + progress * (SERVO_MAX_PULSE_WIDTH - SERVO_MIN_PULSE_WIDTH)
                / SERVO_CONTROL_BAR_RESOLUTION;
    }

    private SeekBar.OnSeekBarChangeListener servoControlBarListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser || !servoControlBar.isEnabled()) return;

            // Cancel any previously scheduled update
            if (updateRunnable != null) debounceHandler.removeCallbacks(updateRunnable);

            // Schedule a new update after 20ms of no changes
            updateRunnable = () -> servoBluetooth.updateServoPosition(seekBarProgressToServoPosition(progress));
            servoControlBar.setEnabled(false); // disable control until peripheral ACK
            canWrite = false;
            debounceHandler.postDelayed(updateRunnable, 20);  // 20ms debounce time
            // basically, it will cancel servo update request unless the slide bar has be still for 300ms
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            // Optional: cancel any pending update when dragging starts
            if (updateRunnable != null) debounceHandler.removeCallbacks(updateRunnable);
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            // Optional: force immediate update when user lifts finger (if you want)
        }
    };



}