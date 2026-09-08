package com.smartbus.passenger;

import android.content.SharedPreferences;

import com.smartbus.passenger.bluetooth.BluetoothEvent;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PassengerSelectionFlowTest {

    @Test
    public void routeSelectionCanBeConfirmedWithoutTripId() {
        MemoryPreferences preferences = new MemoryPreferences();
        SelectedTripStore store = new SelectedTripStore(preferences);
        PassengerSelectionState state = new PassengerSelectionState();
        RouteModel route = new RouteModel();
        route.id = 7L;
        state.setRoute(route);
        state.setBoardingStop(stop(101L, 1, 10.1, 106.1));
        state.setDestinationStop(stop(105L, 5, 10.5, 106.5));

        store.persist(state);

        PassengerSelectionState restored = new SelectedTripStore(preferences).state();
        assertEquals(Long.valueOf(7L), restored.selectedRouteId());
        assertEquals(Long.valueOf(101L), restored.boardingStopId());
        assertEquals(Long.valueOf(105L), restored.destinationStopId());
        assertTrue(!restored.hasTrip());

        restored.confirmLocalSelection(null);
        // Rebinding the same spinner values must not cancel the confirmation.
        restored.setBoardingStop(stop(101L, 1, 10.1, 106.1));
        restored.setDestinationStop(stop(105L, 5, 10.5, 106.5));
        store = new SelectedTripStore(preferences);
        store.persist(restored);
        assertTrue(new SelectedTripStore(preferences).state().isLocalSelectionConfirmed());
    }

    @Test
    public void selectedTripSurvivesLocalRestoreAndBuildsBlePayload() {
        MemoryPreferences preferences = new MemoryPreferences();
        SelectedTripStore firstStore = new SelectedTripStore(preferences);

        TripModel trip = new TripModel();
        trip.id = 42L;
        trip.routeId = 7L;

        StopModel boarding = stop(101L, 1, 10.1, 106.1);
        StopModel destination = stop(105L, 5, 10.5, 106.5);

        PassengerSelectionState firstState = new PassengerSelectionState();
        firstState.selectTrip(trip);
        firstState.setBoardingStop(boarding);
        firstState.setDestinationStop(destination);
        firstStore.selectTrip(trip, boarding, destination);
        firstStore.persist(firstState);

        SelectedTripStore restoredStore = new SelectedTripStore(preferences);
        PassengerSelectionState restoredState = restoredStore.state();
        assertEquals(Long.valueOf(42L), restoredState.selectedTripId());
        assertEquals(Long.valueOf(101L), restoredState.boardingStopId());
        assertEquals(Long.valueOf(105L), restoredState.destinationStopId());

        restoredState.confirmLocalSelection(null);
        restoredStore.persist(restoredState);

        PassengerSelectionState confirmedState = new SelectedTripStore(preferences).state();
        assertTrue(confirmedState.isLocalSelectionConfirmed());
        assertEquals(Long.valueOf(42L), confirmedState.selectedTripId());

        BluetoothEvent event = new BluetoothEvent(
                900L,
                confirmedState.selectedTripId(),
                trip.routeId,
                confirmedState.destinationStopId(),
                destination.latitude.doubleValue(),
                destination.longitude.doubleValue(),
                "P1D2"
        );
        byte[] payload = event.toManufacturerPayload();
        assertEquals(24, payload.length);
        assertEquals(0x53, payload[0] & 0xff);
        assertEquals(0x42, payload[1] & 0xff);
        long payloadRouteId = 0L;
        for (int index = 2; index < 10; index++) {
            payloadRouteId = (payloadRouteId << 8) | (payload[index] & 0xffL);
        }
        assertEquals(7L, payloadRouteId);
        long payloadDestinationStopId = 0L;
        for (int index = 10; index < 18; index++) {
            payloadDestinationStopId = (payloadDestinationStopId << 8) | (payload[index] & 0xffL);
        }
        assertEquals(105L, payloadDestinationStopId);
    }

    private static StopModel stop(long id, int order, double latitude, double longitude) {
        StopModel stop = new StopModel();
        stop.id = id;
        stop.stopOrder = order;
        stop.latitude = java.math.BigDecimal.valueOf(latitude);
        stop.longitude = java.math.BigDecimal.valueOf(longitude);
        stop.name = "Stop " + id;
        return stop;
    }

    private static final class MemoryPreferences implements SharedPreferences {
        private final Map<String, Object> values = new HashMap<>();

        @Override
        public Map<String, ?> getAll() {
            return Collections.unmodifiableMap(values);
        }

        @Override
        public String getString(String key, String defaultValue) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : defaultValue;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Set<String> getStringSet(String key, Set<String> defaultValue) {
            Object value = values.get(key);
            return value instanceof Set ? (Set<String>) value : defaultValue;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            Object value = values.get(key);
            return value instanceof Integer ? (Integer) value : defaultValue;
        }

        @Override
        public long getLong(String key, long defaultValue) {
            Object value = values.get(key);
            return value instanceof Long ? (Long) value : defaultValue;
        }

        @Override
        public float getFloat(String key, float defaultValue) {
            Object value = values.get(key);
            return value instanceof Float ? (Float) value : defaultValue;
        }

        @Override
        public boolean getBoolean(String key, boolean defaultValue) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : defaultValue;
        }

        @Override
        public boolean contains(String key) {
            return values.containsKey(key);
        }

        @Override
        public Editor edit() {
            return new MemoryEditor();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        }

        private final class MemoryEditor implements Editor {
            private final Map<String, Object> updates = new HashMap<>();
            private boolean clear;

            @Override
            public Editor putString(String key, String value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor putStringSet(String key, Set<String> value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor putInt(String key, int value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor putLong(String key, long value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor putFloat(String key, float value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor putBoolean(String key, boolean value) {
                updates.put(key, value);
                return this;
            }

            @Override
            public Editor remove(String key) {
                updates.put(key, null);
                return this;
            }

            @Override
            public Editor clear() {
                clear = true;
                return this;
            }

            @Override
            public boolean commit() {
                apply();
                return true;
            }

            @Override
            public void apply() {
                if (clear) {
                    values.clear();
                }
                for (Map.Entry<String, Object> entry : updates.entrySet()) {
                    if (entry.getValue() == null) {
                        values.remove(entry.getKey());
                    } else {
                        values.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }
}
