package com.smartbus.passenger.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;

public class PassengerBluetoothManager {

    private final Context context;

    public PassengerBluetoothManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public BluetoothConnectionState getState() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return BluetoothConnectionState.UNSUPPORTED;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return BluetoothConnectionState.PERMISSION_REQUIRED;
        }
        try {
            return adapter.isEnabled()
                    ? BluetoothConnectionState.READY
                    : BluetoothConnectionState.DISABLED;
        } catch (SecurityException ignored) {
            return BluetoothConnectionState.PERMISSION_REQUIRED;
        }
    }

    public BluetoothEvent createCheckInEvent(Long boardingRequestId, Long tripId, String bluetoothIdentifier) {
        return new BluetoothEvent(boardingRequestId, tripId, bluetoothIdentifier);
    }
}
