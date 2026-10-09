package com.smartbus.passenger.map;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Locale;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.MapTileProviderBase;
import org.osmdroid.tileprovider.MapTileProviderBasic;
import org.osmdroid.tileprovider.modules.CantContinueException;
import org.osmdroid.tileprovider.modules.IFilesystemCache;
import org.osmdroid.tileprovider.modules.MapTileDownloader;
import org.osmdroid.tileprovider.modules.TileDownloader;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.MapTileIndex;

/** Central osmdroid configuration and optional packaged MBTiles installation. */
public final class OfflineMapSupport {
    private static final String TAG = "SmartBusMap";
    private static final String OFFLINE_ASSET = "offline_maps/smartbus.mbtiles";
    private static final String OFFLINE_FILENAME = "smartbus-offline.mbtiles";
    private static final long CACHE_MAX_BYTES = 256L * 1024L * 1024L;
    private static final long CACHE_TRIM_BYTES = 200L * 1024L * 1024L;

    private OfflineMapSupport() { }

    public static OnlineTileSourceBase onlineTileSource(Context context) {
        return onlineTileSource(context, "");
    }

    public static OnlineTileSourceBase onlineTileSource(Context context, String stadiaApiKey) {
        if (isStadiaConfigured(stadiaApiKey)) {
            return createStadiaSource(stadiaApiKey);
        }
        return createOsmSource();
    }

    public static MapTileProviderBase createTileProvider(Context context, String stadiaApiKey) {
        if (!isStadiaConfigured(stadiaApiKey)) {
            return null;
        }
        StadiaTileSource stadiaSource = createStadiaSource(stadiaApiKey);
        return new FallbackMapTileProvider(context, stadiaSource);
    }

    public static boolean isStadiaConfigured(String stadiaApiKey) {
        return stadiaApiKey != null && !stadiaApiKey.trim().isEmpty();
    }

    private static StadiaTileSource createStadiaSource(String stadiaApiKey) {
        return new StadiaTileSource(stadiaApiKey.trim(), createOsmSource());
    }

    private static OnlineTileSourceBase createOsmSource() {
        return new OnlineTileSourceBase("OpenStreetMap", 1, 19, 256, ".png",
                new String[]{"https://tile.openstreetmap.org/"},
                "© OpenStreetMap contributors") {
            @Override
            public String getTileURLString(long mapTileIndex) {
                return getBaseUrl()
                        + MapTileIndex.getZoom(mapTileIndex) + "/"
                        + MapTileIndex.getX(mapTileIndex) + "/"
                        + MapTileIndex.getY(mapTileIndex) + ".png";
            }
        };
    }

    public static void configure(Context context, String userAgent, SharedPreferences preferences) {
        Context appContext = context.getApplicationContext();
        Configuration.getInstance().load(appContext, preferences);
        Configuration.getInstance().setUserAgentValue(userAgent);
        Configuration.getInstance().setTileDownloadThreads((short) 2);
        Configuration.getInstance().setTileDownloadMaxQueueSize((short) 48);
        Configuration.getInstance().setCacheMapTileCount((short) 256);
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(CACHE_MAX_BYTES);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(CACHE_TRIM_BYTES);
        installPackagedOfflineArchive(appContext);
        Log.i(TAG, "osmdroid configured: Stadia primary with OSM fallback"
                + ", cache=" + Configuration.getInstance().getOsmdroidTileCache()
                + ", offlinePackage=" + hasOfflinePackage());
    }

    public static boolean hasOfflinePackage() {
        File basePath = Configuration.getInstance().getOsmdroidBasePath();
        return basePath != null && new File(basePath, OFFLINE_FILENAME).isFile();
    }

    public static void logCoverage(String owner, List<double[]> coordinates) {
        OfflineMapPlanner.Bounds bounds = OfflineMapPlanner.fromCoordinates(coordinates, 0.20d);
        if (!bounds.isValid()) {
            Log.w(TAG, owner + ": no valid marker coordinates for offline bounds");
            return;
        }
        if (bounds.crossesAntimeridian()) {
            Log.w(TAG, owner + ": marker bounds cross the antimeridian; refusing one huge offline package");
            return;
        }
        OfflineMapPlanner.TileRange range = OfflineMapPlanner.tiles(bounds, OfflineMapPlanner.DEFAULT_ZOOM);
        Log.i(TAG, owner + ": offline bounds=" + bounds.minLatitude + "," + bounds.minLongitude
                + ".." + bounds.maxLatitude + "," + bounds.maxLongitude
                + ", zoom=" + OfflineMapPlanner.DEFAULT_ZOOM + ", tiles=" + range.count());
        if (range.count() > 256L) {
            Log.w(TAG, owner + ": offline area exceeds safe 256-tile package limit");
        }
    }

    private static void installPackagedOfflineArchive(Context context) {
        File basePath = Configuration.getInstance().getOsmdroidBasePath(context);
        if (basePath == null || !basePath.exists() || hasOfflinePackage()) {
            return;
        }
        File destination = new File(basePath, OFFLINE_FILENAME);
        File temporary = new File(basePath, OFFLINE_FILENAME + ".tmp");
        try (InputStream input = context.getAssets().open(OFFLINE_ASSET);
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.getFD().sync();
            if (temporary.length() <= 0L || !temporary.renameTo(destination)) {
                throw new IOException("offline archive rename failed");
            }
        } catch (IOException absentOrFailed) {
            temporary.delete();
            Log.i(TAG, "No packaged offline MBTiles found; using online/cache fallback");
        }
    }

    private static final class StadiaTileSource extends OnlineTileSourceBase {
        private static final String TILE_URL = "https://tiles.stadiamaps.com/tiles/alidade_smooth/";
        private final String apiKey;
        private final OnlineTileSourceBase fallbackSource;

        private StadiaTileSource(String apiKey, OnlineTileSourceBase fallbackSource) {
            super("StadiaMaps", 1, 20, 256, ".png", new String[]{TILE_URL},
                    "© Stadia Maps © OpenMapTiles © OpenStreetMap");
            this.apiKey = apiKey;
            this.fallbackSource = fallbackSource;
        }

        @Override
        public String getTileURLString(long mapTileIndex) {
            return TILE_URL
                    + MapTileIndex.getZoom(mapTileIndex) + "/"
                    + MapTileIndex.getX(mapTileIndex) + "/"
                    + MapTileIndex.getY(mapTileIndex) + ".png";
        }
    }

    private static final class FallbackMapTileProvider extends MapTileProviderBasic {
        private FallbackMapTileProvider(Context context, StadiaTileSource stadiaSource) {
            super(context, stadiaSource);
        }

        @Override
        protected MapTileDownloader createDownloaderProvider(
                org.osmdroid.tileprovider.modules.INetworkAvailablityCheck networkAvailability,
                ITileSource tileSource
        ) {
            MapTileDownloader downloader = super.createDownloaderProvider(networkAvailability, tileSource);
            if (tileSource instanceof StadiaTileSource) {
                StadiaTileSource stadiaSource = (StadiaTileSource) tileSource;
                downloader.setTileDownloader(new StadiaThenOsmTileDownloader(
                        stadiaSource, stadiaSource.fallbackSource));
            }
            return downloader;
        }
    }

    private static final class StadiaThenOsmTileDownloader extends TileDownloader {
        private final StadiaTileSource stadiaSource;
        private final OnlineTileSourceBase osmSource;

        private StadiaThenOsmTileDownloader(
                StadiaTileSource stadiaSource,
                OnlineTileSourceBase osmSource
        ) {
            this.stadiaSource = stadiaSource;
            this.osmSource = osmSource;
        }

        @Override
        public Drawable downloadTile(
                long mapTileIndex,
                int redirectCount,
                String targetUrl,
                IFilesystemCache filesystemCache,
                OnlineTileSourceBase tileSource
        ) throws CantContinueException {
            Drawable stadiaTile = downloadStadiaTile(mapTileIndex, filesystemCache);
            if (stadiaTile != null) {
                return stadiaTile;
            }
            // Exactly one fallback request is made for this tile; osmdroid handles
            // queueing and backoff for the original Stadia URL.
            return super.downloadTile(
                    mapTileIndex, 0, osmSource.getTileURLString(mapTileIndex),
                    filesystemCache, osmSource);
        }

        private Drawable downloadStadiaTile(long mapTileIndex, IFilesystemCache filesystemCache) {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(
                        stadiaSource.getTileURLString(mapTileIndex)).openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(15_000);
                connection.setInstanceFollowRedirects(true);
                connection.setUseCaches(true);
                connection.setRequestProperty(
                        Configuration.getInstance().getUserAgentHttpHeader(),
                        Configuration.getInstance().getUserAgentValue());
                connection.setRequestProperty("Authorization", "Stadia-Auth " + stadiaSource.apiKey);

                int responseCode = connection.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "Stadia tile request failed with HTTP " + responseCode
                            + "; using OSM fallback");
                    return null;
                }
                String mime = connection.getHeaderField("Content-Type");
                if (mime != null && !mime.toLowerCase(Locale.US).contains("image")) {
                    Log.w(TAG, "Stadia returned a non-image tile; using OSM fallback");
                    return null;
                }

                byte[] data = readFully(connection.getInputStream());
                if (data.length == 0) {
                    return null;
                }
                Drawable drawable = stadiaSource.getDrawable(new ByteArrayInputStream(data));
                if (drawable == null) {
                    return null;
                }
                if (filesystemCache != null) {
                    filesystemCache.saveFile(
                            stadiaSource, mapTileIndex, new ByteArrayInputStream(data),
                            stadiaSource.getTileSourcePolicy().computeExpirationTime(
                                    connection, System.currentTimeMillis()));
                }
                return drawable;
            } catch (Exception ignored) {
                Log.w(TAG, "Stadia tile request failed; using OSM fallback");
                return null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private byte[] readFully(InputStream input) throws IOException {
            try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        }
    }
}
