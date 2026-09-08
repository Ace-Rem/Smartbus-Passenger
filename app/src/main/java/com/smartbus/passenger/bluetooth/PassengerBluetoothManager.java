package com.smartbus.passenger.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;

public class PassengerBluetoothManager {

    private final Context context;
    private BluetoothLeAdvertiser advertiser;
    private AdvertiseCallback advertiseCallback;

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE)
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

    public BluetoothEvent createCheckInEvent(
            Long boardingRequestId,
            Long tripId,
            Long routeId,
            Long destinationStopId,
            Double destinationLatitude,
            Double destinationLongitude,
            String bluetoothIdentifier
    ) {
        return new BluetoothEvent(
                boardingRequestId,
                tripId,
                routeId,
                destinationStopId,
                destinationLatitude,
                destinationLongitude,
                bluetoothIdentifier
        );
    }

    public boolean startAdvertising(BluetoothEvent event) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || event == null || getState() != BluetoothConnectionState.READY) {
            return false;
        }
        try {
            advertiser = adapter.getBluetoothLeAdvertiser();
            if (advertiser == null) {
                return false;
            }
            stopAdvertising();
            AdvertiseSettings settings = new AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                    .setConnectable(false)
                    .setTimeout(0)
                    .build();
            AdvertiseData advertiseData = new AdvertiseData.Builder()
                    .setIncludeDeviceName(false)
                    .setIncludeTxPowerLevel(false)
                    // Keep route, destination and passenger identity in one
                    // packet; scan responses are not delivered consistently on
                    // every Android device.
                    .addManufacturerData(SmartBusBleCodec.MANUFACTURER_ID, event.toManufacturerPayload())
                    .build();
            advertiseCallback = new AdvertiseCallback() {
                @Override
                public void onStartFailure(int errorCode) {
                    super.onStartFailure(errorCode);
                    advertiseCallback = null;
                }
            };
            advertiser.startAdvertising(settings, advertiseData, advertiseCallback);
            return true;
        } catch (SecurityException | IllegalArgumentException ignored) {
            return false;
        }
    }

    public boolean isAdvertising() {
        return advertiser != null && advertiseCallback != null;
    }

    public void stopAdvertising() {
        if (advertiser == null || advertiseCallback == null) {
            return;
        }
        try {
            advertiser.stopAdvertising(advertiseCallback);
        } catch (SecurityException ignored) {
        }
        advertiseCallback = null;
    }
}
