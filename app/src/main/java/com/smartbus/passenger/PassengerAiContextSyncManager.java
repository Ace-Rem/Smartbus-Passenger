package com.smartbus.passenger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Fire-and-forget uploader. It never runs on the UI or Room executor. */
final class PassengerAiContextSyncManager {

    private static final long DEBOUNCE_MS = 350L;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private final AtomicReference<Pending> latest = new AtomicReference<>();
    private final AtomicLong version = new AtomicLong(System.currentTimeMillis());
    private ScheduledFuture<?> scheduled;
    private boolean stopped;

    synchronized void submit(Map<String, Object> context) {
        if (stopped || context == null) return;
        Long tripId = numberValue(context.get("selectedTripId"));
        Long routeId = numberValue(context.get("selectedRouteId"));
        if (tripId == null && routeId == null) return;
        latest.set(new Pending(tripId, routeId, new LinkedHashMap<>(context), version.incrementAndGet()));
        if (scheduled != null) scheduled.cancel(false);
        scheduled = executor.schedule(this::flush, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    private void flush() {
        Pending pending = latest.getAndSet(null);
        if (pending == null || stopped) return;
        Call<ApiResponse<AiContextSyncResponse>> call = RetrofitClient.api().syncAiContext(
                new AiContextSyncRequest(pending.tripId, pending.routeId, pending.context, pending.version));
        call.enqueue(new Callback<>() {
            @Override public void onResponse(Call<ApiResponse<AiContextSyncResponse>> call,
                                              Response<ApiResponse<AiContextSyncResponse>> response) {
                scheduleIfNewer();
            }

            @Override public void onFailure(Call<ApiResponse<AiContextSyncResponse>> call, Throwable t) {
                // The next trigger contains a complete snapshot; never block or surface this to UI.
                scheduleIfNewer();
            }
        });
    }

    private synchronized void scheduleIfNewer() {
        if (stopped || latest.get() == null) return;
        if (scheduled != null) scheduled.cancel(false);
        scheduled = executor.schedule(this::flush, DEBOUNCE_MS, TimeUnit.MILLISECONDS);
    }

    synchronized void shutdown() {
        stopped = true;
        latest.set(null);
        if (scheduled != null) scheduled.cancel(false);
        executor.shutdownNow();
    }

    synchronized void clearPending() {
        latest.set(null);
        if (scheduled != null) scheduled.cancel(false);
    }

    private Long numberValue(Object raw) {
        if (raw instanceof Number number) return number.longValue();
        try { return Long.parseLong(String.valueOf(raw)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private record Pending(Long tripId, Long routeId, Map<String, Object> context, long version) { }
}
