package com.smartbus.passenger.lan;

import android.content.Context;
import android.net.wifi.WifiManager;
import com.smartbus.passenger.bluetooth.SmartBusBleCodec;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Dependency-free Passenger-side LAN sender using UDP discovery + TCP data. */
public final class LocalLanCheckInClient {

    private static final int DISCOVERY_PORT = 38766;
    private static final int DEFAULT_TCP_PORT = 38767;
    private static final int STATUS_PORT = 38768;
    private static final String DISCOVERY_REQUEST = "SMARTBUS_DRIVER_DISCOVERY_V1";
    private static final String DISCOVERY_RESPONSE = "SMARTBUS_DRIVER_V1";
    private static final String CHECK_IN_HEADER = "SMARTBUS_CHECKIN_V1";
    private static final String STATUS_HEADER = "SMARTBUS_LAN_STATUS_V1";

    public interface Listener {
        void onMessage(String message);

        void onAccepted(String requestId, String status, long tripId, long routeId,
                        Long boardingStopId, Long destinationStopId);
    }

    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService statusExecutor = Executors.newCachedThreadPool();
    private volatile ServerSocket statusServer;
    private volatile Listener statusListener;
    private volatile boolean stopped;

    public LocalLanCheckInClient(Context context) {
        this.context = context.getApplicationContext();
    }

    public void startCheckIn(long routeId, Long boardingStopId, long alightingStationId,
                             String requestId, String passengerIdentifier, Listener listener) {
        if (stopped || requestId == null || requestId.isBlank()) return;
        if (passengerIdentifier == null || passengerIdentifier.isBlank()) {
            notify(listener, "Không gửi được check-in LAN vì thiếu mã định danh hành khách.");
            return;
        }
        ensureStatusServer(listener);
        executor.execute(() -> send(routeId, boardingStopId, alightingStationId,
                requestId, passengerIdentifier, listener));
    }

    public void stop() {
        stopped = true;
        ServerSocket server = statusServer;
        statusServer = null;
        if (server != null) {
            try { server.close(); } catch (IOException ignored) { }
        }
        executor.shutdownNow();
        statusExecutor.shutdownNow();
    }

    private synchronized void ensureStatusServer(Listener listener) {
        if (statusServer != null) return;
        statusListener = listener;
        try {
            ServerSocket server = new ServerSocket();
            server.setReuseAddress(true);
            server.bind(new InetSocketAddress(STATUS_PORT));
            statusServer = server;
            statusExecutor.execute(this::acceptStatusConnections);
        } catch (IOException exception) {
            notify(listener, "Không mở được kênh nhận trạng thái LAN: " + safeMessage(exception));
        }
    }

    private void acceptStatusConnections() {
        while (!stopped) {
            try {
                ServerSocket server = statusServer;
                if (server == null) return;
                Socket socket = server.accept();
                statusExecutor.execute(() -> handleStatusConnection(socket));
            } catch (IOException exception) {
                if (!stopped) notify(statusListener,
                        "Mất kênh nhận trạng thái LAN: " + safeMessage(exception));
            }
        }
    }

    private void handleStatusConnection(Socket socket) {
        try (Socket connection = socket) {
            connection.setSoTimeout(3000);
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8));
            String header = reader.readLine();
            String requestId = reader.readLine();
            String status = reader.readLine();
            Long tripId = parseStatusId(reader.readLine());
            Long routeId = parseStatusId(reader.readLine());
            Long boardingStopId = parseOptionalStatusId(reader.readLine());
            Long destinationStopId = parseOptionalStatusId(reader.readLine());
            if (!STATUS_HEADER.equals(header) || requestId == null || requestId.isBlank()
                    || status == null || tripId == null || routeId == null) {
                return;
            }
            Listener listener = statusListener;
            if (listener != null) {
                listener.onAccepted(requestId, status, tripId, routeId,
                        boardingStopId, destinationStopId);
            }
        } catch (IOException ignored) {
            // The normal check-in channel has its own status/error reporting.
        }
    }

    private static Long parseStatusId(String value) {
        if (value == null || value.length() > 32) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Long parseOptionalStatusId(String value) {
        return value == null || "-".equals(value) ? null : parseStatusId(value);
    }

    private void send(long routeId, Long boardingStopId, long alightingStationId,
                      String requestId, String passengerIdentifier, Listener listener) {
        DriverEndpoint endpoint = discover(routeId, listener);
        if (endpoint == null || stopped) {
            notify(listener, "Không tìm thấy Driver trong mạng LAN; kiểm tra hai thiết bị cùng Wi-Fi/hotspot.");
            return;
        }
        String payload = new String(
                SmartBusBleCodec.encode(routeId, alightingStationId), StandardCharsets.UTF_8);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(endpoint.address, endpoint.port), 1500);
            socket.setSoTimeout(3000);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                    socket.getOutputStream(), StandardCharsets.UTF_8));
            writer.write(CHECK_IN_HEADER);
            writer.newLine();
            writer.write(requestId);
            writer.newLine();
            writer.write(passengerIdentifier);
            writer.newLine();
            writer.write(boardingStopId == null ? "-" : String.valueOf(boardingStopId));
            writer.newLine();
            writer.write(payload);
            writer.newLine();
            writer.flush();
            BufferedReader reader = new BufferedReader(new InputStreamReader(
                    socket.getInputStream(), StandardCharsets.UTF_8));
            String response = reader.readLine();
            if ("OK".equals(response) || "DUPLICATE".equals(response)) {
                notify(listener, "Đã gửi check-in qua mạng LAN đến Driver.");
            } else {
                notify(listener, "Driver đã nhận kết nối LAN nhưng từ chối dữ liệu check-in.");
            }
        } catch (IOException exception) {
            notify(listener, "Không gửi được check-in LAN: " + safeMessage(exception));
        }
    }

    private DriverEndpoint discover(long routeId, Listener listener) {
        Set<String> sentTargets = new HashSet<>();
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(250);
            byte[] request = DISCOVERY_REQUEST.getBytes(StandardCharsets.UTF_8);
            sendDiscovery(socket, request, InetAddress.getByName("255.255.255.255"), sentTargets);
            InetAddress gateway = wifiGateway();
            if (gateway != null) sendDiscovery(socket, request, gateway, sentTargets);

            long deadline = System.currentTimeMillis() + 1200L;
            byte[] buffer = new byte[160];
            while (!stopped && System.currentTimeMillis() < deadline) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    String response = new String(
                            packet.getData(), packet.getOffset(), packet.getLength(), StandardCharsets.UTF_8);
                    String[] parts = response.split(";", -1);
                    if (parts.length != 3 || !DISCOVERY_RESPONSE.equals(parts[0])) continue;
                    int port;
                    long driverRoute;
                    try {
                        port = Integer.parseInt(parts[1]);
                        driverRoute = Long.parseLong(parts[2]);
                    } catch (NumberFormatException ignored) {
                        continue;
                    }
                    if (driverRoute == routeId && port > 0 && port <= 65535) {
                        return new DriverEndpoint(packet.getAddress(), port);
                    }
                } catch (java.net.SocketTimeoutException ignored) {
                    // Continue until the short discovery window expires.
                }
            }
        } catch (IOException exception) {
            notify(listener, "Không kiểm tra được mạng LAN: " + safeMessage(exception));
        }
        return null;
    }

    private static void sendDiscovery(DatagramSocket socket, byte[] request,
                                      InetAddress address, Set<String> sentTargets) throws IOException {
        String key = address.getHostAddress() + ":" + DISCOVERY_PORT;
        if (!sentTargets.add(key)) return;
        socket.send(new DatagramPacket(request, request.length, address, DISCOVERY_PORT));
    }

    @SuppressWarnings("deprecation")
    private InetAddress wifiGateway() {
        WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null || wifiManager.getDhcpInfo() == null) return null;
        int gateway = wifiManager.getDhcpInfo().gateway;
        if (gateway == 0) return null;
        byte[] address = new byte[] {
                (byte) (gateway & 0xff), (byte) ((gateway >> 8) & 0xff),
                (byte) ((gateway >> 16) & 0xff), (byte) ((gateway >> 24) & 0xff)
        };
        try {
            return InetAddress.getByAddress(address);
        } catch (IOException ignored) {
            return null;
        }
    }

    private void notify(Listener listener, String message) {
        if (!stopped && listener != null) listener.onMessage(message);
    }

    private static String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private static final class DriverEndpoint {
        private final InetAddress address;
        private final int port;

        private DriverEndpoint(InetAddress address, int port) {
            this.address = address;
            this.port = port == 0 ? DEFAULT_TCP_PORT : port;
        }
    }
}
