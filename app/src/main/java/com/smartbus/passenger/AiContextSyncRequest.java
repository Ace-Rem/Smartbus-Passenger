package com.smartbus.passenger;

import java.util.Map;

final class AiContextSyncRequest {
    final Long tripId;
    final Long routeId;
    final Map<String, Object> context;
    final Long clientVersion;

    AiContextSyncRequest(Long tripId, Long routeId, Map<String, Object> context, Long clientVersion) {
        this.tripId = tripId;
        this.routeId = routeId;
        this.context = context;
        this.clientVersion = clientVersion;
    }
}

final class AiContextSyncResponse {
    boolean accepted;
    String storedAt;
}
