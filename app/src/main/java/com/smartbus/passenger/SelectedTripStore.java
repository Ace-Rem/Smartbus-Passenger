package com.smartbus.passenger;

import android.content.SharedPreferences;
import java.math.BigDecimal;

final class SelectedTripStore {

    private static final String KEY_TRIP_ID = "selected_trip_id";
    private static final String KEY_ROUTE_ID = "selected_trip_route_id";
    private static final String KEY_STATUS = "selected_trip_status";
    private static final String KEY_CURRENT_STOP_ID = "selected_trip_current_stop_id";
    private static final String KEY_CURRENT_LATITUDE = "selected_trip_current_latitude";
    private static final String KEY_CURRENT_LONGITUDE = "selected_trip_current_longitude";
    private static final String KEY_BOARDING_REQUEST_ID = "selected_trip_boarding_request_id";
    private static final String KEY_BOARDING_REQUEST_STATUS = "selected_trip_boarding_request_status";
    private static final String KEY_BLUETOOTH_IDENTIFIER = "selected_trip_bluetooth_identifier";
    private static final String KEY_BOARDING_STOP_PREFIX = "selected_trip_boarding_stop_";
    private static final String KEY_DESTINATION_STOP_PREFIX = "selected_trip_destination_stop_";

    private final SharedPreferences preferences;
    private Selection selection;

    SelectedTripStore(SharedPreferences preferences) {
        this.preferences = preferences;
        this.selection = load();
    }

    Selection selection() {
        return selection;
    }

    PassengerSelectionState state() {
        PassengerSelectionState state = new PassengerSelectionState();
        state.selectTrip(trip());
        state.setBoardingStop(boardingStop());
        state.setDestinationStop(destinationStop());
        state.syncBoardingRequest(boardingRequest());
        return state;
    }

    void persist(PassengerSelectionState state) {
        if (state == null || !state.hasTrip()) {
            clear();
            return;
        }
        selection.tripId = state.selectedTripId();
        selection.routeId = state.selectedRouteId();
        TripModel trip = state.selectedTrip();
        selection.tripStatus = trip == null ? null : trip.status;
        selection.currentStopId = trip == null ? null : trip.currentStopId;
        selection.currentLatitude = trip == null ? null : trip.currentLatitude;
        selection.currentLongitude = trip == null ? null : trip.currentLongitude;
        BoardingRequestModel request = state.boardingRequest();
        selection.boardingRequestId = request == null ? null : request.id;
        selection.boardingRequestStatus = request == null ? null : request.status;
        selection.bluetoothIdentifier = request == null ? null : request.bluetoothIdentifier;
        selection.boardingStop = PassengerSelectionState.copyStop(state.boardingStop());
        selection.destinationStop = PassengerSelectionState.copyStop(state.destinationStop());
        persist();
    }

    boolean hasTrip() {
        return selection.tripId != null;
    }

    void selectTrip(TripModel trip, StopModel boardingStop, StopModel destinationStop) {
        if (trip == null || trip.id == null) {
            return;
        }
        selection.tripId = trip.id;
        selection.routeId = trip.routeId;
        selection.tripStatus = trip.status;
        selection.currentStopId = trip.currentStopId;
        selection.currentLatitude = trip.currentLatitude;
        selection.currentLongitude = trip.currentLongitude;
        if (boardingStop != null && boardingStop.id != null) {
            selection.boardingStop = copyStop(boardingStop);
        }
        if (destinationStop != null && destinationStop.id != null) {
            selection.destinationStop = copyStop(destinationStop);
        }
        persist();
    }

    void syncBoardingRequest(BoardingRequestModel request) {
        if (request == null) {
            selection.boardingRequestId = null;
            selection.boardingRequestStatus = null;
            selection.bluetoothIdentifier = null;
            persist();
            return;
        }
        Long requestTripId = request.trip == null ? null : request.trip.id;
        if (selection.tripId != null && requestTripId != null && !selection.tripId.equals(requestTripId)) {
            return;
        }
        if (request.trip != null) {
            selectTrip(request.trip, request.boardingStop, request.destinationStop);
        }
        selection.boardingRequestId = request.id;
        selection.boardingRequestStatus = request.status;
        selection.bluetoothIdentifier = request.bluetoothIdentifier;
        if (request.boardingStop != null && request.boardingStop.id != null) {
            selection.boardingStop = copyStop(request.boardingStop);
        }
        if (request.destinationStop != null && request.destinationStop.id != null) {
            selection.destinationStop = copyStop(request.destinationStop);
        }
        persist();
    }

    void clear() {
        selection = new Selection();
        preferences.edit()
                .remove(KEY_TRIP_ID)
                .remove(KEY_ROUTE_ID)
                .remove(KEY_STATUS)
                .remove(KEY_CURRENT_STOP_ID)
                .remove(KEY_CURRENT_LATITUDE)
                .remove(KEY_CURRENT_LONGITUDE)
                .remove(KEY_BOARDING_REQUEST_ID)
                .remove(KEY_BOARDING_REQUEST_STATUS)
                .remove(KEY_BLUETOOTH_IDENTIFIER)
                .remove(KEY_BOARDING_STOP_PREFIX + "id")
                .remove(KEY_BOARDING_STOP_PREFIX + "route_id")
                .remove(KEY_BOARDING_STOP_PREFIX + "name")
                .remove(KEY_BOARDING_STOP_PREFIX + "latitude")
                .remove(KEY_BOARDING_STOP_PREFIX + "longitude")
                .remove(KEY_BOARDING_STOP_PREFIX + "order")
                .remove(KEY_DESTINATION_STOP_PREFIX + "id")
                .remove(KEY_DESTINATION_STOP_PREFIX + "route_id")
                .remove(KEY_DESTINATION_STOP_PREFIX + "name")
                .remove(KEY_DESTINATION_STOP_PREFIX + "latitude")
                .remove(KEY_DESTINATION_STOP_PREFIX + "longitude")
                .remove(KEY_DESTINATION_STOP_PREFIX + "order")
                .apply();
    }

    TripModel trip() {
        if (selection.tripId == null) {
            return null;
        }
        TripModel trip = new TripModel();
        trip.id = selection.tripId;
        trip.routeId = selection.routeId;
        trip.status = selection.tripStatus;
        trip.currentStopId = selection.currentStopId;
        trip.currentLatitude = selection.currentLatitude;
        trip.currentLongitude = selection.currentLongitude;
        return trip;
    }

    BoardingRequestModel boardingRequest() {
        if (selection.boardingRequestId == null) {
            return null;
        }
        BoardingRequestModel request = new BoardingRequestModel();
        request.id = selection.boardingRequestId;
        request.trip = trip();
        request.boardingStop = boardingStop();
        request.destinationStop = destinationStop();
        request.status = selection.boardingRequestStatus;
        request.bluetoothIdentifier = selection.bluetoothIdentifier;
        return request;
    }

    StopModel boardingStop() {
        return copyStop(selection.boardingStop);
    }

    StopModel destinationStop() {
        return copyStop(selection.destinationStop);
    }

    Long tripId() {
        return selection.tripId;
    }

    Long routeId() {
        return selection.routeId;
    }

    Long boardingRequestId() {
        return selection.boardingRequestId;
    }

    String boardingRequestStatus() {
        return selection.boardingRequestStatus;
    }

    String bluetoothIdentifier() {
        return selection.bluetoothIdentifier;
    }

    private Selection load() {
        Selection value = new Selection();
        value.tripId = readLong(KEY_TRIP_ID);
        value.routeId = readLong(KEY_ROUTE_ID);
        value.tripStatus = preferences.getString(KEY_STATUS, null);
        value.currentStopId = readLong(KEY_CURRENT_STOP_ID);
        value.currentLatitude = readBigDecimal(KEY_CURRENT_LATITUDE);
        value.currentLongitude = readBigDecimal(KEY_CURRENT_LONGITUDE);
        value.boardingRequestId = readLong(KEY_BOARDING_REQUEST_ID);
        value.boardingRequestStatus = preferences.getString(KEY_BOARDING_REQUEST_STATUS, null);
        value.bluetoothIdentifier = preferences.getString(KEY_BLUETOOTH_IDENTIFIER, null);
        value.boardingStop = readStop(KEY_BOARDING_STOP_PREFIX);
        value.destinationStop = readStop(KEY_DESTINATION_STOP_PREFIX);
        return value;
    }

    private void persist() {
        SharedPreferences.Editor editor = preferences.edit()
                .putLong(KEY_TRIP_ID, selection.tripId == null ? -1L : selection.tripId)
                .putLong(KEY_ROUTE_ID, selection.routeId == null ? -1L : selection.routeId)
                .putString(KEY_STATUS, selection.tripStatus)
                .putLong(KEY_CURRENT_STOP_ID, selection.currentStopId == null ? -1L : selection.currentStopId)
                .putString(KEY_CURRENT_LATITUDE, decimalString(selection.currentLatitude))
                .putString(KEY_CURRENT_LONGITUDE, decimalString(selection.currentLongitude))
                .putLong(KEY_BOARDING_REQUEST_ID, selection.boardingRequestId == null ? -1L : selection.boardingRequestId)
                .putString(KEY_BOARDING_REQUEST_STATUS, selection.boardingRequestStatus)
                .putString(KEY_BLUETOOTH_IDENTIFIER, selection.bluetoothIdentifier);
        putStop(editor, KEY_BOARDING_STOP_PREFIX, selection.boardingStop);
        putStop(editor, KEY_DESTINATION_STOP_PREFIX, selection.destinationStop);
        editor.apply();
    }

    private StopModel readStop(String prefix) {
        Long id = readLong(prefix + "id");
        if (id == null) {
            return null;
        }
        StopModel stop = new StopModel();
        stop.id = id;
        stop.routeId = readLong(prefix + "route_id");
        stop.name = preferences.getString(prefix + "name", null);
        stop.latitude = readBigDecimal(prefix + "latitude");
        stop.longitude = readBigDecimal(prefix + "longitude");
        stop.stopOrder = readInteger(prefix + "order");
        return stop;
    }

    private void putStop(SharedPreferences.Editor editor, String prefix, StopModel stop) {
        if (stop == null || stop.id == null) {
            editor.remove(prefix + "id")
                    .remove(prefix + "route_id")
                    .remove(prefix + "name")
                    .remove(prefix + "latitude")
                    .remove(prefix + "longitude")
                    .remove(prefix + "order");
            return;
        }
        editor.putLong(prefix + "id", stop.id)
                .putLong(prefix + "route_id", stop.routeId == null ? -1L : stop.routeId)
                .putString(prefix + "name", stop.name)
                .putString(prefix + "latitude", decimalString(stop.latitude))
                .putString(prefix + "longitude", decimalString(stop.longitude));
        if (stop.stopOrder == null) {
            editor.remove(prefix + "order");
        } else {
            editor.putInt(prefix + "order", stop.stopOrder);
        }
    }

    private Long readLong(String key) {
        long value = preferences.getLong(key, -1L);
        return value > 0 ? value : null;
    }

    private Integer readInteger(String key) {
        return preferences.contains(key) ? preferences.getInt(key, 0) : null;
    }

    private BigDecimal readBigDecimal(String key) {
        String value = preferences.getString(key, null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String decimalString(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private StopModel copyStop(StopModel source) {
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

    static final class Selection {
        Long tripId;
        Long routeId;
        String tripStatus;
        Long currentStopId;
        BigDecimal currentLatitude;
        BigDecimal currentLongitude;
        Long boardingRequestId;
        String boardingRequestStatus;
        String bluetoothIdentifier;
        StopModel boardingStop;
        StopModel destinationStop;
    }
}
