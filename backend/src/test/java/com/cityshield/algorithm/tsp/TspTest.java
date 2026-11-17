package com.cityshield.algorithm.tsp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TspTest {
    private static double[][] euclid(double[] x, double[] y) {
        int n = x.length;
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                d[i][j] = Math.hypot(x[i] - x[j], y[i] - y[j]);
            }
        }
        return d;
    }

    private static double[][] random(Random r, int n) {
        double[] x = new double[n];
        double[] y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = r.nextDouble() * 100;
            y[i] = r.nextDouble() * 100;
        }
        return euclid(x, y);
    }

    @Test
    @DisplayName("Unit square: tour has length 4 and visits each node once")
    void square() {
        double[][] d = euclid(new double[]{0, 1, 1, 0}, new double[]{0, 0, 1, 1});
        TspResult r = MetricTspApproximation.solve(d);
        assertEquals(5, r.tour().size());
        assertEquals(0, r.tour().get(0));
        assertEquals(0, r.tour().get(4));
        assertEquals(3.0, r.mstWeight(), 1e-9);
        Set<Integer> seen = new HashSet<>(r.tour().subList(0, 4));
        assertEquals(4, seen.size());
        assertTrue(r.length() <= 2 * 4.0 + 1e-9);
        assertEquals(4.0, HeldKarpTsp.solve(d).length(), 1e-9);
    }

    @Test
    @DisplayName("Collinear points: approximation equals optimum 2*span")
    void collinear() {
        double[][] d = euclid(new double[]{0, 1, 2, 3}, new double[]{0, 0, 0, 0});
        assertEquals(6.0, MetricTspApproximation.solve(d).length(), 1e-9);
        assertEquals(6.0, HeldKarpTsp.solve(d).length(), 1e-9);
    }

    @Test
    @DisplayName("Small cases: 1, 2 and 3 nodes")
    void tiny() {
        double[][] one = {{0}};
        assertEquals(List.of(0, 0), MetricTspApproximation.solve(one).tour());
        double[][] two = {{0, 5}, {5, 0}};
        assertEquals(10.0, MetricTspApproximation.solve(two).length(), 1e-9);
        assertEquals(List.of(0, 1, 0), MetricTspApproximation.solve(two).tour());
        double[][] three = euclid(new double[]{0, 3, 0}, new double[]{0, 0, 4});
        assertEquals(12.0, MetricTspApproximation.solve(three).length(), 1e-9);
        assertEquals(12.0, HeldKarpTsp.solve(three).length(), 1e-9);
        assertEquals(List.of(0, 0), HeldKarpTsp.solve(one).tour());
    }

    @Test
    @DisplayName("Randomized (seed 11): 2-approximation bound and MST lower bound versus Held-Karp")
    void guarantee() {
        Random r = new Random(11);
        for (int t = 0; t < 150; t++) {
            int n = 3 + r.nextInt(8);
            double[][] d = random(r, n);
            TspResult a = MetricTspApproximation.solve(d);
            TspResult opt = HeldKarpTsp.solve(d);
            assertTrue(a.length() <= 2 * opt.length() + 1e-6, "approx " + a.length() + " opt " + opt.length());
            assertTrue(a.mstWeight() <= opt.length() + 1e-6);
            assertTrue(a.length() >= opt.length() - 1e-6);
            assertEquals(n + 1, a.tour().size());
            assertEquals(n, new HashSet<>(a.tour()).size());
            assertEquals(n + 0, opt.tour().subList(0, n).stream().distinct().count());
            TspResult nn = NearestNeighbourTsp.solve(d);
            assertTrue(nn.length() >= opt.length() - 1e-6);
        }
    }

    @Test
    @DisplayName("Non-metric, asymmetric and malformed matrices are rejected")
    void invalid() {
        double[][] nonMetric = {{0, 1, 10}, {1, 0, 1}, {10, 1, 0}};
        assertThrows(IllegalArgumentException.class, () -> MetricTspApproximation.solve(nonMetric));
        double[][] asym = {{0, 1}, {2, 0}};
        assertThrows(IllegalArgumentException.class, () -> MetricTspApproximation.solve(asym));
        assertThrows(IllegalArgumentException.class, () -> MetricTspApproximation.solve(new double[0][0]));
        assertThrows(IllegalArgumentException.class, () -> MetricTspApproximation.solve(new double[][]{{0, 1}}));
        assertThrows(IllegalArgumentException.class, () -> HeldKarpTsp.solve(new double[14][14]));
    }

    @Test
    @DisplayName("Haversine x 1.3: Colaba to Dadar is a plausible road estimate and the matrix is a metric")
    void geo() {
        double km = GeoDistance.roadKm(18.9067, 72.8147, 19.0178, 72.8478);
        assertTrue(km > 12 && km < 20, "got " + km);
        assertEquals(0.0, GeoDistance.haversine(19, 72, 19, 72), 1e-12);
        double[][] m = GeoDistance.matrix(new double[]{18.9067, 19.0178, 19.0726, 19.1197},
                new double[]{72.8147, 72.8478, 72.8845, 72.8464});
        MetricTspApproximation.validateMetric(m);
        assertThrows(IllegalArgumentException.class, () -> GeoDistance.matrix(new double[]{95}, new double[]{0}));
    }

    @Test
    @DisplayName("Preorder is deterministic: same input gives same tour; MST has n-1 edges")
    void deterministic() {
        double[][] d = random(new Random(3), 9);
        TspResult a = MetricTspApproximation.solve(d);
        TspResult b = MetricTspApproximation.solve(d);
        assertEquals(a.tour(), b.tour());
        assertEquals(8, a.mstEdges().size());
        assertEquals(9, a.preorder().size());
    }
}
