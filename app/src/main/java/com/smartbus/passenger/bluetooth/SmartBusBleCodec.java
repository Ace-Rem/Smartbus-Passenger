package com.smartbus.passenger.bluetooth;

import java.nio.charset.StandardCharsets;

public final class SmartBusBleCodec {

    public static final java.util.UUID SERVICE_UUID =
            java.util.UUID.fromString("0000f00d-0000-1000-8000-00805f9b34fb");
    public static final java.util.UUID CHECK_IN_CHARACTERISTIC_UUID =
            java.util.UUID.fromString("0000f00e-0000-1000-8000-00805f9b34fb");
    /** Technical identity only; the business payload remains route + alighting stop. */
    public static final java.util.UUID IDENTITY_CHARACTERISTIC_UUID =
            java.util.UUID.fromString("0000f00f-0000-1000-8000-00805f9b34fb");

    private SmartBusBleCodec() {
    }

    public static byte[] encode(long routeId, long alightingStationId) {
        // Current database IDs are numeric, so this delimiter is unambiguous.
        return ("#" + routeId + " " + alightingStationId).getBytes(StandardCharsets.UTF_8);
    }

}
