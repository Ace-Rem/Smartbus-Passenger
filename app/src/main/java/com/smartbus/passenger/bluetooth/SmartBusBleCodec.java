package com.smartbus.passenger.bluetooth;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class SmartBusBleCodec {

    public static final int MANUFACTURER_ID = 0x5342;
    public static final UUID SERVICE_UUID =
            UUID.fromString("0000f00d-0000-1000-8000-00805f9b34fb");

    private static final byte MAGIC_0 = 0x53;
    private static final byte MAGIC_1 = 0x42;
    private static final int BINARY_PAYLOAD_LENGTH = 18;
    // 24 bytes keeps the complete manufacturer AD within the legacy BLE limit.
    private static final int EXTENDED_PAYLOAD_LENGTH = 24;
    private static final int IDENTIFIER_HASH_LENGTH = 6;
    private static final long FNV_OFFSET_BASIS = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;

    private SmartBusBleCodec() {
    }

    public static byte[] encode(
            long routeId,
            long destinationStopId
    ) {
        ByteBuffer buffer = ByteBuffer.allocate(BINARY_PAYLOAD_LENGTH).order(ByteOrder.BIG_ENDIAN);
        buffer.put(MAGIC_0);
        buffer.put(MAGIC_1);
        buffer.putLong(routeId);
        buffer.putLong(destinationStopId);
        return buffer.array();
    }

    public static byte[] encode(
            long routeId,
            long destinationStopId,
            String bluetoothIdentifier
    ) {
        ByteBuffer buffer = ByteBuffer.allocate(EXTENDED_PAYLOAD_LENGTH).order(ByteOrder.BIG_ENDIAN);
        buffer.put(MAGIC_0);
        buffer.put(MAGIC_1);
        buffer.putLong(routeId);
        buffer.putLong(destinationStopId);
        long identifierHash = hashIdentifier(bluetoothIdentifier);
        for (int shift = (IDENTIFIER_HASH_LENGTH - 1) * 8; shift >= 0; shift -= 8) {
            buffer.put((byte) (identifierHash >>> shift));
        }
        return buffer.array();
    }

    public static byte[] encodeIdentifier(String bluetoothIdentifier) {
        if (bluetoothIdentifier == null || bluetoothIdentifier.isBlank()) {
            return new byte[0];
        }
        byte[] bytes = bluetoothIdentifier.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 13) {
            return bytes;
        }
        byte[] trimmed = new byte[13];
        System.arraycopy(bytes, 0, trimmed, 0, 13);
        return trimmed;
    }

    private static long hashIdentifier(String identifier) {
        long hash = FNV_OFFSET_BASIS;
        if (identifier == null) {
            return hash;
        }
        byte[] bytes = identifier.getBytes(StandardCharsets.UTF_8);
        for (byte value : bytes) {
            hash ^= value & 0xffL;
            hash *= FNV_PRIME;
        }
        return hash;
    }

}
