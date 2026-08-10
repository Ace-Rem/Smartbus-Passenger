package com.smartbus.passenger;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SmartBusApi {

    @POST("passengers/register")
    Call<ApiResponse<PassengerLoginResponse>> register(@Body PassengerRegisterRequest request);

    @POST("passengers/login")
    Call<ApiResponse<PassengerLoginResponse>> login(@Body PassengerLoginRequest request);

    @GET("passengers/me")
    Call<ApiResponse<PassengerModel>> me();

    @GET("routes")
    Call<ApiResponse<List<RouteModel>>> routes();

    @GET("sync/bootstrap")
    Call<ApiResponse<BootstrapModel>> bootstrap();

    @GET("routes/{routeId}/stops")
    Call<ApiResponse<List<StopModel>>> stops(@Path("routeId") long routeId);

    @GET("routes/path")
    Call<ApiResponse<RoutePathModel>> routePath(
            @Query("fromLatitude") BigDecimal fromLatitude,
            @Query("fromLongitude") BigDecimal fromLongitude,
            @Query("toLatitude") BigDecimal toLatitude,
            @Query("toLongitude") BigDecimal toLongitude
    );

    @GET("boarding-requests/active-trips")
    Call<ApiResponse<List<TripModel>>> activeTrips(
            @Query("routeId") long routeId,
            @Query("boardingStopId") long boardingStopId,
            @Query("destinationStopId") long destinationStopId
    );

    @GET("boarding-requests/nearby-active-trips")
    Call<ApiResponse<NearbyActiveTripsModel>> nearbyActiveTrips(
            @Query("latitude") double latitude,
            @Query("longitude") double longitude
    );

    @POST("boarding-requests")
    Call<ApiResponse<BoardingRequestModel>> createBoardingRequest(@Body CreateBoardingRequest request);

    @GET("boarding-requests/mine")
    Call<ApiResponse<List<BoardingRequestModel>>> myRequests();

    @POST("boarding-requests/{id}/cancel")
    Call<ApiResponse<BoardingRequestModel>> cancel(@Path("id") long id);

    @GET("boarding-requests/{id}/tracking")
    Call<ApiResponse<PassengerTripTrackingModel>> tracking(@Path("id") long id);

    @POST("ai/chat")
    Call<ApiResponse<AiAssistantResponse>> aiChat(@Body AiAssistantRequest request);

    @POST("ai/summary")
    Call<ApiResponse<AiAssistantResponse>> aiSummary(@Body AiSummaryRequest request);
}

final class RetrofitClient {

    private static SmartBusApi api;
    private static String token;

    private RetrofitClient() {
    }

    static void setToken(String accessToken) {
        token = accessToken;
    }

    static void clearToken() {
        token = null;
    }

    static SmartBusApi api() {
        if (api == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(BuildConfig.DEBUG
                    ? HttpLoggingInterceptor.Level.BASIC
                    : HttpLoggingInterceptor.Level.NONE);
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(45, TimeUnit.SECONDS)
                    .callTimeout(60, TimeUnit.SECONDS)
                    .addInterceptor(chain -> {
                        okhttp3.Request.Builder request = chain.request().newBuilder();
                        if (token != null && !token.isBlank()) {
                            request.addHeader("Authorization", "Bearer " + token);
                        }
                        return chain.proceed(request.build());
                    })
                    .addInterceptor(logging)
                    .build();
            api = new Retrofit.Builder()
                    .baseUrl(BuildConfig.BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(SmartBusApi.class);
        }
        return api;
    }
}

class ApiResponse<T> {
    boolean success;
    String message;
    T data;
}

class PassengerRegisterRequest {
    final String fullName;
    final String phoneNumber;
    final String username;
    final String password;

    PassengerRegisterRequest(String fullName, String phoneNumber, String username, String password) {
        this.fullName = fullName;
        this.phoneNumber = phoneNumber;
        this.username = username;
        this.password = password;
    }
}

class PassengerLoginRequest {
    final String username;
    final String password;

    PassengerLoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}

class CreateBoardingRequest {
    final Long tripId;
    final Long boardingStopId;
    final Long destinationStopId;
    final String note;

    CreateBoardingRequest(Long tripId, Long boardingStopId, Long destinationStopId, String note) {
        this.tripId = tripId;
        this.boardingStopId = boardingStopId;
        this.destinationStopId = destinationStopId;
        this.note = note;
    }
}

class PassengerLoginResponse {
    String accessToken;
    String tokenType;
    Long expiresInMinutes;
    PassengerModel passenger;
}

class PassengerModel {
    Long id;
    String fullName;
    String phoneNumber;
    String username;
}

class RouteModel {
    Long id;
    String code;
    String name;
    String description;

    @Override
    public String toString() {
        return code + " · " + name;
    }
}

class StopModel {
    Long id;
    Long routeId;
    String name;
    BigDecimal latitude;
    BigDecimal longitude;
    Integer stopOrder;

    @Override
    public String toString() {
        if (id == null) {
            return name == null || name.isBlank() ? "Chưa chọn bến" : name;
        }
        if (stopOrder == null) {
            return name == null ? "Bến chưa đặt tên" : name;
        }
        return stopOrder + ". " + name;
    }
}

class TripModel {
    Long id;
    Long driverId;
    Long routeId;
    Long currentStopId;
    String status;
    String startedAt;
    BigDecimal currentLatitude;
    BigDecimal currentLongitude;
}

class BoardingRequestModel {
    Long id;
    PassengerModel passenger;
    TripModel trip;
    StopModel boardingStop;
    StopModel destinationStop;
    Long passengerRecordId;
    String status;
    String note;
    String bluetoothIdentifier;
}

class PassengerTripTrackingModel {
    BoardingRequestModel boardingRequest;
    RouteModel route;
    StopModel currentStop;
    StopModel nextStop;
    Integer remainingStops;
    Integer etaMinutes;
    Integer progressPercent;
    String notification;
}

class NearbyActiveTripsModel {
    StopModel boardingStop;
    StopModel suggestedDestinationStop;
    RouteModel route;
    Double distanceMeters;
    List<TripModel> trips;
}

class BootstrapModel {
    String serverTime;
    List<RouteModel> routes;
    List<StopModel> stops;
    List<TripModel> activeTrips;
}

class RoutePathModel {
    String provider;
    List<RoutePathPointModel> points;
}

class RoutePathPointModel {
    BigDecimal latitude;
    BigDecimal longitude;
}

class AiAssistantRequest {
    final Long tripId;
    final String question;
    final Map<String, Object> clientContext;

    AiAssistantRequest(Long tripId, String question, Map<String, Object> clientContext) {
        this.tripId = tripId;
        this.question = question;
        this.clientContext = clientContext;
    }
}

class AiSummaryRequest {
    final Long tripId;
    final Map<String, Object> clientContext;

    AiSummaryRequest(Long tripId, Map<String, Object> clientContext) {
        this.tripId = tripId;
        this.clientContext = clientContext;
    }
}

class AiAssistantResponse {
    String answer;
}
