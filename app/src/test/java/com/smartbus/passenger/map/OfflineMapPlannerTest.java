package com.smartbus.passenger.map;

import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class OfflineMapPlannerTest {
    @Test
    public void ignoresInvalidCoordinatesAndAddsMinimumPadding() {
        OfflineMapPlanner.Bounds bounds = OfflineMapPlanner.fromCoordinates(Arrays.asList(
                new double[]{Double.POSITIVE_INFINITY, 106.6},
                new double[]{10.762, 106.660},
                new double[]{10.763, 106.660}
        ), 0.20d);

        assertTrue(bounds.isValid());
        assertTrue(bounds.minLatitude < 10.762);
        assertTrue(bounds.maxLatitude > 10.763);
    }

    @Test
    public void xyzTileConversionUsesWebMercator() {
        assertEquals(4096, OfflineMapPlanner.tileX(0d, 13));
        assertEquals(4096, OfflineMapPlanner.tileY(0d, 13));
    }

    @Test
    public void tileRangeIsFinite() {
        OfflineMapPlanner.Bounds bounds = OfflineMapPlanner.fromCoordinates(
                Arrays.asList(new double[]{10.762, 106.660}), 0.20d);
        assertTrue(OfflineMapPlanner.tiles(bounds, OfflineMapPlanner.DEFAULT_ZOOM).count() > 0);
    }
}
