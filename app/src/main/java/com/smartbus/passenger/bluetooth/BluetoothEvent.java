package com.smartbus.passenger.bluetooth;

public class BluetoothEvent {

    private final Long boardingRequestId;
    private final Long tripId;
    private final Long routeId;
    private final Long destinationStopId;
    private final Double destinationLatitude;
    private final Double destinationLongitude;
    private final String bluetoothIdentifier;

    public BluetoothEvent(
            Long boardingRequestId,
            Long tripId,
            Long routeId,
            Long destinationStopId,
            Double destinationLatitude,
            Double destinationLongitude,
            String bluetoothIdentifier
    ) {
        this.boardingRequestId = boardingRequestId;
        this.tripId = tripId;
        this.routeId = routeId;
        this.destinationStopId = destinationStopId;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.bluetoothIdentifier = bluetoothIdentifier;
    }

    public Long getBoardingRequestId() {
        return boardingRequestId;
    }

    public Long getTripId() {
        return tripId;
    }

    public Long getRouteId() {
        return routeId;
    }

    public Long getDestinationStopId() {
        return destinationStopId;
    }

    public Double getDestinationLatitude() {
        return destinationLatitude;
    }

    public Double getDestinationLongitude() {
        return destinationLongitude;
    }

    public String getBluetoothIdentifier() {
        return bluetoothIdentifier;
    }

    public String getCheckInId() {
        return "CHECKIN-" + (routeId == null ? tripId : routeId) + "-" + bluetoothIdentifier;
    }

    public byte[] toManufacturerPayload() {
        return SmartBusBleCodec.encode(
                routeId == null ? 0L : routeId,
                destinationStopId == null ? 0L : destinationStopId,
                bluetoothIdentifier
        );
    }

    public byte[] toIdentifierPayload() {
        return SmartBusBleCodec.encodeIdentifier(bluetoothIdentifier);
    }
}
