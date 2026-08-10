package com.smartbus.passenger;

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
    private final ExecutorService executor;

    PassengerDataRepository(PassengerDatabase database, ExecutorService executor) {
        this.api = RetrofitClient.api();
        this.routeDao = database.routeDao();
        this.stopDao = database.stopDao();
        this.tripDao = database.tripDao();
        this.executor = executor;
    }

    void loadRoutes(DataCallback<List<RouteModel>> callback, ErrorCallback errorCallback) {
        executor.execute(() -> {
            List<RouteModel> cached = PassengerCacheMapper.routes(routeDao.routes());
            if (!cached.isEmpty()) {
                callback.onData(cached, true);
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
        executor.execute(() -> {
            List<StopModel> cached = PassengerCacheMapper.stops(stopDao.stopsByRoute(routeId));
            if (!cached.isEmpty()) {
                callback.onData(cached, true);
            }
            try {
                Response<ApiResponse<List<StopModel>>> response = api.stops(routeId).execute();
                ApiResponse<List<StopModel>> body = response.body();
                if (response.isSuccessful() && body != null && body.success && body.data != null) {
                    stopDao.deleteByRoute(routeId);
                    cacheStops(body.data);
                    callback.onData(body.data, false);
                } else if (cached.isEmpty() && errorCallback != null) {
                    errorCallback.onError(new IOException("Không thể tải danh sách bến từ backend."));
                }
            } catch (IOException exception) {
                if (cached.isEmpty() && errorCallback != null) {
                    errorCallback.onError(exception);
                }
            }
        });
    }

    void findTrips(long routeId, long boardingStopId, long destinationStopId,
                   DataCallback<List<TripModel>> callback, ErrorCallback errorCallback) {
        executor.execute(() -> {
            List<TripModel> cached = PassengerCacheMapper.trips(tripDao.tripsByRouteAndStatus(routeId, "IN_PROGRESS"));
            if (!cached.isEmpty()) {
                callback.onData(cached, true);
            }
            try {
                Response<ApiResponse<List<TripModel>>> response =
                        api.activeTrips(routeId, boardingStopId, destinationStopId).execute();
                ApiResponse<List<TripModel>> body = response.body();
                if (response.isSuccessful() && body != null && body.success && body.data != null) {
                    cacheTrips(body.data);
                    callback.onData(body.data, false);
                } else if (cached.isEmpty() && errorCallback != null) {
                    errorCallback.onError(new IOException("Không thể tải chuyến đang hoạt động."));
                }
            } catch (IOException exception) {
                if (cached.isEmpty() && errorCallback != null) {
                    errorCallback.onError(exception);
                }
            }
        });
    }

    void syncBootstrap() {
        executor.execute(() -> {
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

    private void cacheBootstrap(BootstrapModel data) {
        if (data == null) {
            return;
        }
        cacheRoutes(data.routes);
        cacheStops(data.stops);
        cacheTrips(data.activeTrips);
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
}
