package com.smartbus.passenger;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.smartbus.passenger.bluetooth.BluetoothConnectionState;
import com.smartbus.passenger.bluetooth.BluetoothEvent;
import com.smartbus.passenger.bluetooth.PassengerBluetoothManager;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "smartbus_passenger_session";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_PASSENGER_ID = "passenger_id";
    private static final String KEY_PASSENGER_NAME = "passenger_name";
    private static final String KEY_PASSENGER_PHONE = "passenger_phone";
    private static final String KEY_PASSENGER_USERNAME = "passenger_username";
    private static final String KEY_LOCAL_CHECKIN_COUNT = "local_checkin_count";
    private static final String KEY_LOCAL_CHECKIN_PREFIX = "local_checkin_";
    private static final int MAX_LOCAL_CHECKINS = 5;
    private static final long TRACKING_REFRESH_MS = 12_000L;
    private static final double AUTO_BOARDING_STOP_RADIUS_METERS = 50d;
    private static final int REQUEST_BLUETOOTH_CONNECT = 4101;
    private static final int REQUEST_LOCATION = 4102;
    private static final int TAB_HOME = 0;
    private static final int TAB_MAP = 1;
    private static final int TAB_ROUTES = 2;
    private static final int TAB_MY_TRIP = 3;
    private static final int TAB_PROFILE = 4;

    private final List<RouteModel> routes = new ArrayList<>();
    private final List<StopModel> stops = new ArrayList<>();
    private final List<StopModel> nearbyBoardingStops = new ArrayList<>();
    private final List<StopModel> nearbyRouteStops = new ArrayList<>();
    private final List<StopModel> nearbyDestinationStops = new ArrayList<>();
    private final List<BoardingRequestModel> localCheckIns = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable trackingRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            BoardingRequestModel request = currentBoardingRequest();
            if (!destroyed && request != null && request.id != null) {
                track(request, false);
                handler.postDelayed(this, TRACKING_REFRESH_MS);
            }
        }
    };
    private RouteModel selectedRoute;
    private BoardingRequestModel selectedRequest;
    private SelectedTripStore selectedTripStore;
    private PassengerSelectionState selectionState;
    private PassengerDataRepository dataRepository;
    private final ExecutorService dataExecutor = Executors.newSingleThreadExecutor();
    private SharedPreferences preferences;
    private PassengerBluetoothManager bluetoothManager;
    private PassengerModel currentPassenger;
    private Location currentLocation;
    private StopModel nearbyBoardingStop;
    private StopModel nearbySuggestedBoardingStop;
    private Double nearbySuggestedDistanceMeters;
    private boolean nearbyAutoBoardingAllowed;
    private boolean registerMode;
    private int currentTab = TAB_HOME;
    private boolean destroyed;
    private boolean pendingMapFit;
    private int pendingMapFitAttempts;
    private boolean syncingBottomNavigation;
    private boolean syncingSelectionControls;
    private boolean syncingRouteControls;
    private String selectedRoutePathKey;
    private String selectedRoutePathInFlightKey;
    private final List<GeoPoint> selectedRoutePathPoints = new ArrayList<>();

    private View rootShell;
    private View startupLoading;
    private View authLogoImage;
    private TextView titleText;
    private TextInputEditText fullNameInput;
    private TextInputEditText phoneInput;
    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private TextInputLayout usernameLayout;
    private View authCard;
    private View fullNameLayout;
    private View phoneLayout;
    private View confirmPasswordLayout;
    private View authProgress;
    private LinearLayout appContent;
    private View homeTab;
    private View mapTab;
    private View routesTab;
    private View myTripTab;
    private View profileTab;
    private BottomNavigationView bottomNavigation;
    private TextView subtitleText;
    private TextView profileText;
    private TextView homeGreetingText;
    private TextView homeSummaryText;
    private TextView routesStateText;
    private TextView routeDetailText;
    private TextView mapStatusText;
    private TextInputEditText routeSearchInput;
    private LinearLayout routesContainer;
    private LinearLayout stopsContainer;
    private LinearLayout tripsContainer;
    private LinearLayout requestsContainer;
    private LinearLayout nearbyTripsContainer;
    private Spinner boardingStopSpinner;
    private Spinner destinationStopSpinner;
    private Spinner nearbyBoardingStopSpinner;
    private Spinner nearbyDestinationStopSpinner;
    private TextView nearbyBoardingStopText;
    private TextView trackingText;
    private TextView bluetoothStatusText;
    private ProgressBar tripProgress;
    private MaterialButton loginButton;
    private MaterialButton registerButton;
    private MaterialButton checkInButton;
    private MaterialButton nearbyPickBoardingStopButton;
    private MaterialButton nearbyPickDestinationStopButton;
    private MaterialButton nearbyConfirmCheckInButton;
    private MaterialButton homeFindRouteButton;
    private MaterialButton homeMyTripButton;
    private MaterialButton recenterMapButton;
    private TextInputEditText aiQuestionInput;
    private LinearLayout aiMessagesContainer;
    private View aiProgress;
    private MaterialButton aiSendButton;
    private MaterialButton aiSummaryButton;
    private MaterialButton aiWhereBusButton;
    private MaterialButton aiStopsButton;
    private MapView mapView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME);
        setContentView(R.layout.activity_main);
        bindViews();
        applySystemInsets();
        setupActions();
        setupMap();
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedTripStore = new SelectedTripStore(preferences);
        selectionState = selectedTripStore.state();
        loadLocalCheckIns();
        dataRepository = new PassengerDataRepository(
                PassengerDatabase.getInstance(this),
                preferences,
                dataExecutor
        );
        bluetoothManager = new PassengerBluetoothManager(this);
        setAuthMode(false);
        addAiAssistantMessage("Tôi có thể trả lời dựa trên chuyến bạn đã đăng ký: vị trí xe, bến tiếp theo, bến xuống và tóm tắt hành trình.");
        updateBluetoothStatus();
        restoreSession();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        startTrackingRefresh();
    }

    @Override
    protected void onPause() {
        stopTrackingRefresh();
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        stopTrackingRefresh();
        if (bluetoothManager != null) {
            bluetoothManager.stopAdvertising();
        }
        if (mapView != null) {
            mapView.onDetach();
        }
        dataExecutor.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        rootShell = findViewById(R.id.rootShell);
        startupLoading = findViewById(R.id.startupLoading);
        authLogoImage = findViewById(R.id.authLogoImage);
        titleText = findViewById(R.id.titleText);
        fullNameInput = findViewById(R.id.fullNameInput);
        phoneInput = findViewById(R.id.phoneInput);
        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        usernameLayout = findViewById(R.id.usernameLayout);
        fullNameLayout = findViewById(R.id.fullNameLayout);
        phoneLayout = findViewById(R.id.phoneLayout);
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout);
        authProgress = findViewById(R.id.authProgress);
        authCard = findViewById(R.id.authCard);
        appContent = findViewById(R.id.appContent);
        homeTab = findViewById(R.id.homeTab);
        mapTab = findViewById(R.id.mapTab);
        routesTab = findViewById(R.id.routesTab);
        myTripTab = findViewById(R.id.myTripTab);
        profileTab = findViewById(R.id.profileTab);
        bottomNavigation = findViewById(R.id.passengerBottomNavigation);
        subtitleText = findViewById(R.id.subtitleText);
        profileText = findViewById(R.id.profileText);
        homeGreetingText = findViewById(R.id.homeGreetingText);
        homeSummaryText = findViewById(R.id.homeSummaryText);
        routesStateText = findViewById(R.id.routesStateText);
        routeDetailText = findViewById(R.id.routeDetailText);
        mapStatusText = findViewById(R.id.mapStatusText);
        routeSearchInput = findViewById(R.id.routeSearchInput);
        routesContainer = findViewById(R.id.routesContainer);
        stopsContainer = findViewById(R.id.stopsContainer);
        tripsContainer = findViewById(R.id.tripsContainer);
        requestsContainer = findViewById(R.id.requestsContainer);
        nearbyTripsContainer = findViewById(R.id.nearbyTripsContainer);
        boardingStopSpinner = findViewById(R.id.boardingStopSpinner);
        destinationStopSpinner = findViewById(R.id.destinationStopSpinner);
        nearbyBoardingStopSpinner = findViewById(R.id.nearbyBoardingStopSpinner);
        nearbyDestinationStopSpinner = findViewById(R.id.nearbyDestinationStopSpinner);
        nearbyBoardingStopText = findViewById(R.id.nearbyBoardingStopText);
        trackingText = findViewById(R.id.trackingText);
        bluetoothStatusText = findViewById(R.id.bluetoothStatusText);
        tripProgress = findViewById(R.id.tripProgress);
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registerButton);
        checkInButton = findViewById(R.id.checkInButton);
        nearbyPickBoardingStopButton = findViewById(R.id.nearbyPickBoardingStopButton);
        nearbyPickDestinationStopButton = findViewById(R.id.nearbyPickDestinationStopButton);
        nearbyConfirmCheckInButton = findViewById(R.id.nearbyConfirmCheckInButton);
        homeFindRouteButton = findViewById(R.id.homeFindRouteButton);
        homeMyTripButton = findViewById(R.id.homeMyTripButton);
        recenterMapButton = findViewById(R.id.recenterMapButton);
        aiQuestionInput = findViewById(R.id.aiQuestionInput);
        aiMessagesContainer = findViewById(R.id.aiMessagesContainer);
        aiProgress = findViewById(R.id.aiProgress);
        aiSendButton = findViewById(R.id.aiSendButton);
        aiSummaryButton = findViewById(R.id.aiSummaryButton);
        aiWhereBusButton = findViewById(R.id.aiWhereBusButton);
        aiStopsButton = findViewById(R.id.aiStopsButton);
        mapView = findViewById(R.id.mapView);
    }

    private void applySystemInsets() {
        int start = rootShell.getPaddingStart();
        int top = rootShell.getPaddingTop();
        int end = rootShell.getPaddingEnd();
        ViewCompat.setOnApplyWindowInsetsListener(rootShell, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    start + systemBars.left,
                    top + systemBars.top,
                    end + systemBars.right,
                    0
            );
            ViewGroup.LayoutParams params = bottomNavigation.getLayoutParams();
            int navHeight = getResources().getDimensionPixelSize(R.dimen.passenger_bottom_nav_height);
            params.height = navHeight + systemBars.bottom;
            bottomNavigation.setLayoutParams(params);
            bottomNavigation.setPadding(0, 4, 0, systemBars.bottom + 8);
            bottomNavigation.setMinimumHeight(params.height);
            return insets;
        });
    }

    private void setupActions() {
        loginButton.setOnClickListener(v -> {
            if (registerMode) {
                register();
            } else {
                login();
            }
        });
        registerButton.setOnClickListener(v -> setAuthMode(!registerMode));
        findViewById(R.id.logoutButton).setOnClickListener(v -> logout());
        findViewById(R.id.findTripsButton).setOnClickListener(v -> findTrips());
        checkInButton.setOnClickListener(v -> handleCheckInButtonClick());
        nearbyConfirmCheckInButton.setOnClickListener(v -> confirmNearbyCheckInSelection());
        homeFindRouteButton.setOnClickListener(v -> showTab(TAB_ROUTES));
        homeMyTripButton.setOnClickListener(v -> showTab(TAB_MY_TRIP));
        recenterMapButton.setOnClickListener(v -> centerMapOnRoute());
        aiSendButton.setOnClickListener(v -> sendAiQuestion(text(aiQuestionInput)));
        aiSummaryButton.setOnClickListener(v -> requestAiSummary());
        aiWhereBusButton.setOnClickListener(v -> sendAiQuestion("Xe của tôi đang ở đâu?"));
        aiStopsButton.setOnClickListener(v -> sendAiQuestion("Tôi còn bao nhiêu bến nữa tới điểm xuống?"));
        nearbyPickBoardingStopButton.setOnClickListener(v -> showNearbyStopPicker(true));
        nearbyPickDestinationStopButton.setOnClickListener(v -> showNearbyStopPicker(false));
        nearbyBoardingStopSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingSelectionControls || syncingRouteControls) {
                    return;
                }
                StopModel selected = selectedStop(nearbyBoardingStopSpinner);
                nearbyBoardingStop = selected != null && selected.id != null ? selected : null;
                if (selectionState != null) {
                    selectionState.setBoardingStop(nearbyBoardingStop);
                }
                onSelectionStateChanged(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (syncingSelectionControls || syncingRouteControls) {
                    return;
                }
                nearbyBoardingStop = null;
                if (selectionState != null) {
                    selectionState.setBoardingStop(null);
                }
                onSelectionStateChanged(true);
            }
        });
        nearbyDestinationStopSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (syncingSelectionControls || syncingRouteControls) {
                    return;
                }
                StopModel selected = selectedStop(nearbyDestinationStopSpinner);
                if (selectionState != null) {
                    selectionState.setDestinationStop(selected);
                }
                onSelectionStateChanged(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (syncingSelectionControls || syncingRouteControls) {
                    return;
                }
                if (selectionState != null) {
                    selectionState.setDestinationStop(null);
                }
                onSelectionStateChanged(true);
            }
        });
        bottomNavigation.setOnItemSelectedListener(item -> {
            if (syncingBottomNavigation) {
                return true;
            }
            int id = item.getItemId();
            if (id == R.id.tab_home) {
                showTab(TAB_HOME);
                return true;
            }
            if (id == R.id.tab_map) {
                showTab(TAB_MAP);
                return true;
            }
            if (id == R.id.tab_routes) {
                showTab(TAB_ROUTES);
                return true;
            }
            if (id == R.id.tab_my_trip) {
                showTab(TAB_MY_TRIP);
                return true;
            }
            if (id == R.id.tab_profile) {
                showTab(TAB_PROFILE);
                return true;
            }
            return false;
        });
        routeSearchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                renderRoutes(text == null ? "" : text.toString());
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });
    }

    private void restoreSession() {
        showStartupLoading();
        String savedToken = preferences.getString(KEY_TOKEN, null);
        if (savedToken == null || savedToken.isBlank()) {
            showAuth();
            return;
        }
        RetrofitClient.setToken(savedToken);
        PassengerModel cachedPassenger = cachedPassenger();
        if (cachedPassenger != null) {
            showApp();
            renderProfile(cachedPassenger);
            restoreSelectedTripState();
            loadRoutes();
            loadRequests();
        }
        RetrofitClient.api().me().enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<ApiResponse<PassengerModel>> call, Response<ApiResponse<PassengerModel>> response) {
                if (destroyed) {
                    return;
                }
                ApiResponse<PassengerModel> body = response.body();
                if (response.isSuccessful() && body != null && body.success && body.data != null) {
                    savePassenger(body.data);
                    if (appContent.getVisibility() != View.VISIBLE) {
                        showApp();
                        restoreSelectedTripState();
                        loadRoutes();
                        loadRequests();
                    }
                    renderProfile(body.data);
                    return;
                }
                if (response.code() == 401) {
                    toast("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
                    clearExpiredSession();
                    return;
                }
                if (cachedPassenger == null) {
                    showAuth();
                    toast("Chưa xác minh được phiên đăng nhập. Vui lòng đăng nhập lại.");
                } else {
                    toast("Chưa xác minh lại được phiên. SmartBus sẽ tiếp tục thử khi bạn thao tác.");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<PassengerModel>> call, Throwable throwable) {
                if (destroyed) {
                    return;
                }
                if (cachedPassenger == null) {
                    showAuth();
                    toast(networkErrorMessage(throwable));
                } else {
                    toast(networkErrorMessage(throwable));
                }
            }
        });
    }

    private void showStartupLoading() {
        rootShell.setBackgroundResource(R.drawable.bg_passenger_auth);
        authLogoImage.setVisibility(View.GONE);
        titleText.setVisibility(View.GONE);
        subtitleText.setVisibility(View.GONE);
        startupLoading.setVisibility(View.VISIBLE);
        authCard.setVisibility(View.GONE);
        appContent.setVisibility(View.GONE);
    }

    private void setupMap() {
        XYTileSource tileSource = new XYTileSource(
                "OpenStreetMap",
                1,
                19,
                256,
                ".png",
                new String[]{
                        "https://a.tile.openstreetmap.org/",
                        "https://b.tile.openstreetmap.org/",
                        "https://c.tile.openstreetmap.org/"
                },
                "© OpenStreetMap contributors"
        );
        mapView.setTileSource(tileSource);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(13.0);
        mapView.getController().setCenter(new GeoPoint(10.7769, 106.7009));
    }

    private void register() {
        String fullName = text(fullNameInput);
        String phone = text(phoneInput);
        String password = text(passwordInput);
        String confirmPassword = text(confirmPasswordInput);
        if (fullName.isBlank() || phone.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
            toast("Vui lòng nhập đủ thông tin đăng ký");
            return;
        }
        if (!password.equals(confirmPassword)) {
            toast("Mật khẩu xác nhận chưa khớp");
            return;
        }
        if (password.length() < 6) {
            toast("Mật khẩu nên có ít nhất 6 ký tự");
            return;
        }
        setAuthLoading(true);
        callPublic(RetrofitClient.api().register(new PassengerRegisterRequest(fullName, phone, phone, password)),
                data -> {
                    setAuthLoading(false);
                    toast("Đăng ký thành công. SmartBus đã đăng nhập cho bạn.");
                    onAuthenticated(data);
                },
                () -> setAuthLoading(false));
    }

    private void login() {
        String username = text(usernameInput);
        String password = text(passwordInput);
        if (username.isBlank() || password.isBlank()) {
            toast("Vui lòng nhập số điện thoại hoặc tên đăng nhập và mật khẩu");
            return;
        }
        setAuthLoading(true);
        callPublic(RetrofitClient.api().login(new PassengerLoginRequest(username, password)),
                data -> {
                    setAuthLoading(false);
                    onAuthenticated(data);
                },
                () -> setAuthLoading(false));
    }

    private void onAuthenticated(PassengerLoginResponse response) {
        if (response == null || response.accessToken == null || response.accessToken.isBlank()) {
            toast("Backend chưa trả token đăng nhập. Vui lòng thử lại.");
            return;
        }
        RetrofitClient.setToken(response.accessToken);
        SharedPreferences.Editor editor = preferences.edit().putString(KEY_TOKEN, response.accessToken);
        if (response.passenger != null) {
            putPassenger(editor, response.passenger);
        }
        editor.apply();
        showApp();
        if (response.passenger != null) {
            renderProfile(response.passenger);
        }
        restoreSelectedTripState();
        loadRoutes();
        loadRequests();
    }

    private void restoreSelectedTripState() {
        if (selectedTripStore == null) {
            return;
        }
        selectionState = selectedTripStore.state();
        if (selectionState == null
                || (!selectionState.hasTrip() && selectionState.selectedRouteId() == null)) {
            return;
        }
        selectedRequest = selectionState.boardingRequest();
        if (selectionState.hasTrip()) {
            syncSelectedRouteFromTrip(selectionState.selectedTrip());
        } else {
            selectedRoute = selectionState.selectedRoute();
            if (selectedRoute != null && selectedRoute.id != null) {
                loadStopsForSelectedTripRoute(selectedRoute.id);
            }
        }
        onSelectionStateChanged(false);
        if (selectionState.hasTrip()) {
            startTrackingRefresh();
        }
    }

    private void logout() {
        if (bluetoothManager != null) {
            bluetoothManager.stopAdvertising();
        }
        RetrofitClient.clearToken();
        preferences.edit().clear().apply();
        currentPassenger = null;
        showAuth();
        routes.clear();
        stops.clear();
        selectedRoute = null;
        selectedRequest = null;
        if (selectedTripStore != null) {
            selectedTripStore.clear();
        }
        if (dataRepository != null) {
            dataRepository.clearSelection();
        }
        if (selectionState != null) {
            selectionState.clear();
        }
        routesContainer.removeAllViews();
        stopsContainer.removeAllViews();
        tripsContainer.removeAllViews();
        requestsContainer.removeAllViews();
        nearbyTripsContainer.removeAllViews();
        trackingText.setText(getString(R.string.empty_default));
        bluetoothStatusText.setText(getString(R.string.empty_default));
        homeSummaryText.setText(getString(R.string.empty_default));
        routeDetailText.setText(R.string.route_detail_empty);
        mapStatusText.setText(R.string.map_empty);
        tripProgress.setProgress(0);
        setAiLoading(false);
        aiQuestionInput.setText("");
        mapView.getOverlays().clear();
        setupMap();
    }

    private void clearExpiredSession() {
        if (bluetoothManager != null) {
            bluetoothManager.stopAdvertising();
        }
        RetrofitClient.clearToken();
        preferences.edit().clear().apply();
        currentPassenger = null;
        selectedRequest = null;
        if (selectedTripStore != null) {
            selectedTripStore.clear();
        }
        if (dataRepository != null) {
            dataRepository.clearSelection();
        }
        if (selectionState != null) {
            selectionState.clear();
        }
        selectedRoute = null;
        routes.clear();
        stops.clear();
        showAuth();
    }

    private void showApp() {
        rootShell.setBackgroundColor(getColor(R.color.smartbus_background));
        authLogoImage.setVisibility(View.GONE);
        titleText.setVisibility(View.VISIBLE);
        subtitleText.setVisibility(View.VISIBLE);
        titleText.setGravity(android.view.Gravity.START);
        startupLoading.setVisibility(View.GONE);
        authCard.setVisibility(View.GONE);
        appContent.setVisibility(View.VISIBLE);
        subtitleText.setText("Chọn tuyến, gửi yêu cầu lên xe và theo dõi bến xuống theo thời gian thực.");
        showTab(currentTab);
    }

    private void showAuth() {
        rootShell.setBackgroundResource(R.drawable.bg_passenger_auth);
        authLogoImage.setVisibility(View.VISIBLE);
        titleText.setVisibility(View.VISIBLE);
        subtitleText.setVisibility(View.VISIBLE);
        titleText.setGravity(android.view.Gravity.CENTER);
        startupLoading.setVisibility(View.GONE);
        appContent.setVisibility(View.GONE);
        authCard.setVisibility(View.VISIBLE);
        subtitleText.setText(R.string.login_subtitle);
        setAuthMode(false);
    }

    private void setAuthMode(boolean register) {
        registerMode = register;
        fullNameLayout.setVisibility(register ? View.VISIBLE : View.GONE);
        phoneLayout.setVisibility(register ? View.VISIBLE : View.GONE);
        confirmPasswordLayout.setVisibility(register ? View.VISIBLE : View.GONE);
        usernameLayout.setHint(register ? getString(R.string.login_identifier_hint) : getString(R.string.login_identifier_hint));
        usernameLayout.setVisibility(register ? View.GONE : View.VISIBLE);
        loginButton.setText(register ? R.string.register_action : R.string.login_action);
        registerButton.setText(register ? R.string.back_to_login_action : R.string.create_account_action);
        subtitleText.setText(register
                ? "Tạo tài khoản hành khách bằng họ tên, số điện thoại và mật khẩu."
                : getString(R.string.login_subtitle));
    }

    private void showTab(int tab) {
        currentTab = tab;
        homeTab.setVisibility(tab == TAB_HOME ? View.VISIBLE : View.GONE);
        mapTab.setVisibility(tab == TAB_MAP ? View.VISIBLE : View.GONE);
        routesTab.setVisibility(tab == TAB_ROUTES ? View.VISIBLE : View.GONE);
        myTripTab.setVisibility(tab == TAB_MY_TRIP ? View.VISIBLE : View.GONE);
        profileTab.setVisibility(tab == TAB_PROFILE ? View.VISIBLE : View.GONE);
        int selectedItem = R.id.tab_home;
        String title = getString(R.string.home_tab);
        if (tab == TAB_MAP) {
            selectedItem = R.id.tab_map;
            title = getString(R.string.map_tab);
            mapView.post(() -> {
                fitMapToStopsIfReady();
                mapView.invalidate();
            });
        } else if (tab == TAB_ROUTES) {
            selectedItem = R.id.tab_routes;
            title = getString(R.string.routes_tab);
        } else if (tab == TAB_MY_TRIP) {
            selectedItem = R.id.tab_my_trip;
            title = getString(R.string.my_trip_tab);
        } else if (tab == TAB_PROFILE) {
            selectedItem = R.id.tab_profile;
            title = getString(R.string.profile_tab);
        }
        if (bottomNavigation.getSelectedItemId() != selectedItem) {
            try {
                syncingBottomNavigation = true;
                bottomNavigation.setSelectedItemId(selectedItem);
            } finally {
                syncingBottomNavigation = false;
            }
        }
        subtitleText.setText(title);
        onSelectionStateChanged(tab == TAB_MAP);
    }

    private void setAuthLoading(boolean loading) {
        authProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        loginButton.setEnabled(!loading);
        registerButton.setEnabled(!loading);
    }

    private void renderProfile(PassengerModel passenger) {
        if (passenger == null) {
            return;
        }
        currentPassenger = passenger;
        String fullName = safe(passenger.fullName).isBlank() ? "hành khách SmartBus" : passenger.fullName;
        String phone = safe(passenger.phoneNumber).isBlank() ? "Chưa cập nhật" : passenger.phoneNumber;
        profileText.setText("Xin chào, " + fullName
                + "\nSố điện thoại: " + phone
                + "\nBackend: " + BuildConfig.BASE_URL);
        homeGreetingText.setText("Xin chào, " + fullName);
        updateHomeSummary();
    }

    private void loadRoutes() {
        routesStateText.setText(R.string.loading_default);
        dataRepository.loadRoutes((data, fromCache) -> runOnUiThread(() -> {
            routes.clear();
            for (RouteModel route : data) {
                if (route != null) {
                    routes.add(route);
                }
            }
            renderRoutes(text(routeSearchInput));
            syncSelectedRouteFromTrip(currentSelectedTrip());
            routesStateText.setText(routes.isEmpty()
                    ? "Chưa có tuyến nào trong bộ nhớ offline."
                    : (fromCache ? "Đang dùng dữ liệu offline, sẽ tự cập nhật khi có mạng." : "Đã đồng bộ tuyến từ backend."));
            updateHomeSummary();
        }), error -> runOnUiThread(() -> {
            renderRoutes(text(routeSearchInput));
            routesStateText.setText(routes.isEmpty()
                    ? networkErrorMessage(error)
                    : "Mất mạng, đang dùng dữ liệu tuyến đã lưu.");
        }));
    }

    private void renderRoutes(String query) {
        routesContainer.removeAllViews();
        String normalized = query == null ? "" : query.trim().toLowerCase();
        if (routes.isEmpty()) {
            routesStateText.setText("Backend chưa có tuyến đang hoạt động.");
            return;
        }
        int count = 0;
        for (RouteModel route : routes) {
            if (route == null) {
                continue;
            }
            String haystack = (safe(route.code) + " " + safe(route.name) + " " + safe(route.description)).toLowerCase();
            if (!normalized.isBlank() && !haystack.contains(normalized)) {
                continue;
            }
            count++;
            routesContainer.addView(routeCard(route));
        }
        routesStateText.setText(count == 0
                ? "Không tìm thấy tuyến xe phù hợp."
                : "Có " + count + " tuyến phù hợp.");
    }

    private View routeCard(RouteModel route) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(8);
        card.setCardElevation(2);
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(getColor(R.color.smartbus_surface));
        boolean routeSelected = route.id != null && selectedRoute != null && route.id.equals(selectedRoute.id);
        card.setStrokeColor(routeSelected ? getColor(R.color.smartbus_primary) : getColor(R.color.smartbus_background));
        card.setStrokeWidth(routeSelected ? 3 : 1);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(18, 16, 18, 16);
        card.addView(inner);
        TextView title = label(routeLabel(route), 17f, true, R.color.smartbus_text_primary);
        inner.addView(title);
        if (route != null && route.description != null && !route.description.isBlank()) {
            inner.addView(label(route.description, 14f, false, R.color.smartbus_text_secondary));
        }
        MaterialButton action = new MaterialButton(this);
        action.setText(routeSelected ? "Đang chọn tuyến này" : "Xem tuyến và chọn bến");
        action.setTextColor(routeSelected ? getColor(R.color.white) : getColor(R.color.smartbus_primary));
        action.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                routeSelected ? getColor(R.color.smartbus_primary) : getColor(R.color.smartbus_surface)));
        action.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_primary)));
        action.setStrokeWidth(2);
        action.setOnClickListener(v -> selectRoute(route));
        inner.addView(action);
        return card;
    }

    private void selectRoute(RouteModel route) {
        if (route == null || route.id == null) {
            toast("Tuyến này thiếu mã định danh từ backend.");
            return;
        }
        selectedRoute = route;
        toast("Đã chọn tuyến " + routeLabel(route) + ". Hãy chọn bến đi, bến xuống rồi bấm Tìm chuyến.");
        if (selectionState != null) {
            selectionState.setRoute(route);
            persistSelectionState();
        }
        tripsContainer.removeAllViews();
        routeDetailText.setText(routeLabel(route) + "\n" + safe(route.description));
        renderRoutes(text(routeSearchInput));
        dataRepository.loadStopsFromRoom(route.id, (data, fromCache) -> runOnUiThread(() -> {
            StopModel savedBoarding = null;
            StopModel savedDestination = null;
            TripModel savedTrip = currentSelectedTrip();
            if (savedTrip != null && route.id.equals(savedTrip.routeId)) {
                savedBoarding = currentBoardingStop();
                savedDestination = currentDestinationStop();
            }
            stops.clear();
            for (StopModel stop : data) {
                if (stop != null) {
                    stops.add(stop);
                }
            }
            ArrayAdapter<StopModel> adapter = stopAdapter(stops);
            syncingRouteControls = true;
            try {
                boardingStopSpinner.setAdapter(adapter);
                destinationStopSpinner.setAdapter(adapter);
                selectStopInSpinner(boardingStopSpinner, savedBoarding);
                selectStopInSpinner(destinationStopSpinner, savedDestination);
            } finally {
                syncingRouteControls = false;
            }
            if (selectionState != null) {
                selectionState.setRoute(route);
                selectionState.setRouteStops(stops);
                if (savedBoarding != null && savedBoarding.id != null) {
                    selectionState.setBoardingStop(savedBoarding);
                }
                if (savedDestination != null && savedDestination.id != null) {
                    selectionState.setDestinationStop(savedDestination);
                }
                persistSelectionState();
            }
            renderStopsTimeline(null, null);
            renderMap(stops, null, null, true);
            mapStatusText.setText(routeLabel(route) + "\n" + stops.size() + " bến đang hiển thị."
                    + (fromCache ? "\nDữ liệu offline." : ""));
            onSelectionStateChanged(false);
            if (!fromCache) {
                toast("Đã đồng bộ tuyến " + routeLabel(route));
            }
        }), error -> runOnUiThread(() -> {
            if (stops.isEmpty()) {
                toast(networkErrorMessage(error));
            } else {
                toast("Mất mạng, đang dùng danh sách bến offline.");
            }
        }));
    }

    private void findTrips() {
        StopModel boarding = selectedStop(boardingStopSpinner);
        StopModel destination = selectedStop(destinationStopSpinner);
        if (selectedRoute == null || selectedRoute.id == null || boarding == null || destination == null
                || boarding.id == null || destination.id == null) {
            toast("Vui lòng chọn tuyến, bến lên và bến xuống");
            return;
        }
        if (boarding.id != null && boarding.id.equals(destination.id)) {
            toast("Bến xuống phải khác bến lên.");
            return;
        }
        if (boarding.stopOrder != null
                && destination.stopOrder != null
                && destination.stopOrder <= boarding.stopOrder) {
            toast("Bến xuống phải nằm sau bến lên theo chiều tuyến.");
            return;
        }
        if (selectionState != null) {
            selectionState.setRoute(selectedRoute);
            selectionState.setRouteStops(stops);
            selectionState.setBoardingStop(boarding);
            selectionState.setDestinationStop(destination);
            persistSelectionState();
        }
        dataRepository.findTrips(selectedRoute.id, boarding.id, destination.id, (data, fromCache) -> runOnUiThread(() -> {
            tripsContainer.removeAllViews();
            if (data.isEmpty()) {
                addText(tripsContainer, "Offline: chưa có chuyến nào trong bộ nhớ máy cho tuyến này.");
                return;
            }
            for (TripModel trip : data) {
                if (trip != null && trip.id != null) {
                    tripsContainer.addView(tripCard(trip, boarding, destination));
                }
            }
            if (tripsContainer.getChildCount() == 0) {
                addText(tripsContainer, "Chưa có chuyến đủ dữ liệu để đăng ký.");
            }
        }), error -> runOnUiThread(() -> {
            if (tripsContainer.getChildCount() == 0) {
                addText(tripsContainer, networkErrorMessage(error));
            } else {
                toast("Mất mạng, đang dùng chuyến đã cache.");
            }
        }));
    }

    private View tripCard(TripModel trip, StopModel boarding, StopModel destination) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(8);
        card.setCardElevation(2);
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(getColor(R.color.smartbus_surface));
        card.setTag(trip.id);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(18, 16, 18, 16);
        card.addView(inner);
        inner.addView(label("Chuyến #" + safeLong(trip.id) + " · " + statusLabel(trip.status), 17f, true, R.color.smartbus_text_primary));
        inner.addView(label("Bến lên: " + name(boarding) + "\nBến xuống: " + name(destination), 14f, false, R.color.smartbus_text_secondary));
        MaterialButton select = new MaterialButton(this);
        select.setText("Chọn chuyến này");
        select.setTextColor(getColor(R.color.white));
        select.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_success)));
        select.setOnClickListener(v -> {
            syncSelectedTrip(trip, boarding, destination);
            if (isTripSynchronized(trip.id)) {
                bluetoothStatusText.setText("Đã chọn chuyến #" + safeLong(trip.id)
                        + "\nBến đi: " + name(boarding)
                        + "\nBến xuống: " + name(destination)
                        + "\nTrạng thái: đã đồng bộ vào Chuyến của tôi.");
                toast("Đã đồng bộ chuyến #" + safeLong(trip.id) + ".");
            } else {
                bluetoothStatusText.setText("Chưa ghi được chuyến #" + safeLong(trip.id)
                        + " vào bộ nhớ chọn chuyến. Vui lòng bấm chọn lại.");
                toast("Chưa đồng bộ được chuyến, vui lòng thử lại.");
            }
            showTab(TAB_MY_TRIP);
        });
        inner.addView(select);
        MaterialButton cancel = new MaterialButton(this);
        cancel.setText("Hủy chuyến này");
        cancel.setTextColor(getColor(R.color.smartbus_error));
        cancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_surface)));
        cancel.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_error)));
        cancel.setStrokeWidth(2);
        cancel.setOnClickListener(v -> cancelSelectedTrip());
        inner.addView(cancel);
        return card;
    }

    private void loadRequests() {
        renderLocalCheckIns();
    }

    private void renderLocalCheckIns() {
        requestsContainer.removeAllViews();
        if (localCheckIns.isEmpty()) {
            addText(requestsContainer, getString(R.string.empty_default));
            syncSelectedRequest(null);
            updateBluetoothStatus();
            return;
        }
        syncSelectedRequest(selectedRequestFrom(localCheckIns));
        updateBluetoothStatus();
        for (BoardingRequestModel request : localCheckIns) {
            if (request == null) {
                continue;
            }
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(8);
            card.setCardElevation(2);
            card.setUseCompatPadding(true);
            card.setCardBackgroundColor(getColor(R.color.smartbus_surface));
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(18, 18, 18, 18);
            card.addView(inner);
            addText(inner, "Yêu cầu local #" + request.id + " · " + statusLabel(request.status)
                    + "\nTuyến: #" + safeLong(request.routeId != null
                    ? request.routeId : request.trip == null ? null : request.trip.routeId)
                    + "\nBến lên: " + name(request.boardingStop)
                    + "\nBến xuống: " + name(request.destinationStop)
                    + "\nBluetooth: " + safe(request.bluetoothIdentifier)
                    + "\nPhát BLE: " + (bluetoothManager.isAdvertising() ? "đang phát" : "chưa phát/đã dừng"));
            MaterialButton replay = new MaterialButton(this);
            replay.setText("Phát lại BLE");
            replay.setTextColor(getColor(R.color.white));
            replay.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_primary)));
            replay.setOnClickListener(v -> {
                syncSelectedRequest(request);
                prepareBluetoothCheckIn();
            });
            inner.addView(replay);
            MaterialButton delete = new MaterialButton(this);
            delete.setText("Xóa");
            delete.setTextColor(getColor(R.color.smartbus_error));
            delete.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_surface)));
            delete.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_error)));
            delete.setStrokeWidth(2);
            delete.setOnClickListener(v -> deleteLocalCheckIn(request));
            inner.addView(delete);
            requestsContainer.addView(card);
        }
    }

    private BoardingRequestModel createLocalCheckInRequest(
            TripModel trip,
            StopModel boarding,
            StopModel destination,
            String identifier
    ) {
        BoardingRequestModel request = new BoardingRequestModel();
        request.id = System.currentTimeMillis();
        request.passenger = currentPassenger;
        request.trip = null;
        request.routeId = trip == null ? null : trip.routeId;
        request.boardingStop = copyStop(boarding);
        request.destinationStop = copyStop(destination);
        request.status = "PENDING";
        request.note = "LOCAL_BLE_CHECKIN";
        request.bluetoothIdentifier = identifier;
        return request;
    }

    private void addLocalCheckIn(BoardingRequestModel request) {
        if (request == null || request.id == null) {
            return;
        }
        for (int i = localCheckIns.size() - 1; i >= 0; i--) {
            BoardingRequestModel existing = localCheckIns.get(i);
            if (existing != null && request.id.equals(existing.id)) {
                localCheckIns.remove(i);
            }
        }
        localCheckIns.add(0, request);
        while (localCheckIns.size() > MAX_LOCAL_CHECKINS) {
            localCheckIns.remove(localCheckIns.size() - 1);
        }
        persistLocalCheckIns();
    }

    private void deleteLocalCheckIn(BoardingRequestModel request) {
        if (request == null || request.id == null) {
            return;
        }
        for (int i = localCheckIns.size() - 1; i >= 0; i--) {
            BoardingRequestModel existing = localCheckIns.get(i);
            if (existing != null && request.id.equals(existing.id)) {
                localCheckIns.remove(i);
            }
        }
        if (selectedRequest != null && request.id.equals(selectedRequest.id)) {
            syncSelectedRequest(localCheckIns.isEmpty() ? null : localCheckIns.get(0));
        }
        persistLocalCheckIns();
        renderLocalCheckIns();
        toast("Đã xóa yêu cầu check-in local.");
    }

    private void loadLocalCheckIns() {
        localCheckIns.clear();
        if (preferences == null) {
            return;
        }
        int count = Math.min(preferences.getInt(KEY_LOCAL_CHECKIN_COUNT, 0), MAX_LOCAL_CHECKINS);
        for (int index = 0; index < count; index++) {
            BoardingRequestModel request = readLocalCheckIn(index);
            if (request != null) {
                localCheckIns.add(request);
            }
        }
    }

    private BoardingRequestModel readLocalCheckIn(int index) {
        String prefix = KEY_LOCAL_CHECKIN_PREFIX + index + "_";
        long id = preferences.getLong(prefix + "id", Long.MIN_VALUE);
        long tripId = preferences.getLong(prefix + "trip_id", Long.MIN_VALUE);
        long routeId = preferences.getLong(prefix + "route_id", Long.MIN_VALUE);
        if (id == Long.MIN_VALUE || routeId == Long.MIN_VALUE) {
            return null;
        }
        BoardingRequestModel request = new BoardingRequestModel();
        request.id = id;
        request.passenger = currentPassenger;
        request.routeId = routeId;
        if (tripId != Long.MIN_VALUE) {
            TripModel trip = new TripModel();
            trip.id = tripId;
            trip.routeId = routeId;
            trip.status = preferences.getString(prefix + "trip_status", "IN_PROGRESS");
            request.trip = trip;
        }
        request.boardingStop = readLocalStop(prefix, "boarding");
        request.destinationStop = readLocalStop(prefix, "destination");
        request.status = preferences.getString(prefix + "status", "PENDING");
        request.note = "LOCAL_BLE_CHECKIN";
        request.bluetoothIdentifier = preferences.getString(prefix + "identifier", null);
        return request;
    }

    private StopModel readLocalStop(String prefix, String role) {
        long id = preferences.getLong(prefix + role + "_id", Long.MIN_VALUE);
        if (id == Long.MIN_VALUE) {
            return null;
        }
        StopModel stop = new StopModel();
        stop.id = id;
        stop.name = preferences.getString(prefix + role + "_name", null);
        stop.latitude = java.math.BigDecimal.valueOf(Double.longBitsToDouble(
                preferences.getLong(prefix + role + "_lat", Double.doubleToLongBits(0d))
        ));
        stop.longitude = java.math.BigDecimal.valueOf(Double.longBitsToDouble(
                preferences.getLong(prefix + role + "_lng", Double.doubleToLongBits(0d))
        ));
        int order = preferences.getInt(prefix + role + "_order", Integer.MIN_VALUE);
        if (order != Integer.MIN_VALUE) {
            stop.stopOrder = order;
        }
        return stop;
    }

    private void persistLocalCheckIns() {
        if (preferences == null) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(KEY_LOCAL_CHECKIN_COUNT, localCheckIns.size());
        for (int index = 0; index < MAX_LOCAL_CHECKINS; index++) {
            clearLocalCheckIn(editor, index);
            if (index < localCheckIns.size()) {
                writeLocalCheckIn(editor, index, localCheckIns.get(index));
            }
        }
        editor.apply();
    }

    private void writeLocalCheckIn(SharedPreferences.Editor editor, int index, BoardingRequestModel request) {
        String prefix = KEY_LOCAL_CHECKIN_PREFIX + index + "_";
        editor.putLong(prefix + "id", request.id == null ? Long.MIN_VALUE : request.id);
        editor.putLong(prefix + "trip_id", request.trip == null || request.trip.id == null ? Long.MIN_VALUE : request.trip.id);
        editor.putLong(prefix + "route_id", request.routeId != null
                ? request.routeId
                : request.trip == null || request.trip.routeId == null ? Long.MIN_VALUE : request.trip.routeId);
        editor.putString(prefix + "trip_status", request.trip == null ? null : request.trip.status);
        editor.putString(prefix + "status", request.status);
        editor.putString(prefix + "identifier", request.bluetoothIdentifier);
        writeLocalStop(editor, prefix, "boarding", request.boardingStop);
        writeLocalStop(editor, prefix, "destination", request.destinationStop);
    }

    private void writeLocalStop(SharedPreferences.Editor editor, String prefix, String role, StopModel stop) {
        if (stop == null || stop.id == null) {
            return;
        }
        editor.putLong(prefix + role + "_id", stop.id);
        editor.putString(prefix + role + "_name", stop.name);
        editor.putLong(prefix + role + "_lat", Double.doubleToLongBits(
                stop.latitude == null ? 0d : stop.latitude.doubleValue()
        ));
        editor.putLong(prefix + role + "_lng", Double.doubleToLongBits(
                stop.longitude == null ? 0d : stop.longitude.doubleValue()
        ));
        if (stop.stopOrder != null) {
            editor.putInt(prefix + role + "_order", stop.stopOrder);
        }
    }

    private void clearLocalCheckIn(SharedPreferences.Editor editor, int index) {
        String prefix = KEY_LOCAL_CHECKIN_PREFIX + index + "_";
        editor.remove(prefix + "id");
        editor.remove(prefix + "trip_id");
        editor.remove(prefix + "route_id");
        editor.remove(prefix + "trip_status");
        editor.remove(prefix + "status");
        editor.remove(prefix + "identifier");
        clearLocalStop(editor, prefix, "boarding");
        clearLocalStop(editor, prefix, "destination");
    }

    private void clearLocalStop(SharedPreferences.Editor editor, String prefix, String role) {
        editor.remove(prefix + role + "_id");
        editor.remove(prefix + role + "_name");
        editor.remove(prefix + role + "_lat");
        editor.remove(prefix + role + "_lng");
        editor.remove(prefix + role + "_order");
    }

    private TripModel copyTrip(TripModel source) {
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

    private BoardingRequestModel firstTrackableRequest(List<BoardingRequestModel> requests) {
        for (BoardingRequestModel request : requests) {
            if (request == null) {
                continue;
            }
            if (!"COMPLETED".equals(request.status) && !"CANCELLED".equals(request.status)) {
                return request;
            }
        }
        return requests.isEmpty() ? null : requests.get(0);
    }

    private BoardingRequestModel selectedRequestFrom(List<BoardingRequestModel> requests) {
        if (requests == null || requests.isEmpty()) {
            return null;
        }
        Long selectedRequestId = selectionState == null ? null : selectionState.boardingRequestId();
        if (selectedRequestId != null) {
            for (BoardingRequestModel request : requests) {
                if (request != null && selectedRequestId.equals(request.id)) {
                    return request;
                }
            }
        }
        if (selectedRequest != null && selectedRequest.id != null) {
            for (BoardingRequestModel request : requests) {
                if (request != null && selectedRequest.id.equals(request.id)) {
                    return request;
                }
            }
        }
        Long selectedTripId = currentSelectedTripId();
        if (selectedTripId != null) {
            for (BoardingRequestModel request : requests) {
                Long tripId = request == null || request.trip == null ? null : request.trip.id;
                if (selectedTripId.equals(tripId)
                        && !"COMPLETED".equals(request.status)
                        && !"CANCELLED".equals(request.status)) {
                    return request;
                }
            }
            return null;
        }
        return firstTrackableRequest(requests);
    }

    private void syncSelectedRequest(BoardingRequestModel request) {
        restoreSelectionStateIfNeeded();
        selectedRequest = request;
        if (selectionState == null || selectedTripStore == null) {
            return;
        }
        if (request == null) {
            selectionState.syncBoardingRequest(null);
            selectedTripStore.persist(selectionState);
            onSelectionStateChanged(false);
            return;
        }
        selectionState.syncBoardingRequest(request);
        selectedTripStore.persist(selectionState);
        syncSelectedRouteFromTrip(selectionState.selectedTrip());
        onSelectionStateChanged(false);
    }

    private void syncSelectedTrip(TripModel trip, StopModel boarding, StopModel destination) {
        if (selectedTripStore == null || trip == null || trip.id == null) {
            return;
        }
        if (selectionState == null) {
            selectionState = selectedTripStore.state();
        }
        selectedRequest = null;
        selectionState.selectTrip(trip);
        selectionState.setBoardingStop(boarding);
        selectionState.setDestinationStop(destination);
        selectedTripStore.selectTrip(trip, boarding, destination);
        selectedTripStore.persist(selectionState);
        syncSelectedRouteFromTrip(selectionState.selectedTrip());
        onSelectionStateChanged(true);
        showLocalDropoffDistance(destination);
    }

    private boolean isTripSynchronized(Long tripId) {
        if (tripId == null || selectedTripStore == null) {
            return false;
        }
        restoreSelectionStateIfNeeded();
        TripModel memoryTrip = selectionState == null ? null : selectionState.selectedTrip();
        TripModel storedTrip = selectedTripStore.trip();
        return memoryTrip != null && tripId.equals(memoryTrip.id)
                && storedTrip != null && tripId.equals(storedTrip.id);
    }

    private TripModel currentSelectedTrip() {
        restoreSelectionStateIfNeeded();
        return selectionState == null ? null : selectionState.selectedTrip();
    }

    private void restoreSelectionStateIfNeeded() {
        if (selectionState != null && selectionState.hasTrip()) {
            return;
        }
        if (selectedTripStore != null && selectedTripStore.hasTrip()) {
            selectionState = selectedTripStore.state();
        }
    }

    private BoardingRequestModel currentBoardingRequest() {
        Long selectedTripId = currentSelectedTripId();
        Long selectedRequestId = selectionState == null ? null : selectionState.boardingRequestId();
        if (selectedRequest != null && selectedRequest.id != null) {
            Long requestTripId = selectedRequest.trip == null ? null : selectedRequest.trip.id;
            if ((selectedRequestId != null && selectedRequestId.equals(selectedRequest.id))
                    || (selectedTripId != null && selectedTripId.equals(requestTripId))) {
                return selectedRequest;
            }
        }
        return selectionState == null ? null : selectionState.boardingRequest();
    }

    private StopModel currentBoardingStop() {
        restoreSelectionStateIfNeeded();
        return selectionState == null ? null : selectionState.boardingStop();
    }

    private StopModel currentDestinationStop() {
        restoreSelectionStateIfNeeded();
        return selectionState == null ? null : selectionState.destinationStop();
    }

    private void onSelectionStateChanged(boolean fitMapToSelection) {
        persistSelectionState();
        syncCheckInSelectionControls();
        renderSelectedTripSummary();
        updateBluetoothStatus();
        updateHomeSummary();
        updateSelectedTripMap(fitMapToSelection);
    }

    private void persistSelectionState() {
        restoreSelectionStateIfNeeded();
        if (selectedTripStore != null && selectionState != null) {
            selectedTripStore.persist(selectionState);
        }
        if (dataRepository != null) {
            dataRepository.persistSelection(selectionState);
        }
    }

    private void cancelSelectedTrip() {
        selectedRequest = null;
        selectedRoutePathKey = null;
        selectedRoutePathInFlightKey = null;
        selectedRoutePathPoints.clear();
        if (selectionState != null) {
            selectionState.clear();
        }
        if (selectedTripStore != null) {
            selectedTripStore.clear();
        }
        if (dataRepository != null) {
            dataRepository.clearSelection();
        }
        nearbyTripsContainer.removeAllViews();
        tripsContainer.removeAllViews();
        trackingText.setText("Chưa chọn chuyến. Vào tab Tuyến xe để chọn tuyến, bến đi và bến xuống.");
        tripProgress.setProgress(0);
        updateBluetoothStatus();
        updateHomeSummary();
        updateSelectedTripMap(false);
        toast("Đã hủy chuyến đang chọn.");
    }

    private void syncCheckInSelectionControls() {
        if (nearbyBoardingStopSpinner == null || nearbyDestinationStopSpinner == null) {
            return;
        }
        syncingSelectionControls = true;
        try {
            nearbyRouteStops.clear();
            if (selectionState != null && selectionState.hasStops()) {
                nearbyRouteStops.addAll(selectionState.routeStops());
            } else if (!stops.isEmpty()) {
                nearbyRouteStops.addAll(stops);
            }
            nearbyBoardingStops.clear();
            nearbyBoardingStops.add(placeholderStop());
            for (StopModel stop : nearbyRouteStops) {
                if (stop != null && stop.id != null && stop.stopOrder != null) {
                    nearbyBoardingStops.add(stop);
                }
            }
            ArrayAdapter<StopModel> boardingAdapter = stopAdapter(nearbyBoardingStops);
            nearbyBoardingStopSpinner.setAdapter(boardingAdapter);
            nearbyBoardingStopSpinner.setEnabled(nearbyBoardingStops.size() > 1);
            nearbyPickBoardingStopButton.setEnabled(nearbyBoardingStops.size() > 1);
            StopModel boarding = currentBoardingStop();
            nearbyBoardingStop = boarding != null && boarding.id != null ? boarding : null;
            if (boarding != null) {
                selectStopInSpinner(nearbyBoardingStopSpinner, boarding);
            } else {
                nearbyBoardingStopSpinner.setSelection(0);
            }

            nearbyDestinationStops.clear();
            if (selectionState != null && currentBoardingStop() != null && currentBoardingStop().id != null) {
                nearbyDestinationStops.addAll(selectionState.destinationChoices());
            }
            ArrayAdapter<StopModel> destinationAdapter = stopAdapter(nearbyDestinationStops);
            nearbyDestinationStopSpinner.setAdapter(destinationAdapter);
            StopModel destination = currentDestinationStop();
            if (destination != null) {
                selectStopInSpinner(nearbyDestinationStopSpinner, destination);
            }
            boolean canChooseDestination = !nearbyDestinationStops.isEmpty();
            nearbyDestinationStopSpinner.setEnabled(canChooseDestination);
            nearbyPickDestinationStopButton.setEnabled(canChooseDestination);
            nearbyConfirmCheckInButton.setEnabled(true);
        } finally {
            syncingSelectionControls = false;
        }
        updateNearbySelectionUi();
    }

    private void renderSelectedTripSummary() {
        if (trackingText == null) {
            return;
        }
        TripModel trip = currentSelectedTrip();
        if (trip == null || trip.id == null) {
            RouteModel route = selectionState == null || selectionState.selectedRoute() == null
                    ? selectedRoute
                    : selectionState.selectedRoute();
            if (route != null && route.id != null) {
                boolean confirmed = selectionState != null && selectionState.isLocalSelectionConfirmed();
                trackingText.setText("Đang chọn tuyến: " + routeLabel(route)
                        + "\nBến đi: " + name(currentBoardingStop())
                        + "\nBến xuống: " + name(currentDestinationStop())
                        + "\n" + (confirmed
                        ? "Đã xác nhận bến đi/xuống cho tuyến này."
                        : "Chưa xác nhận bến đi/xuống cho tuyến này."));
            } else {
                trackingText.setText("Chưa chọn tuyến. Vào tab Tuyến xe để chọn tuyến, bến đi và bến xuống.");
            }
            if (tripProgress != null) {
                tripProgress.setProgress(0);
            }
            return;
        }
        RouteModel route = selectionState == null || selectionState.selectedRoute() == null
                ? selectedRoute
                : selectionState.selectedRoute();
        BoardingRequestModel request = currentBoardingRequest();
        String requestLine = request == null || request.id == null
                ? "Yêu cầu check-in: chưa tạo"
                : "Yêu cầu check-in #" + request.id + " · " + statusLabel(request.status);
        trackingText.setText("Tuyến: " + routeLabel(route)
                + "\nChuyến #" + safeLong(trip.id)
                + "\nTrạng thái chuyến: " + statusLabel(trip.status)
                + "\nBến đi: " + name(currentBoardingStop())
                + "\nBến xuống: " + name(currentDestinationStop())
                + "\nThông tin xe: " + (trip.driverId == null ? "backend chưa cung cấp" : "tài xế #" + safeLong(trip.driverId))
                + "\n" + requestLine);
    }

    private void syncSelectedRouteFromTrip(TripModel trip) {
        if (trip == null || trip.routeId == null) {
            return;
        }
        RouteModel matched = routeById(trip.routeId);
        if (selectedRoute == null
                || selectedRoute.id == null
                || !trip.routeId.equals(selectedRoute.id)
                || (matched != null && selectedRoute != matched)) {
            selectedRoute = matched == null ? placeholderRoute(trip.routeId) : matched;
            if (selectionState != null) {
                selectionState.setRoute(selectedRoute);
                persistSelectionState();
            }
            if (routeDetailText != null) {
                routeDetailText.setText(routeLabel(selectedRoute) + "\n" + safe(selectedRoute.description));
            }
        }
        loadStopsForSelectedTripRoute(trip.routeId);
    }

    private RouteModel routeById(Long routeId) {
        if (routeId == null) {
            return null;
        }
        for (RouteModel route : routes) {
            if (route != null && routeId.equals(route.id)) {
                return route;
            }
        }
        return null;
    }

    private RouteModel placeholderRoute(Long routeId) {
        RouteModel route = new RouteModel();
        route.id = routeId;
        route.code = "Tuyến #" + safeLong(routeId);
        route.name = "đang tải dữ liệu";
        route.description = "";
        return route;
    }

    private void loadStopsForSelectedTripRoute(Long routeId) {
        if (routeId == null || stopsBelongToRoute(routeId)) {
            return;
        }
        dataRepository.loadStopsFromRoom(routeId, (data, fromCache) -> runOnUiThread(() -> {
            stops.clear();
            for (StopModel stop : data) {
                if (stop != null) {
                    stops.add(stop);
                }
            }
            if (selectionState != null) {
                selectionState.setRouteStops(stops);
                persistSelectionState();
            }
            renderStopsTimeline(null, null);
            renderMap(stops, null, null, currentTab == TAB_MAP);
            mapStatusText.setText(routeLabel(selectedRoute) + "\n" + stops.size() + " bến đang hiển thị."
                    + (fromCache ? "\nDữ liệu offline." : ""));
            onSelectionStateChanged(currentTab == TAB_MAP);
        }), error -> runOnUiThread(() -> mapStatusText.setText(stops.isEmpty()
                ? networkErrorMessage(error)
                : "Mất mạng, đang dùng dữ liệu bến offline.")));
    }

    private boolean stopsBelongToRoute(Long routeId) {
        if (routeId == null || stops.isEmpty()) {
            return false;
        }
        for (StopModel stop : stops) {
            if (stop != null && stop.routeId != null && !routeId.equals(stop.routeId)) {
                return false;
            }
        }
        return true;
    }

    private void cancelRequest(BoardingRequestModel request) {
        if (request == null || request.id == null) {
            return;
        }
        if (isLocalCheckIn(request)) {
            deleteLocalCheckIn(request);
            return;
        }
        call(RetrofitClient.api().cancel(request.id), data -> {
            toast("Đã hủy yêu cầu lên xe.");
            syncSelectedRequest(data);
            loadRequests();
            updateBluetoothStatus();
            stopTrackingRefresh();
        });
    }

    private void track(BoardingRequestModel request, boolean userInitiated) {
        if (request == null || request.id == null) {
            toast("Yêu cầu lên xe thiếu mã theo dõi.");
            return;
        }
        if (isLocalCheckIn(request)) {
            trackLocalCheckIn(request, userInitiated);
            return;
        }
        selectedRequest = request;
        syncSelectedRequest(request);
        Call<ApiResponse<PassengerTripTrackingModel>> trackingCall = RetrofitClient.api().tracking(request.id);
        Success<PassengerTripTrackingModel> trackingSuccess = data -> {
            BoardingRequestModel trackedRequest = data.boardingRequest != null
                    ? data.boardingRequest
                    : request;
            syncSelectedRequest(trackedRequest);
            trackingText.setText("Tuyến: " + routeLabel(data.route)
                    + "\nTrạng thái: " + statusLabel(trackedRequest.status)
                    + "\nBến hiện tại: " + name(data.currentStop)
                    + "\nBến tiếp theo: " + name(data.nextStop)
                    + "\nBến xuống: " + name(trackedRequest.destinationStop)
                    + "\nETA: " + safe(data.etaMinutes) + " phút"
                    + "\nCòn lại: " + safe(data.remainingStops) + " bến"
                    + "\nThông báo: " + safe(data.notification));
            tripProgress.setProgress(clampProgress(data.progressPercent));
            Long currentId = data.currentStop == null ? null : data.currentStop.id;
            Long nextId = data.nextStop == null ? null : data.nextStop.id;
            renderStopsTimeline(currentId, nextId);
            renderMap(stops, currentId, nextId, false);
            mapStatusText.setText("Xe hiện tại: " + name(data.currentStop)
                    + "\nBến tiếp theo: " + name(data.nextStop)
                    + "\nKhông tự kéo camera khi bạn đang xem bản đồ.");
            updateBluetoothStatus();
            updateHomeSummary();
            if (userInitiated) {
                toast("Đã cập nhật trạng thái từ backend.");
            }
            if (selectedRequest != null
                    && ("COMPLETED".equals(selectedRequest.status)
                    || "CANCELLED".equals(selectedRequest.status))) {
                stopTrackingRefresh();
                loadRequests();
            }
        };
        if (userInitiated) {
            call(trackingCall, trackingSuccess);
        } else {
            callSilent(trackingCall, trackingSuccess);
        }
    }

    private boolean isLocalCheckIn(BoardingRequestModel request) {
        return request != null && "LOCAL_BLE_CHECKIN".equals(request.note);
    }

    private void trackLocalCheckIn(BoardingRequestModel request, boolean userInitiated) {
        selectedRequest = request;
        syncSelectedRequest(request);
        StopModel destination = request.destinationStop;
        trackingText.setText("Yêu cầu check-in local"
                + "\nChuyến: #" + safeLong(request.trip == null ? null : request.trip.id)
                + "\nTrạng thái: " + statusLabel(request.status)
                + "\nBến lên: " + name(request.boardingStop)
                + "\nBến xuống: " + name(destination)
                + "\nBluetooth: " + safe(request.bluetoothIdentifier));
        tripProgress.setProgress(0);
        showLocalDropoffDistance(destination);
        updateBluetoothStatus();
        updateHomeSummary();
        if (userInitiated) {
            toast("Đang theo dõi yêu cầu check-in local.");
        }
    }

    private String localSelectionStatus(boolean confirmed) {
        RouteModel route = currentSelectedRoute();
        if (route == null || route.id == null) {
            return "Chưa chọn tuyến.";
        }
        StopModel boarding = currentBoardingStop();
        if (boarding == null || boarding.id == null) {
            return "Tuyến đã chọn nhưng chưa chọn bến đi.";
        }
        StopModel destination = currentDestinationStop();
        if (destination == null || destination.id == null) {
            return "Đã chọn bến đi nhưng chưa chọn bến xuống.";
        }
        return confirmed
                ? "Đã xác nhận bến đi/xuống cho tuyến #" + safeLong(route.id)
                : "Đã chọn bến đi: " + name(boarding)
                + "\nĐã chọn bến xuống: " + name(destination)
                + "\nChưa xác nhận bến đi/xuống.";
    }

    private void handleCheckInButtonClick() {
        restoreSelectionStateIfNeeded();
        BoardingRequestModel request = currentBoardingRequest();
        if (request != null && request.id != null) {
            prepareBluetoothCheckIn();
            return;
        }
        if (selectionState == null || !selectionState.isLocalSelectionConfirmed()
                || currentSelectedRouteId() == null) {
            findNearbyTripsForCheckIn();
            return;
        }
        prepareBluetoothCheckIn();
    }

    private void updateBluetoothStatus() {
        if (bluetoothStatusText == null || checkInButton == null) {
            return;
        }
        BoardingRequestModel request = currentBoardingRequest();
        if (request == null || request.id == null) {
            TripModel pendingTrip = currentSelectedTrip();
            if (pendingTrip != null && pendingTrip.id != null) {
                StopModel boarding = currentBoardingStop();
                StopModel destination = currentDestinationStop();
                boolean readyToCreateRequest = canConfirmNearbySelection();
                boolean confirmed = selectionState != null && selectionState.isLocalSelectionConfirmed();
                bluetoothStatusText.setText("Đã chọn chuyến #" + safeLong(pendingTrip.id)
                        + (confirmed ? " · đã xác nhận" : " · chưa xác nhận")
                        + "\nBến đi: " + (boarding == null ? getString(R.string.boarding_stop_not_selected) : name(boarding))
                        + "\nBến xuống: " + name(destination)
                        + "\n" + (confirmed
                        ? "Bấm Tìm chuyến gần tôi để phát BLE check-in."
                        : "Chọn bến đi/xuống rồi bấm Xác nhận bến đi/xuống."));
                checkInButton.setText(R.string.bluetooth_checkin_action);
                checkInButton.setEnabled(true);
                nearbyConfirmCheckInButton.setText(R.string.confirm_trip_stops_action);
                nearbyConfirmCheckInButton.setEnabled(true);
            } else {
                RouteModel route = selectionState == null || selectionState.selectedRoute() == null
                        ? selectedRoute
                        : selectionState.selectedRoute();
                if (route != null && route.id != null) {
                    boolean confirmed = selectionState != null && selectionState.isLocalSelectionConfirmed();
                    bluetoothStatusText.setText("Đã chọn tuyến: " + routeLabel(route)
                            + (confirmed ? "\nĐã xác nhận bến đi/xuống." : "\nChưa xác nhận bến đi/xuống.")
                            + "\n" + (confirmed
                            ? "Bấm Tìm chuyến gần tôi để phát BLE."
                            : "Chọn bến đi/xuống rồi bấm Xác nhận bến đi/xuống."));
            } else {
                bluetoothStatusText.setText(route != null && route.id != null
                        ? "Đã chọn tuyến #" + safeLong(route.id)
                                + "\nHãy chọn bến đi và bến xuống rồi bấm Xác nhận bến đi/xuống."
                        : "Chưa chọn tuyến. Vào tab Tuyến xe để chọn tuyến và bến.");
                }
                checkInButton.setText(R.string.bluetooth_checkin_action);
                checkInButton.setEnabled(true);
                nearbyConfirmCheckInButton.setText(R.string.confirm_trip_stops_action);
                nearbyConfirmCheckInButton.setEnabled(true);
            }
            if (nearbyBoardingStopText != null && nearbyBoardingStop == null) {
                nearbyBoardingStopText.setText(R.string.boarding_stop_not_selected);
            }
            return;
        }
        BluetoothConnectionState state = bluetoothManager.getState();
        String identifier = request.bluetoothIdentifier == null
                ? "Chưa có mã"
                : request.bluetoothIdentifier;
        bluetoothStatusText.setText("Mã check-in: " + identifier
                + "\nTuyến #" + safeLong(currentSelectedRouteId())
                + "\n" + (bluetoothManager.isAdvertising() ? "Đang phát BLE." : bluetoothStateMessage(state)));
        boolean canCheckIn = "PENDING".equals(request.status) || "CONFIRMED".equals(request.status);
        checkInButton.setText("Chuẩn bị check-in");
        checkInButton.setEnabled(true);
    }

    private void findNearbyTripsForCheckIn() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION
            );
            return;
        }
        currentLocation = lastKnownLocation();
        if (currentLocation == null) {
            toast("Chưa lấy được vị trí hiện tại. Hãy bật GPS rồi thử lại.");
            return;
        }
        clearNearbySelection(false);
        nearbyTripsContainer.removeAllViews();
        bluetoothStatusText.setText("Đang tìm bến gần nhất và các chuyến đang đi qua...");
        dataRepository.findNearbyActiveTrips(
                currentLocation.getLatitude(),
                currentLocation.getLongitude(),
                (data, fromCache) -> runOnUiThread(() -> {
                    if (data == null || data.boardingStop == null) {
                        bluetoothStatusText.setText("Chưa có dữ liệu bến/tuyến offline. Mở tab Tuyến xe khi có mạng để tải dữ liệu.");
                        toast("Chưa có dữ liệu bến offline trên máy.");
                        return;
                    }
                    renderNearbyTrips(data);
                    if (fromCache) {
                        toast("Đang dùng tuyến/bến offline trong bộ nhớ máy.");
                    }
                }),
                error -> runOnUiThread(() -> {
                    bluetoothStatusText.setText(error.getMessage() != null
                            ? error.getMessage()
                            : "Không tìm được chuyến gần vị trí hiện tại.");
                    toast(error.getMessage() != null
                            ? error.getMessage()
                            : "Không tìm được chuyến gần vị trí hiện tại.");
                })
        );
    }

    private void renderNearbyTrips(NearbyActiveTripsModel data) {
        nearbyTripsContainer.removeAllViews();
        if (data == null || data.boardingStop == null) {
            bluetoothStatusText.setText("Chưa xác định được bến gần nhất để check-in.");
            return;
        }
        RouteModel savedRoute = currentSelectedRoute();
        StopModel savedBoarding = currentBoardingStop();
        StopModel savedDestination = currentDestinationStop();
        boolean confirmed = selectionState != null && selectionState.isLocalSelectionConfirmed();
        selectedRoute = confirmed && savedRoute != null ? savedRoute : data.route;
        if (selectionState != null) {
            selectionState.setRoute(selectedRoute);
            if (confirmed && savedBoarding != null) {
                selectionState.setBoardingStop(savedBoarding);
            }
            if (confirmed && savedDestination != null) {
                selectionState.setDestinationStop(savedDestination);
            }
            persistSelectionState();
        }
        nearbySuggestedBoardingStop = data.boardingStop;
        nearbySuggestedDistanceMeters = data.distanceMeters;
        nearbyAutoBoardingAllowed = data.distanceMeters != null
                && data.distanceMeters <= AUTO_BOARDING_STOP_RADIUS_METERS;
        nearbyBoardingStop = confirmed && savedBoarding != null
                ? savedBoarding
                : nearbyAutoBoardingAllowed ? data.boardingStop : null;
        if (selectionState != null && selectionState.hasStops()) {
            nearbyRouteStops.clear();
            nearbyRouteStops.addAll(selectionState.routeStops());
        }
        if (!nearbyRouteStops.isEmpty()) {
            populateNearbyBoardingChoices();
        }
        updateNearbySelectionUi();
        bluetoothStatusText.setText((confirmed ? "Đã giữ lựa chọn đã xác nhận. "
                : nearbyAutoBoardingAllowed ? "Đã tự chọn bến đi: " : "Bến gần nhất gợi ý: ")
                + name(data.boardingStop)
                + "\nTuyến: " + routeLabel(selectedRoute)
                + "\nKhoảng cách: " + distanceLabel(data.distanceMeters)
                + (confirmed
                ? "\nĐã xác nhận bến đi/xuống, sẵn sàng phát BLE."
                : "\nChọn tuyến và bến đi/bến xuống rồi xác nhận."));
        if (data.trips == null || data.trips.isEmpty()) {
            addText(nearbyTripsContainer, "Chưa có chuyến offline cho tuyến này. Vào tab Tuyến xe để chọn tuyến/bến rồi tìm chuyến.");
            return;
        }
        for (TripModel trip : data.trips) {
            if (trip != null && trip.id != null) {
                nearbyTripsContainer.addView(nearbyTripCard(trip, data.boardingStop, data.suggestedDestinationStop));
            }
        }
    }

    private View nearbyTripCard(TripModel trip, StopModel boarding, StopModel destination) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(8);
        card.setCardElevation(2);
        card.setUseCompatPadding(true);
        card.setCardBackgroundColor(getColor(R.color.smartbus_surface));
        card.setTag(trip.id);
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(18, 16, 18, 16);
        card.addView(inner);
        inner.addView(label("Chuyến #" + safeLong(trip.id) + " · " + statusLabel(trip.status), 16f, true, R.color.smartbus_text_primary));
        inner.addView(label("Lên tại: " + name(boarding)
                + "\nXuống gợi ý: " + name(destination)
                + "\nSau khi chọn chuyến, bạn có thể đổi bến đi thủ công.", 14f, false, R.color.smartbus_text_secondary));
        MaterialButton select = new MaterialButton(this);
        select.setText("Chọn chuyến này");
        select.setTextColor(getColor(R.color.white));
        select.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_success)));
        select.setOnClickListener(v -> selectNearbyTrip(trip, boarding, destination));
        inner.addView(select);
        MaterialButton cancel = new MaterialButton(this);
        cancel.setText("Hủy chuyến này");
        cancel.setTextColor(getColor(R.color.smartbus_error));
        cancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_surface)));
        cancel.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_error)));
        cancel.setStrokeWidth(2);
        cancel.setOnClickListener(v -> cancelSelectedTrip());
        inner.addView(cancel);
        return card;
    }

    private void selectNearbyTrip(TripModel trip, StopModel boarding, StopModel suggestedDestination) {
        if (trip == null || trip.id == null) {
            toast("Chuyến này thiếu mã định danh trong dữ liệu offline.");
            return;
        }
        Long routeId = resolveRouteIdForNearbyTrip(trip, boarding);
        if (routeId == null) {
            bluetoothStatusText.setText("Không xác định được tuyến của chuyến #" + safeLong(trip.id)
                    + "\nThiếu routeId trong dữ liệu chuyến/bến offline.");
            toast("Chuyến này thiếu tuyến trong bộ nhớ máy.");
            return;
        }
        syncSelectedTrip(trip, nearbyBoardingStop, suggestedDestination);
        if (!isTripSynchronized(trip.id)) {
            bluetoothStatusText.setText("Chưa ghi được chuyến #" + safeLong(trip.id)
                    + " vào bộ nhớ chọn chuyến. Vui lòng bấm chọn lại.");
            toast("Chưa đồng bộ được chuyến, vui lòng thử lại.");
            return;
        }
        markSelectedNearbyTrip(trip);
        toast("Đã chọn chuyến #" + safeLong(trip.id) + ". Chọn bến đi/xuống rồi bấm Xác nhận bến đi/xuống.");
        onSelectionStateChanged(true);
        bluetoothStatusText.setText("Đã chọn chuyến #" + safeLong(trip.id)
                + "\nTuyến #" + routeId
                + "\nĐang tải danh sách bến đi và bến xuống...");
        dataRepository.loadStopsFromRoom(routeId, (data, fromCache) -> runOnUiThread(() -> {
            nearbyRouteStops.clear();
            int rawCount = data == null ? 0 : data.size();
            int missingOrderCount = 0;
            if (data != null) {
                for (StopModel stop : data) {
                    if (stop == null || stop.id == null) {
                        continue;
                    }
                    if (stop.stopOrder == null) {
                        missingOrderCount++;
                    } else {
                        nearbyRouteStops.add(stop);
                    }
                }
            }
            if (selectionState != null) {
                selectionState.setRouteStops(nearbyRouteStops);
                if (nearbyBoardingStop != null) {
                    selectionState.setBoardingStop(nearbyBoardingStop);
                }
                if (suggestedDestination != null) {
                    selectionState.setDestinationStop(suggestedDestination);
                }
                persistSelectionState();
            }
            populateNearbyBoardingChoices();
            bluetoothStatusText.setText("Đã chọn chuyến #" + safeLong(trip.id)
                    + "\nTrạng thái: đã chọn chuyến"
                    + "\nBến đi: " + (nearbyBoardingStop == null ? getString(R.string.boarding_stop_not_selected) : name(nearbyBoardingStop))
                    + "\nChọn bến đi và bến xuống rồi xác nhận check-in."
                    + (fromCache ? "\nDữ liệu bến offline, đang đồng bộ nền." : ""));
            if (nearbyRouteStops.isEmpty()) {
                bluetoothStatusText.setText("Đã chọn chuyến #" + safeLong(trip.id)
                        + "\nTuyến #" + routeId
                        + "\nBackend trả " + rawCount + " bến, nhưng " + missingOrderCount
                        + " bến thiếu stopOrder nên chưa lập được chặng hợp lệ."
                        + "\nKiểm tra dữ liệu stops của tuyến này trong backend.");
                toast(rawCount == 0
                        ? "Backend chưa trả danh sách bến của tuyến này."
                        : "Danh sách bến thiếu thứ tự stopOrder.");
            } else if (nearbyBoardingStop == null) {
                toast("Vui lòng chọn bến đi thủ công.");
            }
        }), error -> runOnUiThread(() -> {
            if (nearbyRouteStops.isEmpty()) {
                toast(networkErrorMessage(error));
            } else {
                toast("Mất mạng, đang dùng danh sách bến offline.");
            }
        }));
    }

    private Long resolveRouteIdForNearbyTrip(TripModel trip, StopModel boarding) {
        if (trip != null && trip.routeId != null) {
            return trip.routeId;
        }
        if (boarding != null && boarding.routeId != null) {
            return boarding.routeId;
        }
        if (nearbySuggestedBoardingStop != null && nearbySuggestedBoardingStop.routeId != null) {
            return nearbySuggestedBoardingStop.routeId;
        }
        return selectedRoute == null ? null : selectedRoute.id;
    }

    private void populateNearbyBoardingChoices() {
        // Spinner adapter changes emit selection callbacks; they are UI restoration, not user input.
        syncingSelectionControls = true;
        nearbyBoardingStops.clear();
        nearbyBoardingStops.add(placeholderStop());
        for (StopModel stop : nearbyRouteStops) {
            if (stop != null && stop.id != null && stop.stopOrder != null) {
                nearbyBoardingStops.add(stop);
            }
        }
        ArrayAdapter<StopModel> adapter = stopAdapter(nearbyBoardingStops);
        nearbyBoardingStopSpinner.setAdapter(adapter);
        nearbyBoardingStopSpinner.setEnabled(nearbyBoardingStops.size() > 1);
        nearbyPickBoardingStopButton.setEnabled(nearbyBoardingStops.size() > 1);

        int selectedIndex = 0;
        StopModel currentBoarding = currentBoardingStop();
        if (currentBoarding != null && currentBoarding.id != null) {
            for (int i = 1; i < nearbyBoardingStops.size(); i++) {
                StopModel stop = nearbyBoardingStops.get(i);
                if (stop != null && currentBoarding.id.equals(stop.id)) {
                    selectedIndex = i;
                    break;
                }
            }
        } else if (nearbyAutoBoardingAllowed && nearbySuggestedBoardingStop != null && nearbySuggestedBoardingStop.id != null) {
            for (int i = 1; i < nearbyBoardingStops.size(); i++) {
                StopModel stop = nearbyBoardingStops.get(i);
                if (stop != null && nearbySuggestedBoardingStop.id.equals(stop.id)) {
                    selectedIndex = i;
                    break;
                }
            }
        }
        nearbyBoardingStopSpinner.setSelection(selectedIndex);
        nearbyBoardingStop = selectedIndex > 0 ? nearbyBoardingStops.get(selectedIndex) : null;
        if (selectionState != null) {
            selectionState.setBoardingStop(nearbyBoardingStop);
            persistSelectionState();
        }
        syncingSelectionControls = false;
        updateNearbySelectionUi();
        updateNearbyDestinationChoices();
    }

    private void updateNearbyDestinationChoices() {
        syncingSelectionControls = true;
        nearbyDestinationStops.clear();
        StopModel boarding = currentBoardingStop();
        if (selectionState != null && boarding != null && boarding.id != null) {
            nearbyDestinationStops.addAll(selectionState.destinationChoices());
        }
        ArrayAdapter<StopModel> adapter = stopAdapter(nearbyDestinationStops);
        nearbyDestinationStopSpinner.setAdapter(adapter);
        StopModel currentDestination = currentDestinationStop();
        if (!nearbyDestinationStops.isEmpty() && currentDestination != null && currentDestination.id != null) {
            selectStopInSpinner(nearbyDestinationStopSpinner, currentDestination);
        } else if (!nearbyDestinationStops.isEmpty()) {
            syncingSelectionControls = true;
            try {
                nearbyDestinationStopSpinner.setSelection(0);
            } finally {
                syncingSelectionControls = false;
            }
            if (selectionState != null) {
                selectionState.setDestinationStop(nearbyDestinationStops.get(0));
            }
        }
        boolean canChooseDestination = !nearbyDestinationStops.isEmpty();
        nearbyDestinationStopSpinner.setEnabled(canChooseDestination);
        nearbyPickDestinationStopButton.setEnabled(canChooseDestination);
        StopModel selectedDestination = selectedStop(nearbyDestinationStopSpinner);
        if (selectionState != null) {
            selectionState.setDestinationStop(selectedDestination);
        }
        syncingSelectionControls = false;
        nearbyConfirmCheckInButton.setEnabled(true);
        onSelectionStateChanged(true);
    }

    private boolean canConfirmNearbySelection() {
        RouteModel route = selectionState == null || selectionState.selectedRoute() == null
                ? selectedRoute
                : selectionState.selectedRoute();
        if (route == null || route.id == null) {
            return false;
        }
        StopModel boarding = selectedStop(nearbyBoardingStopSpinner);
        if (boarding == null || boarding.id == null) {
            boarding = currentBoardingStop();
        }
        StopModel destination = selectedStop(nearbyDestinationStopSpinner);
        if (destination == null || destination.id == null) {
            destination = currentDestinationStop();
        }
        return boarding != null && boarding.id != null
                && destination != null && destination.id != null;
    }

    private void showNearbyStopPicker(boolean boardingPicker) {
        List<StopModel> source = boardingPicker ? nearbyRouteStops : nearbyDestinationStops;
        if (source == null || source.isEmpty()) {
            toast(boardingPicker
                    ? "Hãy chọn chuyến trước để tải danh sách bến đi."
                    : "Hãy chọn bến đi trước để có danh sách bến xuống.");
            return;
        }
        String[] labels = new String[source.size()];
        for (int i = 0; i < source.size(); i++) {
            labels[i] = stopPickerLabel(source.get(i));
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(boardingPicker ? "Chọn bến đi" : "Chọn bến xuống")
                .setItems(labels, (dialog, which) -> {
                    StopModel stop = source.get(which);
                    if (boardingPicker) {
                        nearbyBoardingStop = stop;
                        if (selectionState != null) {
                            selectionState.setBoardingStop(stop);
                            persistSelectionState();
                        }
                        selectStopInSpinner(nearbyBoardingStopSpinner, stop);
                        updateNearbySelectionUi();
                        updateNearbyDestinationChoices();
                    } else {
                        selectStopInSpinner(nearbyDestinationStopSpinner, stop);
                        if (selectionState != null) {
                            selectionState.setDestinationStop(stop);
                            persistSelectionState();
                        }
                        nearbyConfirmCheckInButton.setEnabled(true);
                    }
                    onSelectionStateChanged(true);
                    bluetoothStatusText.setText("Đã chọn chuyến #" + safeLong(currentSelectedTripId())
                            + "\nBến đi: " + (nearbyBoardingStop == null ? getString(R.string.boarding_stop_not_selected) : name(nearbyBoardingStop))
                            + "\nBến xuống: " + name(selectedStop(nearbyDestinationStopSpinner))
                            + "\nDữ liệu chọn bến đã sẵn sàng cho AI và check-in.");
                })
                .show();
    }

    private void selectStopInSpinner(Spinner spinner, StopModel target) {
        if (spinner == null || target == null || target.id == null || spinner.getAdapter() == null) {
            return;
        }
        for (int i = 0; i < spinner.getAdapter().getCount(); i++) {
            Object item = spinner.getAdapter().getItem(i);
            if (item instanceof StopModel stop && target.id.equals(stop.id)) {
                spinner.setSelection(i);
                return;
            }
        }
    }

    private String stopPickerLabel(StopModel stop) {
        if (stop == null) {
            return "Chưa xác định";
        }
        String order = stop.stopOrder == null ? "" : stop.stopOrder + ". ";
        return order + name(stop);
    }

    private StopModel placeholderStop() {
        StopModel stop = new StopModel();
        stop.name = getString(R.string.boarding_stop_not_selected);
        return stop;
    }

    private void confirmNearbyCheckInSelection() {
        restoreSelectionStateIfNeeded();
        applyNearbySpinnerSelection();
        RouteModel route = selectionState == null || selectionState.selectedRoute() == null
                ? selectedRoute
                : selectionState.selectedRoute();
        if (route == null || route.id == null) {
            String message = "Hãy chọn tuyến trước.";
            bluetoothStatusText.setText(message);
            toast(message);
            return;
        }
        if (currentBoardingStop() == null || currentBoardingStop().id == null) {
            toast("Chưa chọn bến đi.");
            return;
        }
        if (currentDestinationStop() == null || currentDestinationStop().id == null) {
            toast("Vui lòng chọn bến xuống.");
            return;
        }
        if (selectionState != null) {
            selectionState.confirmLocalSelection(null);
        }
        persistSelectionState();
        onSelectionStateChanged(true);
        bluetoothStatusText.setText("Đã xác nhận tuyến #" + safeLong(route.id)
                + "\nBến đi: " + name(currentBoardingStop())
                + "\nBến xuống: " + name(currentDestinationStop())
                + "\nBấm Tìm chuyến gần tôi để phát BLE check-in.");
        toast("Đã xác nhận tuyến và bến đi/xuống trên máy.");
        updateBluetoothStatus();
        renderSelectedTripSummary();
    }

    private void applyNearbySpinnerSelection() {
        StopModel boarding = selectedStop(nearbyBoardingStopSpinner);
        StopModel destination = selectedStop(nearbyDestinationStopSpinner);
        if (boarding == null || boarding.id == null) {
            boarding = currentBoardingStop();
        }
        if (destination == null || destination.id == null) {
            destination = currentDestinationStop();
        }
        nearbyBoardingStop = boarding != null && boarding.id != null ? boarding : null;
        if (selectionState != null) {
            selectionState.setBoardingStop(nearbyBoardingStop);
            selectionState.setDestinationStop(destination != null && destination.id != null ? destination : null);
        }
    }

    private void clearNearbySelection(boolean keepBoardingText) {
        nearbySuggestedBoardingStop = null;
        nearbySuggestedDistanceMeters = null;
        nearbyAutoBoardingAllowed = false;
        nearbyBoardingStops.clear();
        nearbyRouteStops.clear();
        nearbyDestinationStops.clear();
        if (!keepBoardingText) {
            nearbyBoardingStop = null;
        }
        syncingSelectionControls = true;
        try {
            if (nearbyBoardingStopSpinner != null) {
                ArrayAdapter<StopModel> adapter = stopAdapter(nearbyBoardingStops);
                nearbyBoardingStopSpinner.setAdapter(adapter);
                nearbyBoardingStopSpinner.setEnabled(false);
            }
            if (nearbyPickBoardingStopButton != null) {
                nearbyPickBoardingStopButton.setEnabled(false);
            }
            if (nearbyDestinationStopSpinner != null) {
                ArrayAdapter<StopModel> adapter = stopAdapter(nearbyDestinationStops);
                nearbyDestinationStopSpinner.setAdapter(adapter);
                nearbyDestinationStopSpinner.setEnabled(false);
            }
            if (nearbyPickDestinationStopButton != null) {
                nearbyPickDestinationStopButton.setEnabled(false);
            }
        } finally {
            syncingSelectionControls = false;
        }
        if (nearbyConfirmCheckInButton != null) {
            nearbyConfirmCheckInButton.setEnabled(true);
        }
        updateNearbySelectionUi();
    }

    private void updateNearbySelectionUi() {
        if (nearbyBoardingStopText == null) {
            return;
        }
        StopModel boarding = currentBoardingStop();
        nearbyBoardingStopText.setText(boarding == null
                ? getString(R.string.boarding_stop_not_selected)
                : name(boarding));
    }

    private void prepareBluetoothCheckIn() {
        BoardingRequestModel request = currentBoardingRequest();
        Long routeId = currentSelectedRouteId();
        StopModel destination = currentDestinationStop();
        if (routeId == null || destination == null || destination.id == null) {
            toast("Vui lòng chọn tuyến và bến xuống trước khi check-in.");
            return;
        }
        if ((request == null || request.id == null)
                && (selectionState == null || !selectionState.isLocalSelectionConfirmed())) {
            toast("Hãy xác nhận bến đi/xuống trước khi phát BLE.");
            return;
        }
        String status = request != null && request.status != null
                ? request.status
                : (selectionState == null ? null : selectionState.checkInStatus());
        if ("BOARDED".equals(status) || "COMPLETED".equals(status)) {
            toast("Yêu cầu này đã được xác nhận lên xe, không gửi check-in lần nữa.");
            return;
        }
        BluetoothConnectionState state = bluetoothManager.getState();
        if (state == BluetoothConnectionState.PERMISSION_REQUIRED) {
            requestBluetoothPermission();
            return;
        }
        if (state == BluetoothConnectionState.DISABLED) {
            toast("Bluetooth đang tắt. Vui lòng bật Bluetooth để trình diễn check-in.");
            return;
        }
        if (state == BluetoothConnectionState.UNSUPPORTED) {
            toast("Thiết bị này không hỗ trợ Bluetooth.");
            return;
        }
        String identifier = request != null && request.bluetoothIdentifier != null
                ? request.bluetoothIdentifier
                : ensureLocalBluetoothIdentifier(routeId);
        Long requestId = request == null || request.id == null ? System.currentTimeMillis() : request.id;
        BoardingRequestModel localRequest = request;
        if (localRequest == null) {
            localRequest = new BoardingRequestModel();
            localRequest.id = requestId;
            localRequest.passenger = currentPassenger;
            localRequest.routeId = routeId;
            localRequest.boardingStop = copyStop(currentBoardingStop());
            localRequest.destinationStop = copyStop(destination);
            localRequest.status = "PENDING";
            localRequest.note = "LOCAL_BLE_CHECKIN";
            localRequest.bluetoothIdentifier = identifier;
            syncSelectedRequest(localRequest);
            addLocalCheckIn(localRequest);
        }
        BluetoothEvent event = bluetoothManager.createCheckInEvent(
                requestId,
                null,
                routeId,
                destination.id,
                destination.latitude == null ? null : destination.latitude.doubleValue(),
                destination.longitude == null ? null : destination.longitude.doubleValue(),
                identifier
        );
        boolean advertising = bluetoothManager.startAdvertising(event);
        publishFastBoardingSignal(routeId, destination.id);
        showLocalDropoffDistance(destination);
        updateBluetoothStatus();
        renderLocalCheckIns();
        toast((advertising ? "Đang phát BLE check-in" : "Chưa phát được BLE, hãy kiểm tra quyền/Bluetooth")
                + ": tuyến #" + safeLong(routeId));
    }

    private void publishFastBoardingSignal(Long routeId, Long destinationStopId) {
        if (routeId == null || destinationStopId == null || destroyed) {
            return;
        }
        // Fire this lightweight route/stop signal independently from BLE. It is
        // intentionally silent so a slow/failing backend never changes check-in UX.
        callSilent(
                RetrofitClient.api().publishFastBoardingSignal(
                        new FastBoardingSignalRequest(routeId, destinationStopId)
                ),
                ignored -> {
                }
        );
    }

    private void showLocalDropoffDistance(StopModel destination) {
        if (destination == null || destination.latitude == null || destination.longitude == null) {
            return;
        }
        Location location = currentLocation != null ? currentLocation : lastKnownLocation();
        if (location == null) {
            bluetoothStatusText.setText(bluetoothStatusText.getText()
                    + "\nChưa có GPS để tính khoảng cách tới bến xuống.");
            return;
        }
        float[] result = new float[1];
        Location.distanceBetween(
                location.getLatitude(),
                location.getLongitude(),
                destination.latitude.doubleValue(),
                destination.longitude.doubleValue(),
                result
        );
        int meters = Math.max(0, Math.round(result[0]));
        String line = meters <= 200
                ? "Bạn sắp tới bến xuống, còn " + meters + " m."
                : "Còn " + meters + " m nữa là tới bến xuống.";
        bluetoothStatusText.setText(bluetoothStatusText.getText() + "\n" + line);
    }

    private String ensureLocalBluetoothIdentifier(Long routeId) {
        String existing = selectionState == null ? null : selectionState.bluetoothIdentifier();
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        long passengerId = currentPassenger == null || currentPassenger.id == null ? 0L : currentPassenger.id;
        String identifier = "P" + passengerId + "R" + safeLong(routeId) + "D" + (System.currentTimeMillis() % 100000);
        if (selectionState != null) {
            selectionState.markLocalCheckIn(identifier);
            persistSelectionState();
        }
        return identifier;
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE},
                    REQUEST_BLUETOOTH_CONNECT
            );
        }
    }

    private void markSelectedNearbyTrip(TripModel selectedTrip) {
        if (nearbyTripsContainer == null || selectedTrip == null || selectedTrip.id == null) {
            return;
        }
        for (int i = 0; i < nearbyTripsContainer.getChildCount(); i++) {
            View child = nearbyTripsContainer.getChildAt(i);
            Object tag = child.getTag();
            if (tag instanceof Long tripId && tripId.equals(selectedTrip.id)) {
                child.setAlpha(1f);
            } else {
                child.setAlpha(0.55f);
            }
        }
    }

    private String bluetoothStateMessage(BluetoothConnectionState state) {
        if (state == BluetoothConnectionState.READY) {
            return "Bluetooth sẵn sàng. Tài xế vẫn là người xác nhận lên xe.";
        }
        if (state == BluetoothConnectionState.PERMISSION_REQUIRED) {
            return "Cần cấp quyền Bluetooth để gửi sự kiện check-in.";
        }
        if (state == BluetoothConnectionState.DISABLED) {
            return "Bluetooth đang tắt. Bật Bluetooth trước khi check-in.";
        }
        return "Thiết bị không hỗ trợ Bluetooth.";
    }

    private void startTrackingRefresh() {
        stopTrackingRefresh();
        BoardingRequestModel request = currentBoardingRequest();
        if (request != null
                && request.id != null
                && !"COMPLETED".equals(request.status)
                && !"CANCELLED".equals(request.status)) {
            handler.postDelayed(trackingRefreshRunnable, TRACKING_REFRESH_MS);
        }
    }

    private void stopTrackingRefresh() {
        handler.removeCallbacks(trackingRefreshRunnable);
    }

    private void renderMap(List<StopModel> routeStops, Long currentStopId, Long nextStopId, boolean fitCamera) {
        if (mapView == null || routeStops == null) {
            return;
        }
        mapView.getOverlays().clear();
        List<GeoPoint> points = new ArrayList<>();
        for (int index = 0; index < routeStops.size(); index++) {
            StopModel stop = routeStops.get(index);
            if (stop == null || stop.latitude == null || stop.longitude == null) {
                continue;
            }
            GeoPoint point = new GeoPoint(stop.latitude.doubleValue(), stop.longitude.doubleValue());
            points.add(point);
            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setTitle(name(stop));
            String label = stop.stopOrder == null ? "" : "Bến " + stop.stopOrder;
            if (stop.id != null && stop.id.equals(currentStopId)) {
                label = "Bến hiện tại";
            } else if (stop.id != null && stop.id.equals(nextStopId)) {
                label = "Bến tiếp theo";
            } else if (index == 0) {
                label = "Bến đầu tuyến";
            } else if (index == routeStops.size() - 1) {
                label = "Bến cuối tuyến";
            } else if (currentBoardingStop() != null
                    && stop.id != null
                    && stop.id.equals(currentBoardingStop().id)) {
                label = "Bến đi của bạn";
            } else if (currentDestinationStop() != null
                    && stop.id != null
                    && stop.id.equals(currentDestinationStop().id)) {
                label = "Bến xuống của bạn";
            }
            marker.setSnippet(label);
            marker.setIcon(markerIconFor(stop, index, routeStops.size(), currentStopId, nextStopId));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(marker);
        }
        addSelectedRoutePathOverlay();
        addBusMarkerIfAvailable();
        addPassengerLocationMarker();
        if (fitCamera) {
            pendingMapFit = true;
            fitMapToStopsIfReady();
        }
        mapView.invalidate();
    }

    private void fitMapToStopsIfReady() {
        if (mapView == null || currentTab != TAB_MAP) {
            pendingMapFit = true;
            return;
        }
        if (mapView.getWidth() <= 0 || mapView.getHeight() <= 0) {
            pendingMapFit = true;
            if (pendingMapFitAttempts < 8) {
                pendingMapFitAttempts++;
                mapView.postDelayed(this::fitMapToStopsIfReady, 80);
            }
            return;
        }
        List<GeoPoint> points = routePoints(stops);
        if (points.size() > 1) {
            pendingMapFit = false;
            pendingMapFitAttempts = 0;
            mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, 80);
        } else if (points.size() == 1) {
            pendingMapFit = false;
            pendingMapFitAttempts = 0;
            mapView.getController().setCenter(points.get(0));
            mapView.getController().setZoom(15.0);
        }
    }

    private List<GeoPoint> routePoints(List<StopModel> routeStops) {
        List<GeoPoint> points = new ArrayList<>();
        if (routeStops == null) {
            return points;
        }
        for (StopModel stop : routeStops) {
            if (stop != null && stop.latitude != null && stop.longitude != null) {
                points.add(new GeoPoint(stop.latitude.doubleValue(), stop.longitude.doubleValue()));
            }
        }
        return points;
    }

    private void centerMapOnRoute() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION
            );
            return;
        }
        currentLocation = lastKnownLocation();
        if (currentLocation != null) {
            renderMap(stops, null, null, false);
            GeoPoint mine = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
            mapView.getController().setCenter(mine);
            mapView.getController().setZoom(16.0);
            toast("Đã đưa bản đồ về vị trí của bạn.");
            return;
        }
        if (stops.isEmpty()) {
            mapView.getController().setCenter(new GeoPoint(10.7769, 106.7009));
            mapView.getController().setZoom(13.0);
            toast("Chưa có vị trí hiện tại, tạm đưa bản đồ về trung tâm mặc định.");
            return;
        }
        renderMap(stops, null, null, true);
        toast("Chưa có vị trí hiện tại, tạm đưa bản đồ về tuyến đang chọn.");
    }

    private Location lastKnownLocation() {
        try {
            LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) {
                return null;
            }
            Location gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (gps == null) {
                return network;
            }
            if (network == null) {
                return gps;
            }
            return gps.getTime() >= network.getTime() ? gps : network;
        } catch (SecurityException ignored) {
            return null;
        }
    }

    private void updateSelectedTripMap(boolean fitCamera) {
        List<StopModel> routeStops = selectedRouteStopsForMap();
        if (!routeStops.isEmpty()) {
            renderMap(routeStops, null, null, false);
        }
        StopModel boarding = currentBoardingStop();
        StopModel destination = currentDestinationStop();
        if (!validStopCoordinates(boarding) || !validStopCoordinates(destination)) {
            selectedRoutePathKey = null;
            selectedRoutePathInFlightKey = null;
            selectedRoutePathPoints.clear();
            if (fitCamera && !routeStops.isEmpty()) {
                fitMapToStopsIfReady();
            }
            return;
        }
        String key = currentSelectedTripId() + ":"
                + boarding.id + ":"
                + destination.id + ":"
                + boarding.latitude + ":"
                + boarding.longitude + ":"
                + destination.latitude + ":"
                + destination.longitude;
        if (key.equals(selectedRoutePathKey) && selectedRoutePathPoints.size() > 1) {
            renderMap(routeStops, null, null, false);
            fitMapToSelectedSegment(fitCamera);
            return;
        }
        if (key.equals(selectedRoutePathInFlightKey)) {
            fitMapToSelectedSegment(fitCamera);
            return;
        }
        selectedRoutePathKey = key;
        selectedRoutePathInFlightKey = key;
        selectedRoutePathPoints.clear();
        renderMap(routeStops, null, null, false);
        fitMapToSelectedSegment(fitCamera);
        callSilent(RetrofitClient.api().routePath(
                boarding.latitude,
                boarding.longitude,
                destination.latitude,
                destination.longitude
        ), data -> {
            if (key.equals(selectedRoutePathInFlightKey)) {
                selectedRoutePathInFlightKey = null;
            }
            if (!key.equals(selectedRoutePathKey) || data == null || data.points == null) {
                return;
            }
            selectedRoutePathPoints.clear();
            for (RoutePathPointModel point : data.points) {
                if (point != null && point.latitude != null && point.longitude != null) {
                    selectedRoutePathPoints.add(new GeoPoint(
                            point.latitude.doubleValue(),
                            point.longitude.doubleValue()
                    ));
                }
            }
            renderMap(selectedRouteStopsForMap(), null, null, false);
            fitMapToSelectedSegment(fitCamera);
            mapStatusText.setText("Đã vẽ đoạn đường thực tế: " + name(boarding)
                    + " -> " + name(destination)
                    + "\nNguồn routing: " + safe(data.provider));
        }, () -> {
            if (key.equals(selectedRoutePathInFlightKey)) {
                selectedRoutePathInFlightKey = null;
            }
            if (key.equals(selectedRoutePathKey) && selectedRoutePathPoints.isEmpty()) {
                mapStatusText.setText("Đã đánh dấu bến đi/bến xuống nhưng chưa tải được đường giao thông thực tế.");
            }
        });
    }

    private List<StopModel> selectedRouteStopsForMap() {
        if (selectionState != null && selectionState.hasStops()) {
            return new ArrayList<>(selectionState.routeStops());
        }
        return new ArrayList<>(stops);
    }

    private boolean validStopCoordinates(StopModel stop) {
        return stop != null && stop.id != null && stop.latitude != null && stop.longitude != null;
    }

    private void addSelectedRoutePathOverlay() {
        if (mapView == null || selectedRoutePathPoints.size() < 2) {
            return;
        }
        Polyline line = new Polyline(mapView);
        line.setPoints(new ArrayList<>(selectedRoutePathPoints));
        line.setColor(getColor(R.color.smartbus_success));
        line.setWidth(8f);
        line.setGeodesic(false);
        mapView.getOverlays().add(0, line);
    }

    private void fitMapToSelectedSegment(boolean fitCamera) {
        if (!fitCamera || mapView == null || currentTab != TAB_MAP) {
            return;
        }
        List<GeoPoint> points = new ArrayList<>(selectedRoutePathPoints);
        StopModel boarding = currentBoardingStop();
        StopModel destination = currentDestinationStop();
        if (validStopCoordinates(boarding)) {
            points.add(new GeoPoint(boarding.latitude.doubleValue(), boarding.longitude.doubleValue()));
        }
        if (validStopCoordinates(destination)) {
            points.add(new GeoPoint(destination.latitude.doubleValue(), destination.longitude.doubleValue()));
        }
        fitMapToPoints(points, 90);
    }

    private void fitMapToPoints(List<GeoPoint> points, int padding) {
        if (mapView == null || points == null || points.isEmpty()) {
            return;
        }
        if (mapView.getWidth() <= 0 || mapView.getHeight() <= 0) {
            mapView.postDelayed(() -> fitMapToPoints(points, padding), 80);
            return;
        }
        if (points.size() > 1) {
            mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, padding);
        } else {
            mapView.getController().setCenter(points.get(0));
            mapView.getController().setZoom(15.0);
        }
    }

    private void addBusMarkerIfAvailable() {
        TripModel trip = currentSelectedTrip();
        if (trip == null || trip.currentLatitude == null || trip.currentLongitude == null) {
            return;
        }
        Marker bus = new Marker(mapView);
        bus.setPosition(new GeoPoint(
                trip.currentLatitude.doubleValue(),
                trip.currentLongitude.doubleValue()
        ));
        bus.setTitle("Xe buýt của bạn");
        bus.setSnippet("Vị trí xe theo dữ liệu backend");
        bus.setIcon(createPinDrawable(getColor(R.color.smartbus_warning), "X"));
        bus.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(bus);
    }

    private void addPassengerLocationMarker() {
        if (currentLocation == null) {
            return;
        }
        Marker mine = new Marker(mapView);
        mine.setPosition(new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude()));
        mine.setTitle("Vị trí của tôi");
        mine.setSnippet("Vị trí gần nhất trên thiết bị");
        mine.setIcon(createPinDrawable(getColor(R.color.smartbus_success), "Tôi"));
        mine.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        mapView.getOverlays().add(mine);
    }

    private Drawable markerIconFor(StopModel stop, int index, int total, Long currentStopId, Long nextStopId) {
        if (stop.id != null && stop.id.equals(currentStopId)) {
            return createPinDrawable(getColor(R.color.smartbus_success), "HT");
        }
        if (stop.id != null && stop.id.equals(nextStopId)) {
            return createPinDrawable(getColor(R.color.smartbus_primary), "TT");
        }
        StopModel boardingStop = currentBoardingStop();
        if (boardingStop != null
                && stop.id != null
                && stop.id.equals(boardingStop.id)) {
            return createPinDrawable(getColor(R.color.smartbus_warning), "L");
        }
        StopModel destinationStop = currentDestinationStop();
        if (destinationStop != null
                && stop.id != null
                && stop.id.equals(destinationStop.id)) {
            return createPinDrawable(getColor(R.color.smartbus_error), "X");
        }
        if (index == 0) {
            return createPinDrawable(getColor(R.color.smartbus_success), "Đ");
        }
        if (index == total - 1) {
            return createPinDrawable(getColor(R.color.smartbus_error), "C");
        }
        return createPinDrawable(getColor(R.color.smartbus_primary_dark), String.valueOf(index + 1));
    }

    private Drawable createPinDrawable(int color, String text) {
        int width = 72;
        int height = 90;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();
        path.moveTo(width / 2f, height - 4f);
        path.cubicTo(width * 0.15f, height * 0.55f, 8f, height * 0.38f, 8f, height * 0.25f);
        path.cubicTo(8f, 8f, width - 8f, 8f, width - 8f, height * 0.25f);
        path.cubicTo(width - 8f, height * 0.38f, width * 0.85f, height * 0.55f, width / 2f, height - 4f);
        paint.setColor(color);
        canvas.drawPath(path, paint);
        paint.setColor(getColor(R.color.white));
        canvas.drawCircle(width / 2f, 28f, 21f, paint);
        paint.setColor(color);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(text.length() > 2 ? 17f : 20f);
        canvas.drawText(text, width / 2f, 35f, paint);
        return new BitmapDrawable(getResources(), bitmap);
    }

    private void renderStopsTimeline(Long currentStopId, Long nextStopId) {
        stopsContainer.removeAllViews();
        if (stops.isEmpty()) {
            addText(stopsContainer, "Tuyến này chưa có dữ liệu bến.");
            return;
        }
        for (StopModel stop : stops) {
            if (stop == null) {
                continue;
            }
            String prefix = stop.stopOrder == null ? "• " : stop.stopOrder + ". ";
            String state = "";
            int color = R.color.smartbus_text_primary;
            if (stop.id != null && stop.id.equals(currentStopId)) {
                state = "  · Bến hiện tại";
                color = R.color.smartbus_success;
            } else if (stop.id != null && stop.id.equals(nextStopId)) {
                state = "  · Bến tiếp theo";
                color = R.color.smartbus_primary;
            }
            stopsContainer.addView(label(prefix + name(stop) + state, 15f, stop.id != null
                    && (stop.id.equals(currentStopId) || stop.id.equals(nextStopId)), color));
        }
    }

    private void updateHomeSummary() {
        if (homeGreetingText != null && currentPassenger != null) {
            homeGreetingText.setText("Xin chào, " + currentPassenger.fullName);
        }
        if (homeSummaryText == null) {
            return;
        }
        RouteModel route = selectionState == null || selectionState.selectedRoute() == null
                ? selectedRoute
                : selectionState.selectedRoute();
        String routeLine = route == null
                ? "Chưa chọn tuyến. Mở tab Tuyến xe để tìm chuyến phù hợp."
                : "Tuyến đang chọn: " + routeLabel(route);
        BoardingRequestModel request = currentBoardingRequest();
        String requestLine = request == null
                ? selectedTripSummary()
                : "Chuyến của tôi: yêu cầu #" + request.id
                + " · " + statusLabel(request.status)
                + "\nChuyến #" + safeLong(currentSelectedTripId())
                + "\nBến lên: " + name(currentBoardingStop())
                + "\nBến xuống: " + name(currentDestinationStop());
        homeSummaryText.setText(routeLine + "\n\n" + requestLine);
    }

    private String selectedTripSummary() {
        TripModel trip = currentSelectedTrip();
        if (trip == null || trip.id == null) {
            RouteModel route = currentSelectedRoute();
            if (route == null || route.id == null) {
                return "Chưa có tuyến đang chọn.";
            }
            return "Đã chọn tuyến #" + safeLong(route.id)
                    + "\nBến lên: " + (currentBoardingStop() == null
                    ? getString(R.string.boarding_stop_not_selected) : name(currentBoardingStop()))
                    + "\nBến xuống: " + name(currentDestinationStop())
                    + "\n" + (selectionState != null && selectionState.isLocalSelectionConfirmed()
                    ? "Đã xác nhận bến đi/xuống."
                    : "Chưa xác nhận bến đi/xuống.");
        }
        StopModel boarding = currentBoardingStop();
        StopModel destination = currentDestinationStop();
        return "Đã chọn chuyến #" + safeLong(trip.id)
                + "\nBến lên: " + (boarding == null ? getString(R.string.boarding_stop_not_selected) : name(boarding))
                + "\nBến xuống: " + name(destination)
                + "\nChưa tạo yêu cầu check-in.";
    }

    private void sendAiQuestion(String question) {
        if (question == null || question.isBlank()) {
            toast("Vui lòng nhập câu hỏi cho trợ lý AI.");
            return;
        }
        Long tripId = currentSelectedTripId();
        if (tripId == null) {
            toast("Hãy chọn chuyến trước để AI có dữ liệu backend trả lời.");
            return;
        }
        String trimmed = question.trim();
        addAiUserMessage(trimmed);
        aiQuestionInput.setText("");
        setAiLoading(true);
        call(RetrofitClient.api().aiChat(new AiAssistantRequest(tripId, trimmed, buildPassengerAiClientContext())), data -> {
            setAiLoading(false);
            addAiAssistantMessage(data.answer == null || data.answer.isBlank()
                    ? "Backend chưa trả về câu trả lời AI."
                    : data.answer);
        }, () -> setAiLoading(false));
    }

    private void requestAiSummary() {
        Long tripId = currentSelectedTripId();
        if (tripId == null) {
            toast("Hãy chọn chuyến trước để AI tóm tắt hành trình.");
            return;
        }
        addAiUserMessage("Tóm tắt hành trình của tôi");
        setAiLoading(true);
        call(RetrofitClient.api().aiSummary(new AiSummaryRequest(tripId, buildPassengerAiClientContext())), data -> {
            setAiLoading(false);
            addAiAssistantMessage(data.answer == null || data.answer.isBlank()
                    ? "Backend chưa trả về tóm tắt."
                    : data.answer);
        }, () -> setAiLoading(false));
    }

    private Long currentSelectedTripId() {
        TripModel trip = currentSelectedTrip();
        return trip == null ? null : trip.id;
    }

    private RouteModel currentSelectedRoute() {
        restoreSelectionStateIfNeeded();
        if (selectionState != null && selectionState.selectedRoute() != null) {
            return selectionState.selectedRoute();
        }
        return selectedRoute;
    }

    private Long currentSelectedRouteId() {
        RouteModel route = currentSelectedRoute();
        return route == null ? null : route.id;
    }

    private Map<String, Object> buildPassengerAiClientContext() {
        Map<String, Object> context = new LinkedHashMap<>();
        TripModel trip = currentSelectedTrip();
        context.put("app", "smartbus-passenger");
        context.put("currentTab", currentTab);
        RouteModel route = selectionState == null || selectionState.selectedRoute() == null
                ? selectedRoute
                : selectionState.selectedRoute();
        context.put("selectedRouteId", selectionState == null ? (trip == null ? null : trip.routeId) : selectionState.selectedRouteId());
        context.put("selectedRouteLabel", route == null ? null : routeLabel(route));
        context.put("selectedTripId", currentSelectedTripId());

        StopModel selectedBoarding = currentBoardingStop();
        StopModel selectedDestination = currentDestinationStop();
        putStopContext(context, "selectedBoardingStop", selectedBoarding);
        putStopContext(context, "selectedDestinationStop", selectedDestination);
        putStopContext(context, "nearbyStop", nearbySuggestedBoardingStop);
        context.put("nearbyStopDistanceMeters", nearbySuggestedDistanceMeters);
        context.put("autoBoardingAllowed", nearbyAutoBoardingAllowed);

        Location location = currentLocation != null ? currentLocation : lastKnownLocation();
        if (location != null) {
            context.put("currentLatitude", location.getLatitude());
            context.put("currentLongitude", location.getLongitude());
            context.put("currentAccuracyMeters", location.hasAccuracy() ? location.getAccuracy() : null);
        }
        BoardingRequestModel request = currentBoardingRequest();
        if (request != null) {
            context.put("boardingRequestId", request.id);
            context.put("boardingRequestStatus", request.status);
            context.put("bluetoothIdentifier", request.bluetoothIdentifier);
        } else if (selectionState != null) {
            context.put("boardingRequestStatus", selectionState.checkInStatus());
            context.put("bluetoothIdentifier", selectionState.bluetoothIdentifier());
        }
        return context;
    }

    private boolean validStop(StopModel stop) {
        return stop != null && stop.id != null;
    }

    private void putStopContext(Map<String, Object> context, String prefix, StopModel stop) {
        context.put(prefix + "Id", stop == null ? null : stop.id);
        context.put(prefix + "Name", stop == null ? null : name(stop));
        context.put(prefix + "Order", stop == null ? null : stop.stopOrder);
        context.put(prefix + "Latitude", stop == null ? null : stop.latitude);
        context.put(prefix + "Longitude", stop == null ? null : stop.longitude);
    }

    private void setAiLoading(boolean loading) {
        aiProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        aiSendButton.setEnabled(!loading);
        aiSummaryButton.setEnabled(!loading);
        aiWhereBusButton.setEnabled(!loading);
        aiStopsButton.setEnabled(!loading);
    }

    private void addAiUserMessage(String message) {
        addAiMessage("Bạn", message, R.color.smartbus_primary);
    }

    private void addAiAssistantMessage(String message) {
        addAiMessage("SmartBus AI", message, R.color.smartbus_success);
    }

    private void addAiMessage(String sender, String message, int colorRes) {
        TextView text = new TextView(this);
        text.setText(sender + ": " + message);
        text.setTextColor(getColor(R.color.smartbus_text_primary));
        text.setTextSize(14f);
        text.setPadding(12, 10, 12, 10);
        text.setBackgroundColor(getColor(R.color.smartbus_background));
        text.setCompoundDrawablePadding(8);
        text.setTextAlignment(View.TEXT_ALIGNMENT_TEXT_START);
        text.setTypeface(text.getTypeface(), sender.equals("Bạn") ? Typeface.BOLD : Typeface.NORMAL);
        text.setTextColor(getColor(colorRes));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 6, 0, 0);
        aiMessagesContainer.addView(text, params);
    }

    private StopModel selectedStop(Spinner spinner) {
        Object item = spinner.getSelectedItem();
        return item instanceof StopModel stop ? stop : null;
    }

    private ArrayAdapter<StopModel> stopAdapter(List<StopModel> values) {
        ArrayAdapter<StopModel> adapter = new ArrayAdapter<>(
                this,
                R.layout.item_stop_spinner,
                values
        );
        adapter.setDropDownViewResource(R.layout.item_stop_spinner_dropdown);
        return adapter;
    }

    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private PassengerModel cachedPassenger() {
        String token = preferences.getString(KEY_TOKEN, null);
        String fullName = preferences.getString(KEY_PASSENGER_NAME, null);
        if (token == null || token.isBlank() || fullName == null || fullName.isBlank()) {
            return null;
        }
        PassengerModel passenger = new PassengerModel();
        long id = preferences.getLong(KEY_PASSENGER_ID, -1L);
        passenger.id = id > 0 ? id : null;
        passenger.fullName = fullName;
        passenger.phoneNumber = preferences.getString(KEY_PASSENGER_PHONE, "");
        passenger.username = preferences.getString(KEY_PASSENGER_USERNAME, "");
        return passenger;
    }

    private void savePassenger(PassengerModel passenger) {
        if (passenger == null) {
            return;
        }
        SharedPreferences.Editor editor = preferences.edit();
        putPassenger(editor, passenger);
        editor.apply();
    }

    private void putPassenger(SharedPreferences.Editor editor, PassengerModel passenger) {
        if (passenger.id != null) {
            editor.putLong(KEY_PASSENGER_ID, passenger.id);
        } else {
            editor.remove(KEY_PASSENGER_ID);
        }
        editor.putString(KEY_PASSENGER_NAME, safe(passenger.fullName));
        editor.putString(KEY_PASSENGER_PHONE, safe(passenger.phoneNumber));
        editor.putString(KEY_PASSENGER_USERNAME, safe(passenger.username));
    }

    private String name(StopModel stop) {
        return stop == null || safe(stop.name).isBlank() ? "Chưa xác định" : stop.name;
    }

    private String routeLabel(RouteModel route) {
        if (route == null) {
            return "Chưa xác định";
        }
        String code = safe(route.code).isBlank() ? "Tuyến" : route.code;
        String name = safe(route.name).isBlank() ? "chưa đặt tên" : route.name;
        return code + " · " + name;
    }

    private int clampProgress(Integer value) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, Math.min(100, value));
    }

    private String distanceLabel(Double meters) {
        if (meters == null) {
            return "chưa xác định";
        }
        if (meters >= 1000d) {
            return String.format(java.util.Locale.US, "%.1f km", meters / 1000d);
        }
        return Math.round(meters) + " m";
    }

    private String safe(Integer value) {
        return value == null ? "0" : String.valueOf(value);
    }

    private String safeLong(Long value) {
        return value == null ? "?" : String.valueOf(value);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String statusLabel(String status) {
        if ("PENDING".equals(status)) {
            return "Đã đăng ký, chờ xe";
        }
        if ("CONFIRMED".equals(status)) {
            return "Đã xác nhận";
        }
        if ("BOARDED".equals(status)) {
            return "Đã lên xe";
        }
        if ("COMPLETED".equals(status)) {
            return "Đã hoàn thành";
        }
        if ("CANCELLED".equals(status)) {
            return "Đã hủy";
        }
        if ("IN_PROGRESS".equals(status)) {
            return "Đang chạy";
        }
        return status == null ? "Chưa xác định" : status;
    }

    private void addText(LinearLayout container, String value) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(getColor(R.color.smartbus_text_primary));
        text.setTextSize(15f);
        text.setPadding(0, 10, 0, 10);
        container.addView(text);
    }

    private TextView label(String value, float size, boolean bold, int colorRes) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextColor(getColor(colorRes));
        text.setTextSize(size);
        text.setPadding(0, 8, 0, 8);
        if (bold) {
            text.setTypeface(text.getTypeface(), android.graphics.Typeface.BOLD);
        }
        return text;
    }

    private void toast(String message) {
        if (!destroyed && rootShell != null) {
            Snackbar.make(rootShell, message, Snackbar.LENGTH_LONG).show();
        }
    }

    private <T> void call(Call<ApiResponse<T>> call, Success<T> success) {
        call(call, success, null, true);
    }

    private <T> void call(Call<ApiResponse<T>> call, Success<T> success, Runnable finished) {
        call(call, success, finished, true);
    }

    private <T> void callPublic(Call<ApiResponse<T>> call, Success<T> success, Runnable finished) {
        call(call, success, finished, false, true);
    }

    private <T> void callSilent(Call<ApiResponse<T>> call, Success<T> success) {
        call(call, success, null, false, false);
    }

    private <T> void callSilent(Call<ApiResponse<T>> call, Success<T> success, Runnable finished) {
        call(call, success, finished, false, false);
    }

    private <T> void call(Call<ApiResponse<T>> call, Success<T> success, Runnable finished, boolean requiresSession) {
        call(call, success, finished, requiresSession, true);
    }

    private <T> void call(
            Call<ApiResponse<T>> call,
            Success<T> success,
            Runnable finished,
            boolean requiresSession,
            boolean showErrors
    ) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(Call<ApiResponse<T>> call, Response<ApiResponse<T>> response) {
                if (destroyed) {
                    return;
                }
                if (finished != null) {
                    finished.run();
                }
                ApiResponse<T> body = response.body();
                if (response.isSuccessful() && body != null && body.success && body.data != null) {
                    success.accept(body.data);
                } else if (response.isSuccessful() && body != null && body.success) {
                    if (showErrors) {
                        toast(body.message != null ? body.message : "Backend chưa trả dữ liệu cho thao tác này.");
                    }
                } else if (response.code() == 401) {
                    if (requiresSession && showErrors) {
                        toast("Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.");
                        clearExpiredSession();
                    } else if (showErrors) {
                        toast("Sai tài khoản hoặc mật khẩu. Vui lòng kiểm tra lại.");
                    }
                } else if (response.code() == 400 && showErrors) {
                    toast(body != null && body.message != null ? body.message : "Dữ liệu chưa hợp lệ. Vui lòng kiểm tra lại.");
                } else if (response.code() == 403 && showErrors) {
                    toast("Bạn không có quyền thực hiện thao tác này.");
                } else if (response.code() == 409 && showErrors) {
                    toast(body != null && body.message != null ? body.message : "Tài khoản hoặc yêu cầu đã tồn tại.");
                } else if (showErrors) {
                    toast(body != null && body.message != null ? body.message : "Không thể tải dữ liệu từ backend.");
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<T>> call, Throwable throwable) {
                if (destroyed || call.isCanceled()) {
                    return;
                }
                if (finished != null) {
                    finished.run();
                }
                if (showErrors) {
                    toast(networkErrorMessage(throwable));
                }
            }
        });
    }

    private String networkErrorMessage(Throwable throwable) {
        if (throwable instanceof SocketTimeoutException) {
            return "Backend đang khởi động hoặc phản hồi chậm. Vui lòng thử lại sau vài giây.";
        }
        if (throwable instanceof UnknownHostException) {
            return "Không có Internet hoặc không tìm thấy máy chủ SmartBus.";
        }
        if (throwable instanceof ConnectException) {
            return "Không thể kết nối backend SmartBus. Vui lòng thử lại sau.";
        }
        return "Lỗi kết nối backend: " + throwable.getMessage();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_CONNECT) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Continue the pending BLE action after the runtime permission dialog.
                prepareBluetoothCheckIn();
            } else {
                toast("Chưa có quyền Bluetooth nên chưa thể check-in gần xe.");
            }
            updateBluetoothStatus();
        } else if (requestCode == REQUEST_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (currentTab == TAB_MAP) {
                    centerMapOnRoute();
                } else {
                    findNearbyTripsForCheckIn();
                }
            } else {
                toast("Chưa có quyền vị trí nên chưa thể hiển thị vị trí của bạn.");
            }
        }
    }

    private interface Success<T> {
        void accept(T data);
    }
}
