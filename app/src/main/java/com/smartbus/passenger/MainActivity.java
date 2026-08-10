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
        dataRepository = new PassengerDataRepository(PassengerDatabase.getInstance(this), dataExecutor);
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
        checkInButton.setOnClickListener(v -> {
            if (currentBoardingRequest() != null && currentBoardingRequest().id != null) {
                prepareBluetoothCheckIn();
            } else if (selectionState != null && selectionState.hasTrip()) {
                confirmSelectedTripCheckIn();
            } else {
                findNearbyTripsForCheckIn();
            }
        });
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
                if (syncingSelectionControls) {
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
                if (syncingSelectionControls) {
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
                if (syncingSelectionControls) {
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
                if (syncingSelectionControls) {
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
        if (selectionState == null || !selectionState.hasTrip()) {
            return;
        }
        selectedRequest = selectionState.boardingRequest();
        syncSelectedRouteFromTrip(selectionState.selectedTrip());
        onSelectionStateChanged(false);
        startTrackingRefresh();
    }

    private void logout() {
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
        RetrofitClient.clearToken();
        preferences.edit().clear().apply();
        currentPassenger = null;
        selectedRequest = null;
        if (selectedTripStore != null) {
            selectedTripStore.clear();
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
        card.setStrokeColor(route == selectedRoute ? getColor(R.color.smartbus_primary) : getColor(R.color.smartbus_background));
        card.setStrokeWidth(route == selectedRoute ? 3 : 1);
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
        action.setText(route == selectedRoute ? "Đang chọn tuyến này" : "Xem tuyến và chọn bến");
        action.setTextColor(route == selectedRoute ? getColor(R.color.white) : getColor(R.color.smartbus_primary));
        action.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                route == selectedRoute ? getColor(R.color.smartbus_primary) : getColor(R.color.smartbus_surface)));
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
        if (selectionState != null) {
            selectionState.setRoute(route);
            persistSelectionState();
        }
        tripsContainer.removeAllViews();
        routeDetailText.setText(routeLabel(route) + "\n" + safe(route.description));
        renderRoutes(text(routeSearchInput));
        dataRepository.loadStops(route.id, (data, fromCache) -> runOnUiThread(() -> {
            stops.clear();
            for (StopModel stop : data) {
                if (stop != null) {
                    stops.add(stop);
                }
            }
            if (selectionState != null) {
                selectionState.setRoute(route);
                selectionState.setRouteStops(stops);
                persistSelectionState();
            }
            ArrayAdapter<StopModel> adapter = stopAdapter(stops);
            boardingStopSpinner.setAdapter(adapter);
            destinationStopSpinner.setAdapter(adapter);
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
        dataRepository.findTrips(selectedRoute.id, boarding.id, destination.id, (data, fromCache) -> runOnUiThread(() -> {
            tripsContainer.removeAllViews();
            if (data.isEmpty()) {
                addText(tripsContainer, fromCache
                        ? "Offline: chưa có chuyến đang hoạt động đã lưu cho tuyến này."
                        : "Chưa có chuyến đang hoạt động phù hợp. Vui lòng thử lại sau.");
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
            toast("Đã chọn chuyến #" + safeLong(trip.id) + ". Bạn có thể hỏi AI hoặc xác nhận check-in.");
            showTab(TAB_MY_TRIP);
        });
        inner.addView(select);
        return card;
    }

    private void createRequest(TripModel trip, StopModel boarding, StopModel destination) {
        if (trip == null || trip.id == null || boarding == null || boarding.id == null
                || destination == null || destination.id == null) {
            toast("Chuyến hoặc bến đang thiếu dữ liệu từ backend.");
            return;
        }
        CreateBoardingRequest request = new CreateBoardingRequest(
                trip.id,
                boarding.id,
                destination.id,
                name(boarding) + " -> " + name(destination)
        );
        syncSelectedTrip(trip, boarding, destination);
        call(RetrofitClient.api().createBoardingRequest(request), data -> {
            toast("Đã gửi yêu cầu. Mã Bluetooth: " + data.bluetoothIdentifier);
            syncSelectedRequest(data);
            loadRequests();
            track(data, true);
            updateBluetoothStatus();
            showTab(TAB_MY_TRIP);
            startTrackingRefresh();
        });
    }

    private void loadRequests() {
        call(RetrofitClient.api().myRequests(), data -> {
            requestsContainer.removeAllViews();
            if (data.isEmpty()) {
                addText(requestsContainer, getString(R.string.empty_default));
                syncSelectedRequest(null);
                updateBluetoothStatus();
                return;
            }
            syncSelectedRequest(selectedRequestFrom(data));
            updateBluetoothStatus();
            for (BoardingRequestModel request : data) {
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
                addText(inner, "Yêu cầu #" + request.id + " · " + statusLabel(request.status)
                        + "\nBến lên: " + name(request.boardingStop)
                        + "\nBến xuống: " + name(request.destinationStop)
                        + "\nBluetooth: " + safe(request.bluetoothIdentifier));
                MaterialButton track = new MaterialButton(this);
                track.setText("Theo dõi");
                track.setTextColor(getColor(R.color.white));
                track.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_primary)));
                track.setOnClickListener(v -> track(request, true));
                inner.addView(track);
                if ("PENDING".equals(request.status) || "CONFIRMED".equals(request.status)) {
                    MaterialButton cancel = new MaterialButton(this);
                    cancel.setText("Hủy yêu cầu");
                    cancel.setTextColor(getColor(R.color.smartbus_error));
                    cancel.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_surface)));
                    cancel.setStrokeColor(android.content.res.ColorStateList.valueOf(getColor(R.color.smartbus_error)));
                    cancel.setStrokeWidth(2);
                    cancel.setOnClickListener(v -> cancelRequest(request));
                    inner.addView(cancel);
                }
                requestsContainer.addView(card);
            }
        });
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
        }
        return firstTrackableRequest(requests);
    }

    private void syncSelectedRequest(BoardingRequestModel request) {
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
        if (selectionState == null || selectedTripStore == null || trip == null || trip.id == null) {
            return;
        }
        selectionState.selectTrip(trip);
        selectionState.setBoardingStop(boarding);
        selectionState.setDestinationStop(destination);
        selectedTripStore.persist(selectionState);
        syncSelectedRouteFromTrip(selectionState.selectedTrip());
        onSelectionStateChanged(true);
    }

    private TripModel currentSelectedTrip() {
        return selectionState == null ? null : selectionState.selectedTrip();
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
        return selectionState == null ? null : selectionState.boardingStop();
    }

    private StopModel currentDestinationStop() {
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
        if (selectedTripStore != null && selectionState != null) {
            selectedTripStore.persist(selectionState);
        }
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
            nearbyConfirmCheckInButton.setEnabled(selectionState != null && selectionState.canCreateBoardingRequest());
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
            trackingText.setText("Chưa chọn chuyến. Vào tab Tuyến xe để chọn tuyến, bến đi và bến xuống.");
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
        dataRepository.loadStops(routeId, (data, fromCache) -> runOnUiThread(() -> {
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
                boolean readyToCreateRequest = boarding != null
                        && boarding.id != null
                        && destination != null
                        && destination.id != null;
                bluetoothStatusText.setText("Đã chọn chuyến #" + safeLong(pendingTrip.id)
                        + "\nBến đi: " + (boarding == null ? getString(R.string.boarding_stop_not_selected) : name(boarding))
                        + "\nBến xuống: " + name(destination)
                        + "\nCheck-in Bluetooth đã sẵn sàng khi đủ bến đi/bến xuống.");
                checkInButton.setText(R.string.nearby_confirm_checkin);
                checkInButton.setEnabled(readyToCreateRequest);
                nearbyConfirmCheckInButton.setText("Chọn chuyến này");
            } else {
                bluetoothStatusText.setText("Bấm tìm chuyến gần tôi để SmartBus chọn bến gần vị trí hiện tại nhất.");
                checkInButton.setText(R.string.bluetooth_checkin_action);
                checkInButton.setEnabled(true);
                nearbyConfirmCheckInButton.setText("Chọn chuyến này");
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
        bluetoothStatusText.setText("Mã check-in: " + identifier + "\n" + bluetoothStateMessage(state));
        boolean canCheckIn = "PENDING".equals(request.status) || "CONFIRMED".equals(request.status);
        checkInButton.setText("Chuẩn bị check-in");
        checkInButton.setEnabled(canCheckIn && (state == BluetoothConnectionState.READY
                || state == BluetoothConnectionState.PERMISSION_REQUIRED));
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
        call(RetrofitClient.api().nearbyActiveTrips(
                currentLocation.getLatitude(),
                currentLocation.getLongitude()
        ), this::renderNearbyTrips);
    }

    private void renderNearbyTrips(NearbyActiveTripsModel data) {
        nearbyTripsContainer.removeAllViews();
        if (data == null || data.boardingStop == null || data.suggestedDestinationStop == null) {
            bluetoothStatusText.setText("Backend chưa xác định được bến gần nhất để check-in.");
            return;
        }
        selectedRoute = data.route;
        if (selectionState != null) {
            selectionState.setRoute(data.route);
            persistSelectionState();
        }
        nearbySuggestedBoardingStop = data.boardingStop;
        nearbySuggestedDistanceMeters = data.distanceMeters;
        nearbyAutoBoardingAllowed = data.distanceMeters != null
                && data.distanceMeters <= AUTO_BOARDING_STOP_RADIUS_METERS;
        nearbyBoardingStop = nearbyAutoBoardingAllowed ? data.boardingStop : null;
        updateNearbySelectionUi();
        bluetoothStatusText.setText((nearbyAutoBoardingAllowed ? "Đã tự chọn bến đi: " : "Bến gần nhất gợi ý: ")
                + name(data.boardingStop)
                + "\nTuyến: " + routeLabel(data.route)
                + "\nKhoảng cách: " + distanceLabel(data.distanceMeters)
                + "\nChọn chuyến để mở danh sách bến đi/bến xuống.");
        if (data.trips == null || data.trips.isEmpty()) {
            addText(nearbyTripsContainer, "Chưa có chuyến đang chạy qua bến này.");
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
        return card;
    }

    private void selectNearbyTrip(TripModel trip, StopModel boarding, StopModel suggestedDestination) {
        if (trip == null || trip.id == null) {
            toast("Chuyến này thiếu mã định danh từ backend.");
            return;
        }
        Long routeId = resolveRouteIdForNearbyTrip(trip, boarding);
        if (routeId == null) {
            bluetoothStatusText.setText("Không xác định được tuyến của chuyến #" + safeLong(trip.id)
                    + "\ntrip.routeId=null, selectedRoute=null, boarding.routeId=null."
                    + "\nBackend cần trả routeId trong TripResponse hoặc StopResponse.");
            toast("Chuyến này thiếu tuyến nên chưa tải được danh sách bến.");
            return;
        }
        syncSelectedTrip(trip, nearbyBoardingStop, null);
        markSelectedNearbyTrip(trip);
        bluetoothStatusText.setText("Đã chọn chuyến #" + safeLong(trip.id)
                + "\nTuyến #" + routeId
                + "\nĐang tải danh sách bến đi và bến xuống...");
        dataRepository.loadStops(routeId, (data, fromCache) -> runOnUiThread(() -> {
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
        updateNearbySelectionUi();
        updateNearbyDestinationChoices();
    }

    private void updateNearbyDestinationChoices() {
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
        }
        boolean canChooseDestination = !nearbyDestinationStops.isEmpty();
        nearbyDestinationStopSpinner.setEnabled(canChooseDestination);
        nearbyPickDestinationStopButton.setEnabled(canChooseDestination);
        nearbyConfirmCheckInButton.setEnabled(currentSelectedTripId() != null
                && nearbyBoardingStop != null
                && nearbyBoardingStop.id != null
                && canChooseDestination);
        if (selectionState != null) {
            selectionState.setDestinationStop(selectedStop(nearbyDestinationStopSpinner));
        }
        onSelectionStateChanged(true);
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
                        nearbyConfirmCheckInButton.setEnabled(currentSelectedTripId() != null
                                && nearbyBoardingStop != null
                                && nearbyBoardingStop.id != null
                                && selectedStop(nearbyDestinationStopSpinner) != null);
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
        TripModel trip = currentSelectedTrip();
        if (trip == null || trip.id == null) {
            toast("Vui lòng chọn chuyến trước.");
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
        onSelectionStateChanged(true);
        toast("Đã lưu chuyến #" + safeLong(trip.id) + " cho AI, bản đồ và Check-in Bluetooth.");
    }

    private void confirmSelectedTripCheckIn() {
        TripModel trip = currentSelectedTrip();
        if (trip == null || trip.id == null) {
            toast("Vui lòng chọn chuyến trước.");
            return;
        }
        StopModel boarding = currentBoardingStop();
        if (boarding == null || boarding.id == null) {
            toast("Chưa chọn bến đi.");
            return;
        }
        StopModel destination = currentDestinationStop();
        if (destination == null || destination.id == null) {
            toast("Vui lòng chọn bến xuống.");
            return;
        }
        createRequest(trip, boarding, destination);
        nearbyTripsContainer.removeAllViews();
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
        if (nearbyConfirmCheckInButton != null) {
            nearbyConfirmCheckInButton.setEnabled(false);
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
        if (request == null || request.id == null) {
            toast("Vui lòng tạo yêu cầu đi chuyến trước khi check-in.");
            return;
        }
        if ("BOARDED".equals(request.status) || "COMPLETED".equals(request.status)) {
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
        Long tripId = currentSelectedTripId();
        BluetoothEvent event = bluetoothManager.createCheckInEvent(
                request.id,
                tripId,
                request.bluetoothIdentifier
        );
        toast("Sẵn sàng gửi check-in: " + event.toPayload());
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT},
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
            return "Chưa có chuyến đang đăng ký.";
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
                toast("Đã cấp quyền Bluetooth. Có thể chuẩn bị check-in.");
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
