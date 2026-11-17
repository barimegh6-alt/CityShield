package com.cityshield.algorithm.tsp;

/**
 * Geographic distance helper. Distances are haversine great-circle distances multiplied by
 * {@link #ROAD_FACTOR} to approximate road length. This is a simulation estimate, <b>not</b> real
 * road navigation. A constant multiple of a metric is still a metric, so the triangle inequality holds.
 */
public final class GeoDistance {
    /** Mean Earth radius in km. */
    public static final double EARTH_RADIUS_KM = 6371.0088;
    /** Road-distance approximation factor. */
    public static final double ROAD_FACTOR = 1.3;

    private GeoDistance() {
    }

    /** Great-circle distance in km. */
    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /** Estimated road distance in km: haversine times {@link #ROAD_FACTOR}. */
    public static double roadKm(double lat1, double lon1, double lat2, double lon2) {
        return haversine(lat1, lon1, lat2, lon2) * ROAD_FACTOR;
    }

    /** Builds a symmetric road-distance matrix for the given coordinates (index 0 is the depot). */
    public static double[][] matrix(double[] lat, double[] lon) {
        if (lat == null || lon == null || lat.length != lon.length) {
            throw new IllegalArgumentException("lat and lon arrays must be non-null and of equal length");
        }
        int n = lat.length;
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) {
            if (Double.isNaN(lat[i]) || Double.isNaN(lon[i]) || lat[i] < -90 || lat[i] > 90 || lon[i] < -180 || lon[i] > 180) {
                throw new IllegalArgumentException("invalid coordinate at index " + i);
            }
            for (int j = i + 1; j < n; j++) {
                d[i][j] = roadKm(lat[i], lon[i], lat[j], lon[j]);
                d[j][i] = d[i][j];
            }
        }
        return d;
    }
}
