package com.smartbus.passenger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class PassengerSelectionState {

    private TripModel selectedTrip;
    private RouteModel selectedRoute;
    private final List<StopModel> routeStops = new ArrayList<>();
    private StopModel boardingStop;
    private StopModel destinationStop;
    private BoardingRequestModel boardingRequest;
    private String bluetoothIdentifier;
    private String checkInStatus;

    Long selectedTripId() {
        return selectedTrip == null ? null : selectedTrip.id;
    }

    TripModel selectedTrip() {
        return selectedTrip;
    }

    Long selectedRouteId() {
        if (selectedRoute != null && selectedRoute.id != null) {
            return selectedRoute.id;
        }
        return selectedTrip == null ? null : selectedTrip.routeId;
    }

    RouteModel selectedRoute() {
        return selectedRoute;
    }

    List<StopModel> routeStops() {
        return Collections.unmodifiableList(routeStops);
    }

    StopModel boardingStop() {
        return boardingStop;
    }

    Long boardingStopId() {
        return boardingStop == null ? null : boardingStop.id;
    }

    StopModel destinationStop() {
        return destinationStop;
    }

    Long destinationStopId() {
        return destinationStop == null ? null : destinationStop.id;
    }

    BoardingRequestModel boardingRequest() {
        return boardingRequest;
    }

    String bluetoothIdentifier() {
        if (boardingRequest != null && boardingRequest.bluetoothIdentifier != null) {
            return boardingRequest.bluetoothIdentifier;
        }
        return bluetoothIdentifier;
    }

    String checkInStatus() {
        if (boardingRequest != null && boardingRequest.status != null) {
            return boardingRequest.status;
        }
        return checkInStatus;
    }

    Long boardingRequestId() {
        return boardingRequest == null ? null : boardingRequest.id;
    }

    boolean hasTrip() {
        return selectedTripId() != null;
    }

    boolean hasStops() {
        return !routeStops.isEmpty();
    }

    boolean canCreateBoardingRequest() {
        return selectedTripId() != null && boardingStopId() != null && destinationStopId() != null;
    }

    void markLocalCheckIn(String identifier) {
        bluetoothIdentifier = identifier;
        checkInStatus = "PENDING";
    }

    void restoreLocalSelection(String identifier, String status) {
        bluetoothIdentifier = identifier;
        checkInStatus = status == null || status.isBlank() ? "PENDING" : status;
    }

    void confirmLocalSelection(String identifier) {
        if (identifier != null && !identifier.isBlank()) {
            bluetoothIdentifier = identifier;
        }
        checkInStatus = "SELECTED";
    }

    boolean isLocalSelectionConfirmed() {
        return "SELECTED".equals(checkInStatus);
    }

    void selectTrip(TripModel trip) {
        if (trip == null || trip.id == null) {
            return;
        }
        Long previousTripId = selectedTripId();
        selectedTrip = copyTrip(trip);
        if (previousTripId == null || !previousTripId.equals(trip.id)) {
            boardingRequest = null;
            boardingStop = null;
            destinationStop = null;
            bluetoothIdentifier = null;
            checkInStatus = null;
        }
        if (selectedRoute != null && trip.routeId != null && !trip.routeId.equals(selectedRoute.id)) {
            selectedRoute = null;
            routeStops.clear();
        }
    }

    void setRoute(RouteModel route) {
        selectedRoute = copyRoute(route);
    }

    void setRouteStops(List<StopModel> stops) {
        routeStops.clear();
        if (stops != null) {
            for (StopModel stop : stops) {
                StopModel copy = copyStop(stop);
                if (copy != null && copy.id != null) {
                    routeStops.add(copy);
                }
            }
        }
        if (!containsStop(boardingStop)) {
            boardingStop = null;
        }
        if (!containsStop(destinationStop) || !destinationAfterBoarding(destinationStop)) {
            destinationStop = null;
        }
    }

    void setBoardingStop(StopModel stop) {
        if (stop == null || stop.id == null) {
            if (boardingStop == null && destinationStop == null) {
                return;
            }
            boardingStop = null;
            destinationStop = null;
            checkInStatus = null;
            return;
        }
        boolean changed = boardingStop == null || !stop.id.equals(boardingStop.id);
        boardingStop = copyStop(stop);
        if (changed) {
            checkInStatus = null;
        }
        if (!destinationAfterBoarding(destinationStop)) {
            destinationStop = null;
            checkInStatus = null;
        }
    }

    void setDestinationStop(StopModel stop) {
        if (stop == null || stop.id == null) {
            if (destinationStop == null) {
                return;
            }
            destinationStop = null;
            checkInStatus = null;
            return;
        }
        if (boardingStop != null
                && boardingStop.stopOrder != null
                && stop.stopOrder != null
                && stop.stopOrder <= boardingStop.stopOrder) {
            destinationStop = null;
            checkInStatus = null;
            return;
        }
        boolean changed = destinationStop == null || !stop.id.equals(destinationStop.id);
        destinationStop = copyStop(stop);
        if (changed) {
            checkInStatus = null;
        }
    }

    void syncBoardingRequest(BoardingRequestModel request) {
        if (request == null) {
            boardingRequest = null;
            return;
        }
        Long requestTripId = request.trip == null ? null : request.trip.id;
        if (selectedTripId() != null && requestTripId != null && !selectedTripId().equals(requestTripId)) {
            return;
        }
        boardingRequest = copyRequest(request);
        bluetoothIdentifier = request.bluetoothIdentifier;
        checkInStatus = request.status;
        if (request.trip != null) {
            selectTrip(request.trip);
        }
        if (request.boardingStop != null) {
            boardingStop = copyStop(request.boardingStop);
        }
        if (request.destinationStop != null) {
            destinationStop = copyStop(request.destinationStop);
        }
    }

    void clear() {
        selectedTrip = null;
        selectedRoute = null;
        routeStops.clear();
        boardingStop = null;
        destinationStop = null;
        boardingRequest = null;
        bluetoothIdentifier = null;
        checkInStatus = null;
    }

    List<StopModel> destinationChoices() {
        List<StopModel> choices = new ArrayList<>();
        if (boardingStop == null || boardingStop.stopOrder == null) {
            return choices;
        }
        for (StopModel stop : routeStops) {
            if (stop != null && stop.id != null && stop.stopOrder != null
                    && stop.stopOrder > boardingStop.stopOrder) {
                choices.add(copyStop(stop));
            }
        }
        return choices;
    }

    private boolean containsStop(StopModel target) {
        if (target == null || target.id == null) {
            return false;
        }
        for (StopModel stop : routeStops) {
            if (stop != null && target.id.equals(stop.id)) {
                return true;
            }
        }
        return false;
    }

    private boolean destinationAfterBoarding(StopModel destination) {
        if (destination == null) {
            return true;
        }
        if (boardingStop == null || boardingStop.stopOrder == null || destination.stopOrder == null) {
            return false;
        }
        return destination.stopOrder > boardingStop.stopOrder;
    }

    static TripModel copyTrip(TripModel source) {
        if (source == null) {
            return null;
        }
        TripModel trip = new TripModel();
        trip.id = source.id;
        trip.driverId = source.driverId;
        trip.routeId = source.routeId;
        trip.currentStopId = source.currentStopId;
        trip.status = source.status;
        trip.startedAt = source.startedAt;
        trip.currentLatitude = source.currentLatitude;
        trip.currentLongitude = source.currentLongitude;
        return trip;
    }

    static RouteModel copyRoute(RouteModel source) {
        if (source == null) {
            return null;
        }
        RouteModel route = new RouteModel();
        route.id = source.id;
        route.code = source.code;
        route.name = source.name;
        route.description = source.description;
        return route;
    }

    static StopModel copyStop(StopModel source) {
        if (source == null) {
            return null;
        }
        StopModel stop = new StopModel();
        stop.id = source.id;
        stop.routeId = source.routeId;
        stop.name = source.name;
        stop.latitude = source.latitude;
        stop.longitude = source.longitude;
        stop.stopOrder = source.stopOrder;
        return stop;
    }

    private BoardingRequestModel copyRequest(BoardingRequestModel source) {
        BoardingRequestModel request = new BoardingRequestModel();
        request.id = source.id;
        request.passenger = source.passenger;
        request.trip = copyTrip(source.trip);
        request.boardingStop = copyStop(source.boardingStop);
        request.destinationStop = copyStop(source.destinationStop);
        request.passengerRecordId = source.passengerRecordId;
        request.status = source.status;
        request.note = source.note;
        request.bluetoothIdentifier = source.bluetoothIdentifier;
        return request;
    }
}
