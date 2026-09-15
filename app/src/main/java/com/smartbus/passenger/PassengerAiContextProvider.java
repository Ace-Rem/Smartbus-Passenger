package com.smartbus.passenger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/** Builds the one passenger context consumed by Chat AI from the local cache. */
final class PassengerAiContextProvider {

    interface Callback {
        void onContext(Map<String, Object> context);
    }

    private final PassengerDatabase database;
    private final ExecutorService executor;

    PassengerAiContextProvider(PassengerDatabase database, ExecutorService executor) {
        this.database = database;
        this.executor = executor;
    }

    void load(PassengerSelectionState memoryFallback, Callback callback) {
        executor.execute(() -> callback.onContext(read(memoryFallback)));
    }

    private Map<String, Object> read(PassengerSelectionState fallback) {
        CachedSelectedTripEntity row = database.selectionDao().selected();
        TripModel trip = row == null || row.tripId == null ? null : PassengerCacheMapper.toModel(database.tripDao().trip(row.tripId));
        Long tripId = row == null ? null : row.tripId;
        Long routeId = row == null ? null : row.routeId;
        if (trip == null && fallback != null) {
            trip = PassengerSelectionState.copyTrip(fallback.selectedTrip());
        }
        if (tripId == null && trip != null) {
            tripId = trip.id;
        }
        if (routeId == null && fallback != null) {
            routeId = fallback.selectedRouteId();
        }
        if (routeId == null && trip != null) {
            routeId = trip.routeId;
        }

        RouteModel route = routeId == null ? null : PassengerCacheMapper.toModel(database.routeDao().route(routeId));
        if (route == null && fallback != null) {
            route = PassengerSelectionState.copyRoute(fallback.selectedRoute());
        }
        StopModel boarding = row == null ? null : selectedStop(row, true);
        StopModel destination = row == null ? null : selectedStop(row, false);
        if (boarding == null && fallback != null) {
            boarding = PassengerSelectionState.copyStop(fallback.boardingStop());
        }
        if (destination == null && fallback != null) {
            destination = PassengerSelectionState.copyStop(fallback.destinationStop());
        }
        List<StopModel> stops = routeId == null
                ? new ArrayList<>()
                : PassengerCacheMapper.stops(database.stopDao().stopsByRoute(routeId));
        if (stops.isEmpty() && fallback != null) {
            stops = new ArrayList<>(fallback.routeStops());
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("app", "smartbus-passenger");
        context.put("contextSource", row == null ? "LOCAL_MEMORY_FALLBACK" : "ROOM");
        context.put("selectionState", tripId == null ? "NO_SELECTION" : (route == null || stops.isEmpty()
                ? "PARTIAL_LOCAL_DATA" : "FULL_LOCAL_DATA"));
        context.put("selectedTripId", tripId);
        context.put("tripId", tripId);
        context.put("selectedTripName", tripId == null ? null : "Chuyến #" + tripId);
        context.put("status", trip == null ? null : trip.status);
        context.put("selectedTripCode", tripId == null ? null : "#" + tripId);
        context.put("selectedRouteId", routeId);
        context.put("selectedRouteName", route == null ? null : route.name);
        context.put("selectedRouteCode", route == null ? null : route.code);
        context.put("selectedRouteLabel", route == null ? null : routeLabel(route));
        context.put("routeName", route == null ? null : route.name);
        context.put("routeCode", route == null ? null : route.code);
        context.put("passengerLatitude", trip == null ? null : trip.currentLatitude);
        context.put("passengerLongitude", trip == null ? null : trip.currentLongitude);
        context.put("currentLatitude", trip == null ? null : trip.currentLatitude);
        context.put("currentLongitude", trip == null ? null : trip.currentLongitude);
        putStop(context, "boardingStop", boarding);
        putStop(context, "destinationStop", destination);
        putStop(context, "selectedBoardingStop", boarding);
        putStop(context, "selectedDestinationStop", destination);
        context.put("hasSelectedTrip", tripId != null);
        context.put("hasBoardingStop", boarding != null && boarding.id != null);
        context.put("hasDestinationStop", destination != null && destination.id != null);
        String checkIn = row == null ? (fallback == null ? null : fallback.checkInStatus()) : row.boardingRequestStatus;
        context.put("checkInStatus", checkIn);
        context.put("checkIn", checkIn != null && !checkIn.isBlank());
        context.put("boardingRequestId", row == null ? null : row.boardingRequestId);
        context.put("bluetoothIdentifier", row == null
                ? (fallback == null ? null : fallback.bluetoothIdentifier()) : row.bluetoothIdentifier);
        StopModel current = null;
        StopModel next = null;
        if (trip != null && trip.currentStopId != null) {
            for (StopModel stop : stops) {
                if (stop != null && trip.currentStopId.equals(stop.id)) {
                    current = stop;
                    break;
                }
            }
        }
        if (current != null && current.stopOrder != null) {
            for (StopModel stop : stops) {
                if (stop != null && stop.stopOrder != null && stop.stopOrder > current.stopOrder
                        && (next == null || stop.stopOrder < next.stopOrder)) {
                    next = stop;
                }
            }
        }
        putStop(context, "currentStop", current);
        putStop(context, "nextStop", next);
        List<Map<String, Object>> stopList = new ArrayList<>();
        for (StopModel stop : stops) {
            Map<String, Object> item = new LinkedHashMap<>();
            putStop(item, "stop", stop);
            stopList.add(item);
        }
        context.put("stops", stopList);
        context.put("stopsOnRoute", stopNames(stops));
        return context;
    }

    private StopModel selectedStop(CachedSelectedTripEntity row, boolean boarding) {
        StopModel stop = new StopModel();
        stop.id = boarding ? row.boardingStopId : row.destinationStopId;
        if (stop.id == null) return null;
        stop.routeId = row.routeId;
        stop.name = boarding ? row.boardingStopName : row.destinationStopName;
        stop.latitude = java.math.BigDecimal.valueOf(boarding ? row.boardingLatitude : row.destinationLatitude);
        stop.longitude = java.math.BigDecimal.valueOf(boarding ? row.boardingLongitude : row.destinationLongitude);
        stop.stopOrder = boarding ? row.boardingStopOrder : row.destinationStopOrder;
        return stop;
    }

    private void putStop(Map<String, Object> context, String prefix, StopModel stop) {
        context.put(prefix + "Id", stop == null ? null : stop.id);
        context.put(prefix + "Name", stop == null ? null : stop.name);
        context.put(prefix + "Latitude", stop == null ? null : stop.latitude);
        context.put(prefix + "Longitude", stop == null ? null : stop.longitude);
        context.put(prefix + "Order", stop == null ? null : stop.stopOrder);
    }

    private String routeLabel(RouteModel route) {
        if (route == null) return null;
        String code = route.code == null ? "" : route.code;
        String name = route.name == null ? "" : route.name;
        return code.isBlank() ? name : (name.isBlank() ? code : code + " - " + name);
    }

    private String stopNames(List<StopModel> stops) {
        StringBuilder result = new StringBuilder();
        for (StopModel stop : stops) {
            if (stop == null) continue;
            if (result.length() > 0) result.append(" | ");
            result.append(stop.stopOrder == null ? "?" : stop.stopOrder).append(". ").append(stop.name);
        }
        return result.toString();
    }
}
