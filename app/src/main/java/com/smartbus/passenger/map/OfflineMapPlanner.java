package com.smartbus.passenger.map;

import java.util.List;

/** Pure Web-Mercator calculations used to validate a small offline package. */
public final class OfflineMapPlanner {
    public static final int DEFAULT_ZOOM = 13;
    public static final double MAX_MERCATOR_LATITUDE = 85.05112878d;

    private OfflineMapPlanner() { }

    public static Bounds fromCoordinates(List<double[]> coordinates, double paddingFraction) {
        Bounds bounds = new Bounds();
        if (coordinates != null) {
            for (double[] coordinate : coordinates) {
                if (coordinate != null && coordinate.length >= 2) {
                    bounds.include(coordinate[0], coordinate[1]);
                }
            }
        }
        if (!bounds.isValid()) {
            return bounds;
        }
        bounds.expand(paddingFraction);
        return bounds;
    }

    public static TileRange tiles(Bounds bounds, int zoom) {
        if (bounds == null || !bounds.isValid() || zoom < 0 || zoom > 22) {
            return TileRange.empty(zoom);
        }
        int worldSize = 1 << zoom;
        return new TileRange(tileX(bounds.minLongitude, zoom), tileX(bounds.maxLongitude, zoom),
                tileY(bounds.maxLatitude, zoom), tileY(bounds.minLatitude, zoom), worldSize);
    }

    public static int tileX(double longitude, int zoom) {
        int worldSize = 1 << zoom;
        return clamp((int) Math.floor((longitude + 180d) / 360d * worldSize), 0, worldSize - 1);
    }

    public static int tileY(double latitude, int zoom) {
        int worldSize = 1 << zoom;
        double bounded = Math.max(-MAX_MERCATOR_LATITUDE,
                Math.min(MAX_MERCATOR_LATITUDE, latitude));
        double radians = Math.toRadians(bounded);
        double normalized = (1d - Math.log(Math.tan(radians) + 1d / Math.cos(radians)) / Math.PI) / 2d;
        return clamp((int) Math.floor(normalized * worldSize), 0, worldSize - 1);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Bounds {
        public double minLatitude = Double.POSITIVE_INFINITY;
        public double maxLatitude = Double.NEGATIVE_INFINITY;
        public double minLongitude = Double.POSITIVE_INFINITY;
        public double maxLongitude = Double.NEGATIVE_INFINITY;

        public void include(double latitude, double longitude) {
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || latitude < -90d || latitude > 90d || longitude < -180d || longitude > 180d
                    || (latitude == 0d && longitude == 0d)) {
                return;
            }
            minLatitude = Math.min(minLatitude, latitude);
            maxLatitude = Math.max(maxLatitude, latitude);
            minLongitude = Math.min(minLongitude, longitude);
            maxLongitude = Math.max(maxLongitude, longitude);
        }

        public boolean isValid() {
            return Double.isFinite(minLatitude) && Double.isFinite(maxLatitude)
                    && Double.isFinite(minLongitude) && Double.isFinite(maxLongitude);
        }

        /** A direct min/max box would be wrong for a route crossing the antimeridian. */
        public boolean crossesAntimeridian() {
            return isValid() && maxLongitude - minLongitude > 180d;
        }

        private void expand(double fraction) {
            double safeFraction = Math.max(0.01d, Math.min(0.50d, fraction));
            double latitudeSpan = Math.max(maxLatitude - minLatitude, 0.002d);
            double longitudeSpan = Math.max(maxLongitude - minLongitude, 0.002d);
            minLatitude = Math.max(-90d, minLatitude - latitudeSpan * safeFraction);
            maxLatitude = Math.min(90d, maxLatitude + latitudeSpan * safeFraction);
            minLongitude = Math.max(-180d, minLongitude - longitudeSpan * safeFraction);
            maxLongitude = Math.min(180d, maxLongitude + longitudeSpan * safeFraction);
        }
    }

    public static final class TileRange {
        public final int minX;
        public final int maxX;
        public final int minY;
        public final int maxY;
        public final int worldSize;

        private TileRange(int minX, int maxX, int minY, int maxY, int worldSize) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
            this.worldSize = worldSize;
        }

        private static TileRange empty(int zoom) {
            int worldSize = zoom >= 0 && zoom <= 22 ? 1 << zoom : 0;
            return new TileRange(0, -1, 0, -1, worldSize);
        }

        public long count() {
            return maxX < minX || maxY < minY
                    ? 0L : (long) (maxX - minX + 1) * (maxY - minY + 1);
        }
    }
}
