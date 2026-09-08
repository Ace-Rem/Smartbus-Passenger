package com.smartbus.passenger;

import android.content.SharedPreferences;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import retrofit2.Response;

final class PassengerDataRepository {

    interface DataCallback<T> {
        void onData(T data, boolean fromCache);
    }

    interface ErrorCallback {
        void onError(Throwable throwable);
    }

    private final SmartBusApi api;
    private final PassengerRouteDao routeDao;
    private final PassengerStopDao stopDao;
    private final PassengerTripDao tripDao;
    private final PassengerSelectionDao selectionDao;
    private final SharedPreferences preferences;
    private final ExecutorService executor;

    PassengerDataRepository(
            PassengerDatabase database,
            SharedPreferences preferences,
            ExecutorService executor
    ) {
        this.api = RetrofitClient.api();
        this.routeDao = database.routeDao();
        this.stopDao = database.stopDao();
        this.tripDao = database.tripDao();
        this.selectionDao = database.selectionDao();
        this.preferences = preferences;
        this.executor = executor;
    }

    void loadRoutes(DataCallback<List<RouteModel>> callback, ErrorCallback errorCallback) {
        executor.execute(() -> {
            List<RouteModel> cached = PassengerCacheMapper.routes(routeDao.routes());
            if (!cached.isEmpty()) {
                callback.onData(cached, true);
            }
            if (hasBootstrapCache()) {
                return;
            }
            try {
                Response<ApiResponse<BootstrapModel>> response = api.bootstrap().execute();
                ApiResponse<BootstrapModel> body = response.body();
                if (response.isSuccessful() && body != null && body.success && body.data != null) {
                    cacheBootstrap(body.data);
                    callback.onData(PassengerCacheMapper.routes(routeDao.routes()), false);
                    return;
                }
                Response<ApiResponse<List<RouteModel>>> fallback = api.routes().execute();
                ApiResponse<List<RouteModel>> fallbackBody = fallback.body();
                if (fallback.isSuccessful() && fallbackBody != null && fallbackBody.success && fallbackBody.data != null) {
                    cacheRoutes(fallbackBody.data);
                    callback.onData(fallbackBody.data, false);
                } else if (cached.isEmpty() && errorCallback != null) {
                    errorCallback.onError(new IOException("Không thể tải danh sách tuyến từ backend."));
                }
            } catch (IOException exception) {
                if (cached.isEmpty() && errorCallback != null) {
                    errorCallback.onError(exception);
                }
            }
        });
    }

    void loadStops(long routeId, DataCallback<List<StopModel>> callback, ErrorCallback errorCallback) {
        loadStopsFromRoom(routeId, callback, errorCallback);
    }

    void loadStopsFromRoom(long routeId, DataCallback<List<StopModel>> callback, ErrorCallback errorCallback) {
        executor.execute(() -> {
            List<StopModel> cached = PassengerCacheMapper.stops(stopDao.stopsByRoute(routeId));
            if (!cached.isEmpty()) {
                callback.onData(cached, true);
                return;
            }
            if (errorCallback != null) {
                errorCallback.onError(new IOException(
                        "Chưa có danh sách bến offline cho tuyến này. Mở tab Tuyến xe khi có mạng để tải dữ liệu một lần."
                ));
            }
        });
    }

    void findNearbyActiveTrips(
            double latitude,
            double longitude,
            DataCallback<NearbyActiveTripsModel> callback,
            ErrorCallback errorCallback
    ) {
        executor.execute(() -> {
            NearbyActiveTripsModel local = buildNearbyFromCache(latitude, longitude);
            if (local != null && local.boardingStop != null) {
                callback.onData(local, true);
                return;
            }
            if (errorCallback != null) {
                errorCallback.onError(new IOException(
                        "Chưa có dữ liệu bến/tuyến offline. Mở tab Tuyến xe để tải dữ liệu vào máy trước."
                ));
            }
        });
    }

    void findTrips(
            long routeId,
            long boardingStopId,
            long destinationStopId,
            DataCallback<List<TripModel>> callback,
            ErrorCallback errorCallback
    ) {
        executor.execute(() -> {
            List<TripModel> cached = listTripsForRoute(routeId);
            if (!cached.isEmpty()) {
                callback.onData(cached, true);
            } else if (errorCallback != null) {
                errorCallback.onError(new IOException(
                        "Chưa có chuyến offline cho tuyến này trong bộ nhớ máy."
                ));
            }
        });
    }

    void syncBootstrap() {
        executor.execute(() -> {
            if (hasBootstrapCache()) {
                return;
            }
            try {
                Response<ApiResponse<BootstrapModel>> response = api.bootstrap().execute();
                ApiResponse<BootstrapModel> body = response.body();
                if (response.isSuccessful() && body != null && body.success && body.data != null) {
                    cacheBootstrap(body.data);
                }
            } catch (IOException ignored) {
            }
        });
    }

    private boolean hasBootstrapCache() {
        return preferences.getBoolean("bootstrap_cache_ready", false);
    }

    void persistSelection(PassengerSelectionState state) {
        executor.execute(() -> {
            if (state == null || (!state.hasTrip() && state.selectedRouteId() == null)) {
                selectionDao.clear();
                return;
            }
            CachedSelectedTripEntity entity = new CachedSelectedTripEntity();
            entity.id = 1;
            TripModel trip = state.selectedTrip();
            entity.tripId = state.selectedTripId();
            entity.routeId = state.selectedRouteId();
            entity.tripStatus = trip == null ? null : trip.status;
            entity.currentStopId = trip == null ? null : trip.currentStopId;
            entity.currentLatitude = trip == null || trip.currentLatitude == null ? 0d : trip.currentLatitude.doubleValue();
            entity.currentLongitude = trip == null || trip.currentLongitude == null ? 0d : trip.currentLongitude.doubleValue();
            putStop(entity, state.boardingStop(), true);
            putStop(entity, state.destinationStop(), false);
            BoardingRequestModel request = state.boardingRequest();
            entity.boardingRequestId = request == null ? null : request.id;
            entity.boardingRequestStatus = request == null ? state.checkInStatus() : request.status;
            entity.bluetoothIdentifier = state.bluetoothIdentifier();
            selectionDao.upsert(entity);
        });
    }

    void clearSelection() {
        executor.execute(selectionDao::clear);
    }

    private List<TripModel> listTripsForRoute(long routeId) {
        List<TripModel> trips = PassengerCacheMapper.trips(tripDao.tripsByRoute(routeId));
        if (!trips.isEmpty()) {
            return trips;
        }
        return PassengerCacheMapper.trips(tripDao.allTrips());
    }

    private void putStop(CachedSelectedTripEntity entity, StopModel stop, boolean boarding) {
        if (stop == null || stop.id == null) {
            return;
        }
        if (boarding) {
            entity.boardingStopId = stop.id;
            entity.boardingStopName = stop.name;
            entity.boardingLatitude = stop.latitude == null ? 0d : stop.latitude.doubleValue();
            entity.boardingLongitude = stop.longitude == null ? 0d : stop.longitude.doubleValue();
            entity.boardingStopOrder = stop.stopOrder;
        } else {
            entity.destinationStopId = stop.id;
            entity.destinationStopName = stop.name;
            entity.destinationLatitude = stop.latitude == null ? 0d : stop.latitude.doubleValue();
            entity.destinationLongitude = stop.longitude == null ? 0d : stop.longitude.doubleValue();
            entity.destinationStopOrder = stop.stopOrder;
        }
    }

    private void cacheBootstrap(BootstrapModel data) {
        if (data == null) {
            return;
        }
        cacheRoutes(data.routes);
        cacheStops(data.stops);
        cacheTrips(data.activeTrips);
        preferences.edit().putBoolean("bootstrap_cache_ready", true).apply();
    }

    private void cacheRoutes(List<RouteModel> models) {
        List<CachedRouteEntity> entities = new ArrayList<>();
        if (models != null) {
            for (RouteModel model : models) {
                CachedRouteEntity entity = PassengerCacheMapper.toEntity(model);
                if (entity != null) {
                    entities.add(entity);
                }
            }
        }
        if (!entities.isEmpty()) {
            routeDao.upsertRoutes(entities);
        }
    }

    private void cacheStops(List<StopModel> models) {
        List<CachedStopEntity> entities = new ArrayList<>();
        if (models != null) {
            for (StopModel model : models) {
                CachedStopEntity entity = PassengerCacheMapper.toEntity(model);
                if (entity != null) {
                    entities.add(entity);
                }
            }
        }
        if (!entities.isEmpty()) {
            stopDao.upsertStops(entities);
        }
    }

    private void cacheTrips(List<TripModel> models) {
        List<CachedTripEntity> entities = new ArrayList<>();
        if (models != null) {
            for (TripModel model : models) {
                CachedTripEntity entity = PassengerCacheMapper.toEntity(model);
                if (entity != null) {
                    entities.add(entity);
                }
            }
        }
        if (!entities.isEmpty()) {
            tripDao.upsertTrips(entities);
        }
    }

    private NearbyActiveTripsModel buildNearbyFromCache(double latitude, double longitude) {
        List<CachedStopEntity> allStops = stopDao.allStops();
        if (allStops.isEmpty()) {
            return null;
        }
        CachedStopEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (CachedStopEntity stop : allStops) {
            if (stop.stopOrder <= 0) {
                continue;
            }
            double distance = distanceMeters(latitude, longitude, stop.latitude, stop.longitude);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = stop;
            }
        }
        if (nearest == null) {
            return null;
        }
        CachedStopEntity suggestedDestination = null;
        for (CachedStopEntity stop : stopDao.stopsByRoute(nearest.routeId)) {
            if (stop.stopOrder > nearest.stopOrder
                    && (suggestedDestination == null || stop.stopOrder > suggestedDestination.stopOrder)) {
                suggestedDestination = stop;
            }
        }
        NearbyActiveTripsModel model = new NearbyActiveTripsModel();
        model.boardingStop = PassengerCacheMapper.toModel(nearest);
        model.suggestedDestinationStop = PassengerCacheMapper.toModel(suggestedDestination);
        model.route = PassengerCacheMapper.toModel(routeDao.route(nearest.routeId));
        model.distanceMeters = nearestDistance;
        model.trips = listTripsForRoute(nearest.routeId);
        return model;
    }

    private double distanceMeters(double lat1, double lng1, double lat2, double lng2) {
        float[] result = new float[1];
        android.location.Location.distanceBetween(lat1, lng1, lat2, lng2, result);
        return result[0];
    }
}
