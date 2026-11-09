package com.smartbus.passenger.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelUuid;
import androidx.core.content.ContextCompat;
import java.util.Collections;
import java.util.List;

/** Central/GATT client used by the passenger app for one automatic check-in. */
public class PassengerBluetoothManager {

    public interface Listener {
        void onMessage(String message);
    }

    private static final int MAX_ATTEMPTS = 3;
    private static final long SCAN_TIMEOUT_MS = 8_000L;
    private static final long RETRY_DELAY_MS = 600L;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothLeScanner scanner;
    private ScanCallback scanCallback;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic characteristic;
    private BluetoothGattCharacteristic identityCharacteristic;
    private BluetoothEvent pendingEvent;
    private Listener listener;
    private int attempt;
    private boolean sending;
    private boolean completed;
    private boolean retryScheduled;
    private final Runnable scanTimeout = () -> retry("Hết thời gian tìm thiết bị tài xế.");

    public PassengerBluetoothManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public BluetoothConnectionState getState() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            return BluetoothConnectionState.UNSUPPORTED;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED)) {
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
        return new BluetoothEvent(boardingRequestId, tripId, routeId, destinationStopId,
                destinationLatitude, destinationLongitude, bluetoothIdentifier);
    }

    public synchronized boolean startCheckIn(BluetoothEvent event, Listener callback) {
        if (event == null || event.getRouteId() == null || event.getDestinationStopId() == null
                || getState() != BluetoothConnectionState.READY) {
            return false;
        }
        stopCheckIn();
        pendingEvent = event;
        listener = callback;
        attempt = 0;
        sending = true;
        completed = false;
        notifyMessage("Passenger đang quét Driver (không quảng bá BLE)...");
        startAttempt();
        return true;
    }

    private void startAttempt() {
        if (!sending || completed) return;
        retryScheduled = false;
        if (attempt >= MAX_ATTEMPTS) {
            fail("Không tìm thấy hoặc không ghi được vào thiết bị tài xế.");
            return;
        }
        attempt++;
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        try {
            scanner = adapter == null ? null : adapter.getBluetoothLeScanner();
            if (scanner == null) {
                fail("Bluetooth LE scanner chưa sẵn sàng.");
                return;
            }
            ScanFilter filter = new ScanFilter.Builder()
                    .setServiceUuid(new ParcelUuid(SmartBusBleCodec.SERVICE_UUID))
                    .build();
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            scanCallback = new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    if (result != null && result.getDevice() != null) connect(result.getDevice());
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    if (results != null && !results.isEmpty() && results.get(0) != null
                            && results.get(0).getDevice() != null) {
                        connect(results.get(0).getDevice());
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    retry("BLE scan lỗi: " + errorCode);
                }
            };
            scanner.startScan(Collections.singletonList(filter), settings, scanCallback);
            handler.postDelayed(scanTimeout, SCAN_TIMEOUT_MS);
        } catch (SecurityException | IllegalArgumentException exception) {
            retry("Không thể bắt đầu BLE scan.");
        }
    }

    private synchronized void connect(BluetoothDevice device) {
        if (!sending || completed || gatt != null) return;
        stopScan();
        handler.removeCallbacks(scanTimeout);
        notifyMessage("Đã tìm thấy Driver, đang kết nối GATT...");
        try {
            gatt = device.connectGatt(context, false, new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt changedGatt, int status, int state) {
                    if (state == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                        notifyMessage("Đã kết nối Driver, đang tìm characteristic check-in...");
                        try {
                            changedGatt.discoverServices();
                        } catch (SecurityException ignored) {
                            retry("Không thể tìm service BLE.");
                        }
                    } else if (!completed) {
                        retry("");
                    }
                }

                @Override
                public void onServicesDiscovered(BluetoothGatt changedGatt, int status) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        retry("Không tìm thấy service BLE.");
                        return;
                    }
                    BluetoothGattService service = changedGatt.getService(SmartBusBleCodec.SERVICE_UUID);
                    characteristic = service == null ? null
                            : service.getCharacteristic(SmartBusBleCodec.CHECK_IN_CHARACTERISTIC_UUID);
                    if (characteristic == null
                            || (characteristic.getProperties() & (BluetoothGattCharacteristic.PROPERTY_WRITE
                            | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0) {
                        retry("Thiết bị tài xế thiếu characteristic ghi check-in.");
                        return;
                    }
                    identityCharacteristic = service.getCharacteristic(
                            SmartBusBleCodec.IDENTITY_CHARACTERISTIC_UUID);
                    if (identityCharacteristic != null
                            && (identityCharacteristic.getProperties()
                            & (BluetoothGattCharacteristic.PROPERTY_WRITE
                            | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0
                            && pendingEvent != null
                            && pendingEvent.getBluetoothIdentifier() != null
                            && !pendingEvent.getBluetoothIdentifier().isBlank()) {
                        writeIdentity(changedGatt);
                    } else {
                        // Older Driver builds may expose only the business
                        // characteristic. Keep them compatible.
                        writePayload(changedGatt);
                    }
                }

                @Override
                public void onCharacteristicWrite(BluetoothGatt changedGatt,
                                                  BluetoothGattCharacteristic changedCharacteristic,
                                                  int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        if (identityCharacteristic != null
                                && changedCharacteristic != null
                                && SmartBusBleCodec.IDENTITY_CHARACTERISTIC_UUID.equals(
                                changedCharacteristic.getUuid())) {
                            writePayload(changedGatt);
                            return;
                        }
                        completed = true;
                        notifyMessage("Đã ghi payload check-in vào Driver thành công.");
                        closeGatt();
                        sending = false;
                    } else {
                        retry("");
                    }
                }
            });
        } catch (SecurityException exception) {
            retry("Không có quyền kết nối Bluetooth.");
        }
    }

    private void writeIdentity(BluetoothGatt connectedGatt) {
        if (pendingEvent == null || identityCharacteristic == null) {
            writePayload(connectedGatt);
            return;
        }
        byte[] identity = pendingEvent.getBluetoothIdentifier().getBytes(
                java.nio.charset.StandardCharsets.UTF_8);
        identityCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                int result = connectedGatt.writeCharacteristic(identityCharacteristic, identity,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                if (result != BluetoothGatt.GATT_SUCCESS) {
                    retry("BLE từ chối ghi mã hành khách, đang thử lại...");
                }
            } else {
                identityCharacteristic.setValue(identity);
                if (!connectedGatt.writeCharacteristic(identityCharacteristic)) {
                    retry("BLE từ chối ghi mã hành khách, đang thử lại...");
                }
            }
        } catch (SecurityException | IllegalArgumentException exception) {
            retry("Không thể ghi mã hành khách BLE.");
        }
    }

    private void writePayload(BluetoothGatt connectedGatt) {
        if (pendingEvent == null || characteristic == null) {
            retry("Thiếu dữ liệu check-in BLE.");
            return;
        }
        byte[] payload = pendingEvent.toPayload();
        notifyMessage("Đã tìm thấy Driver; payload chuẩn bị gửi: "
                + new String(payload, java.nio.charset.StandardCharsets.UTF_8));
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                int writeResult = connectedGatt.writeCharacteristic(characteristic, payload,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                if (writeResult != BluetoothGatt.GATT_SUCCESS) {
                    retry("BLE từ chối ghi payload, đang thử lại...");
                }
            } else {
                characteristic.setValue(payload);
                if (!connectedGatt.writeCharacteristic(characteristic)) {
                    retry("BLE từ chối ghi payload, đang thử lại...");
                }
            }
        } catch (SecurityException | IllegalArgumentException exception) {
            retry("Không thể ghi payload BLE.");
        }
    }

    private synchronized void retry(String message) {
        if (!sending || completed || retryScheduled) return;
        retryScheduled = true;
        handler.removeCallbacks(scanTimeout);
        stopScan();
        closeGatt();
        characteristic = null;
        identityCharacteristic = null;
        notifyMessage(message);
        handler.postDelayed(this::startAttempt, RETRY_DELAY_MS);
    }

    private void fail(String message) {
        notifyMessage(": " + message);
        stopCheckIn();
    }

    public synchronized void stopCheckIn() {
        sending = false;
        completed = false;
        retryScheduled = false;
        handler.removeCallbacksAndMessages(null);
        stopScan();
        closeGatt();
        characteristic = null;
        identityCharacteristic = null;
        pendingEvent = null;
    }

    public void stopAdvertising() {
        // Source-compatible alias for old lifecycle calls.
        stopCheckIn();
    }

    public boolean isSending() { return sending; }
    public boolean isAdvertising() { return isSending(); }

    private void stopScan() {
        if (scanner != null && scanCallback != null) {
            try {
                scanner.stopScan(scanCallback);
            } catch (SecurityException ignored) {
            }
        }
        scanCallback = null;
    }

    private void closeGatt() {
        if (gatt == null) return;
        try {
            gatt.disconnect();
            gatt.close();
        } catch (SecurityException ignored) {
        }
        gatt = null;
    }

    private void notifyMessage(String message) {
        if (listener != null) listener.onMessage(message);
    }
}
