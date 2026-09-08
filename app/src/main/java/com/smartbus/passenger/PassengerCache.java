package com.smartbus.passenger;

import android.content.Context;
import androidx.room.Dao;
import androidx.room.Database;
import androidx.room.Entity;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity(tableName = "routes", primaryKeys = {"id"})
class CachedRouteEntity {
    long id;
    String code;
    String name;
    String description;
}

@Entity(tableName = "stops", primaryKeys = {"id"})
class CachedStopEntity {
    long id;
    long routeId;
    String name;
    double latitude;
    double longitude;
    int stopOrder;
}

@Entity(tableName = "trips", primaryKeys = {"id"})
class CachedTripEntity {
    long id;
    long driverId;
    long routeId;
    Long currentStopId;
    String status;
    String startedAt;
    double currentLatitude;
    double currentLongitude;
}

@Entity(tableName = "selected_trip", primaryKeys = {"id"})
class CachedSelectedTripEntity {
    int id;
    Long tripId;
    Long routeId;
    String tripStatus;
    Long currentStopId;
    double currentLatitude;
    double currentLongitude;
    Long boardingStopId;
    String boardingStopName;
    double boardingLatitude;
    double boardingLongitude;
    Integer boardingStopOrder;
    Long destinationStopId;
    String destinationStopName;
    double destinationLatitude;
    double destinationLongitude;
    Integer destinationStopOrder;
    Long boardingRequestId;
    String boardingRequestStatus;
    String bluetoothIdentifier;
}

@Dao
interface PassengerRouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertRoutes(List<CachedRouteEntity> routes);

    @Query("SELECT * FROM routes ORDER BY code ASC")
    List<CachedRouteEntity> routes();

    @Query("SELECT * FROM routes WHERE id = :id LIMIT 1")
    CachedRouteEntity route(long id);
}

@Dao
interface PassengerStopDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertStops(List<CachedStopEntity> stops);

    @Query("SELECT * FROM stops WHERE routeId = :routeId ORDER BY stopOrder ASC")
    List<CachedStopEntity> stopsByRoute(long routeId);

    @Query("SELECT * FROM stops ORDER BY routeId ASC, stopOrder ASC")
    List<CachedStopEntity> allStops();

    @Query("DELETE FROM stops WHERE routeId = :routeId")
    void deleteByRoute(long routeId);
}

@Dao
interface PassengerTripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertTrips(List<CachedTripEntity> trips);

    @Query("SELECT * FROM trips WHERE status = :status ORDER BY startedAt DESC")
    List<CachedTripEntity> tripsByStatus(String status);

    @Query("SELECT * FROM trips WHERE routeId = :routeId AND status = :status ORDER BY startedAt DESC")
    List<CachedTripEntity> tripsByRouteAndStatus(long routeId, String status);

    @Query("SELECT * FROM trips WHERE routeId = :routeId ORDER BY startedAt DESC")
    List<CachedTripEntity> tripsByRoute(long routeId);

    @Query("SELECT * FROM trips ORDER BY startedAt DESC")
    List<CachedTripEntity> allTrips();
}

@Dao
interface PassengerSelectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(CachedSelectedTripEntity selection);

    @Query("SELECT * FROM selected_trip WHERE id = 1 LIMIT 1")
    CachedSelectedTripEntity selected();

    @Query("DELETE FROM selected_trip")
    void clear();
}

@Database(
        entities = {
                CachedRouteEntity.class,
                CachedStopEntity.class,
                CachedTripEntity.class,
                CachedSelectedTripEntity.class
        },
        version = 2,
        exportSchema = false
)
abstract class PassengerDatabase extends RoomDatabase {
    private static volatile PassengerDatabase instance;

    abstract PassengerRouteDao routeDao();

    abstract PassengerStopDao stopDao();

    abstract PassengerTripDao tripDao();

    abstract PassengerSelectionDao selectionDao();

    static PassengerDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (PassengerDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    PassengerDatabase.class,
                                    "smartbus_passenger.db"
                            )
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }
}

final class PassengerCacheMapper {
    private PassengerCacheMapper() {
    }

    static CachedRouteEntity toEntity(RouteModel model) {
        if (model == null || model.id == null) {
            return null;
        }
        CachedRouteEntity entity = new CachedRouteEntity();
        entity.id = model.id;
        entity.code = model.code;
        entity.name = model.name;
        entity.description = model.description;
        return entity;
    }

    static CachedStopEntity toEntity(StopModel model) {
        if (model == null || model.id == null) {
            return null;
        }
        CachedStopEntity entity = new CachedStopEntity();
        entity.id = model.id;
        entity.routeId = model.routeId == null ? 0L : model.routeId;
        entity.name = model.name;
        entity.latitude = model.latitude == null ? 0d : model.latitude.doubleValue();
        entity.longitude = model.longitude == null ? 0d : model.longitude.doubleValue();
        entity.stopOrder = model.stopOrder == null ? 0 : model.stopOrder;
        return entity;
    }

    static CachedTripEntity toEntity(TripModel model) {
        if (model == null || model.id == null) {
            return null;
        }
        CachedTripEntity entity = new CachedTripEntity();
        entity.id = model.id;
        entity.driverId = model.driverId == null ? 0L : model.driverId;
        entity.routeId = model.routeId == null ? 0L : model.routeId;
        entity.currentStopId = model.currentStopId;
        entity.status = model.status;
        entity.startedAt = model.startedAt;
        entity.currentLatitude = model.currentLatitude == null ? 0d : model.currentLatitude.doubleValue();
        entity.currentLongitude = model.currentLongitude == null ? 0d : model.currentLongitude.doubleValue();
        return entity;
    }

    static RouteModel toModel(CachedRouteEntity entity) {
        if (entity == null) {
            return null;
        }
        RouteModel model = new RouteModel();
        model.id = entity.id;
        model.code = entity.code;
        model.name = entity.name;
        model.description = entity.description;
        return model;
    }

    static StopModel toModel(CachedStopEntity entity) {
        if (entity == null) {
            return null;
        }
        StopModel model = new StopModel();
        model.id = entity.id;
        model.routeId = entity.routeId;
        model.name = entity.name;
        model.latitude = BigDecimal.valueOf(entity.latitude);
        model.longitude = BigDecimal.valueOf(entity.longitude);
        model.stopOrder = entity.stopOrder;
        return model;
    }

    static TripModel toModel(CachedTripEntity entity) {
        if (entity == null) {
            return null;
        }
        TripModel model = new TripModel();
        model.id = entity.id;
        model.driverId = entity.driverId;
        model.routeId = entity.routeId;
        model.currentStopId = entity.currentStopId;
        model.status = entity.status;
        model.startedAt = entity.startedAt;
        model.currentLatitude = BigDecimal.valueOf(entity.currentLatitude);
        model.currentLongitude = BigDecimal.valueOf(entity.currentLongitude);
        return model;
    }

    static List<RouteModel> routes(List<CachedRouteEntity> entities) {
        List<RouteModel> models = new ArrayList<>();
        if (entities != null) {
            for (CachedRouteEntity entity : entities) {
                RouteModel model = toModel(entity);
                if (model != null) {
                    models.add(model);
                }
            }
        }
        return models;
    }

    static List<StopModel> stops(List<CachedStopEntity> entities) {
        List<StopModel> models = new ArrayList<>();
        if (entities != null) {
            for (CachedStopEntity entity : entities) {
                StopModel model = toModel(entity);
                if (model != null) {
                    models.add(model);
                }
            }
        }
        return models;
    }

    static List<TripModel> trips(List<CachedTripEntity> entities) {
        List<TripModel> models = new ArrayList<>();
        if (entities != null) {
            for (CachedTripEntity entity : entities) {
                TripModel model = toModel(entity);
                if (model != null) {
                    models.add(model);
                }
            }
        }
        return models;
    }
}
