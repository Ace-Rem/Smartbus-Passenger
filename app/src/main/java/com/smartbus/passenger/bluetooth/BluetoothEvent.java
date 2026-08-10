package com.smartbus.passenger.bluetooth;

public class BluetoothEvent {

    private final Long boardingRequestId;
    private final Long tripId;
    private final String bluetoothIdentifier;

    public BluetoothEvent(Long boardingRequestId, Long tripId, String bluetoothIdentifier) {
        this.boardingRequestId = boardingRequestId;
        this.tripId = tripId;
        this.bluetoothIdentifier = bluetoothIdentifier;
    }

    public Long getBoardingRequestId() {
        return boardingRequestId;
    }

    public Long getTripId() {
        return tripId;
    }

    public String getBluetoothIdentifier() {
        return bluetoothIdentifier;
    }

    public String getCheckInId() {
        return "CHECKIN-" + boardingRequestId + "-" + tripId;
    }

    public String toPayload() {
        return "SMARTBUS_CHECKIN:"
                + "checkInId=" + getCheckInId()
                + ";request=" + boardingRequestId
                + ";trip=" + tripId
                + ";identifier=" + bluetoothIdentifier;
    }
}
