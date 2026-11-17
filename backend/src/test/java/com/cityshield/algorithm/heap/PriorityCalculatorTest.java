package com.cityshield.algorithm.heap;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriorityCalculatorTest {

    private final PriorityCalculator calc = new PriorityCalculator();

    @Test
    @DisplayName("Maximum inputs give exactly 100")
    void maxScore() {
        assertEquals(100.0, calc.calculate(5, 5, 5000, 1.0).score());
    }

    @Test
    @DisplayName("Minimum inputs give 0")
    void minScore() {
        assertEquals(0.0, calc.calculate(0, 0, 0, 0).score());
    }

    @Test
    @DisplayName("Worked example: S=4,U=3,affected=2500,R=0.5 -> 64.0")
    void workedExample() {
        // 100*(0.4*0.8 + 0.25*0.6 + 0.2*0.5 + 0.15*0.5) = 100*(0.32+0.15+0.10+0.075) = 64.5
        PriorityResult r = calc.calculate(4, 3, 2500, 0.5);
        assertEquals(64.5, r.score());
        assertEquals(0.8, r.severityFactor(), 1e-12);
        assertEquals(0.6, r.urgencyFactor(), 1e-12);
        assertEquals(0.5, r.affectedFactor(), 1e-12);
        assertEquals(0.5, r.resourceFactor(), 1e-12);
    }

    @Test
    @DisplayName("Out-of-range inputs are clamped to [0,1]")
    void clamping() {
        PriorityResult r = calc.calculate(10, -3, 99999, 7);
        assertEquals(1.0, r.severityFactor());
        assertEquals(0.0, r.urgencyFactor());
        assertEquals(1.0, r.affectedFactor());
        assertEquals(1.0, r.resourceFactor());
        assertEquals(75.0, r.score(), 1e-9); // 40 + 0 + 20 + 15
        assertEquals(0.0, calc.calculate(Double.NaN, 0, -5, Double.NaN).score());
    }

    @Test
    @DisplayName("Score is rounded to 2 decimals")
    void rounding() {
        double s = calc.calculate(3, 2, 333, 0.123456).score();
        assertEquals(Math.round(s * 100) / 100.0, s);
        assertEquals(String.valueOf(s).length() - String.valueOf(s).indexOf('.') - 1 <= 2, true);
    }

    @Test
    @DisplayName("Weights must sum to 1 and be non-negative, custom weights work")
    void weights() {
        assertThrows(IllegalArgumentException.class, () -> new PriorityCalculator(0.5, 0.5, 0.5, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new PriorityCalculator(1.2, -0.2, 0, 0));
        PriorityCalculator onlySeverity = new PriorityCalculator(1, 0, 0, 0);
        assertEquals(60.0, onlySeverity.calculate(3, 5, 5000, 1).score());
        assertEquals(0.40, calc.getWeightSeverity());
    }

    @Test
    @DisplayName("resourceShortfall: share of required units not covered by stock")
    void shortfall() {
        assertEquals(0.0, PriorityCalculator.resourceShortfall(new long[]{}, new long[]{}));
        assertEquals(0.0, PriorityCalculator.resourceShortfall(new long[]{0, 0}, new long[]{5, 5}));
        assertEquals(0.0, PriorityCalculator.resourceShortfall(new long[]{5, 5}, new long[]{9, 5}));
        assertEquals(0.5, PriorityCalculator.resourceShortfall(new long[]{10, 10}, new long[]{10, 0}), 1e-12);
        assertEquals(1.0, PriorityCalculator.resourceShortfall(new long[]{4}, new long[]{-3}), 1e-12);
        assertEquals(0.25, PriorityCalculator.resourceShortfall(new long[]{10, 10}, new long[]{5, 20}), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> PriorityCalculator.resourceShortfall(new long[]{1}, new long[]{}));
        assertThrows(IllegalArgumentException.class, () -> PriorityCalculator.resourceShortfall(new long[]{-1}, new long[]{1}));
        assertThrows(IllegalArgumentException.class, () -> PriorityCalculator.resourceShortfall(null, new long[]{1}));
    }

    @Test
    @DisplayName("Higher severity gives a higher score, all else equal")
    void monotonic() {
        double prev = -1;
        for (int s = 1; s <= 5; s++) {
            double p = calc.calculate(s, 3, 100, 0.2).score();
            assertTrue(p > prev);
            prev = p;
        }
    }
}
