package com.smartbus.passenger;

final class FastBoardingSignalCancelRequest {

    final Long signalId;
    final Long tripId;
    final Long routeId;
    final Long destinationStopId;
    final String passengerIdentifier;

    FastBoardingSignalCancelRequest(Long signalId, Long tripId, Long routeId,
                                    Long destinationStopId, String passengerIdentifier) {
        this.signalId = signalId;
        this.tripId = tripId;
        this.routeId = routeId;
        this.destinationStopId = destinationStopId;
        this.passengerIdentifier = passengerIdentifier;
    }
}
