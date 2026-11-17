package com.cityshield.algorithm.heap;

/**
 * Computes the incident priority score used to feed the {@link IncidentMaxHeap}.
 *
 * <pre>
 *   P = 100 x (wS*S + wU*U + wA*A + wR*R)       default weights 0.40, 0.25, 0.20, 0.15
 *   S = severity / 5
 *   U = urgency / 5
 *   A = min(affectedPeople, 5000) / 5000
 *   R = share of required resource units NOT covered by current stock
 * </pre>
 *
 * <p>All four factors are clamped to [0, 1] before weighting, so P always lies in [0, 100].
 * A higher P means a more urgent incident. The weights must be non-negative and sum to 1
 * (tolerance 1e-9), otherwise the constructor throws {@link IllegalArgumentException}.
 * The calculator is immutable and thread-safe. Each calculation is O(1) time and space;
 * {@link #resourceShortfall(long[], long[])} is O(r) for r required resource types.</p>
 */
public final class PriorityCalculator {

    /** Scale used for severity and urgency (1-5 scale). */
    public static final double SCALE = 5.0;
    /** Affected-people cap used for factor A. */
    public static final double MAX_AFFECTED = 5000.0;
    /** Default weight of severity. */
    public static final double DEFAULT_WEIGHT_SEVERITY = 0.40;
    /** Default weight of urgency. */
    public static final double DEFAULT_WEIGHT_URGENCY = 0.25;
    /** Default weight of affected people. */
    public static final double DEFAULT_WEIGHT_AFFECTED = 0.20;
    /** Default weight of resource shortfall. */
    public static final double DEFAULT_WEIGHT_RESOURCE = 0.15;

    private final double weightSeverity;
    private final double weightUrgency;
    private final double weightAffected;
    private final double weightResource;

    /** Creates a calculator with the approved weights 0.40 / 0.25 / 0.20 / 0.15. */
    public PriorityCalculator() {
        this(DEFAULT_WEIGHT_SEVERITY, DEFAULT_WEIGHT_URGENCY, DEFAULT_WEIGHT_AFFECTED, DEFAULT_WEIGHT_RESOURCE);
    }

    /**
     * Creates a calculator with custom weights.
     *
     * @param weightSeverity weight of S
     * @param weightUrgency  weight of U
     * @param weightAffected weight of A
     * @param weightResource weight of R
     * @throws IllegalArgumentException if a weight is negative/NaN or the weights do not sum to 1
     */
    public PriorityCalculator(double weightSeverity, double weightUrgency,
                              double weightAffected, double weightResource) {
        double[] w = {weightSeverity, weightUrgency, weightAffected, weightResource};
        double sum = 0;
        for (double x : w) {
            if (Double.isNaN(x) || x < 0) {
                throw new IllegalArgumentException("weights must be non-negative numbers");
            }
            sum += x;
        }
        if (Math.abs(sum - 1.0) > 1e-9) {
            throw new IllegalArgumentException("weights must sum to 1 but sum to " + sum);
        }
        this.weightSeverity = weightSeverity;
        this.weightUrgency = weightUrgency;
        this.weightAffected = weightAffected;
        this.weightResource = weightResource;
    }

    /**
     * Calculates the priority of an incident.
     *
     * @param severity         severity on the 1-5 scale (values outside are clamped after scaling)
     * @param urgency          urgency on the 1-5 scale
     * @param affectedPeople   number of affected people (negative treated as 0, capped at 5000)
     * @param resourceShortfall R: share (0..1) of required resource units not covered by stock
     * @return score rounded to 2 decimals and the four clamped factors
     */
    public PriorityResult calculate(double severity, double urgency, double affectedPeople,
                                    double resourceShortfall) {
        double s = clamp01(severity / SCALE);
        double u = clamp01(urgency / SCALE);
        double a = clamp01(Math.min(affectedPeople, MAX_AFFECTED) / MAX_AFFECTED);
        double r = clamp01(resourceShortfall);
        double raw = 100.0 * (weightSeverity * s + weightUrgency * u + weightAffected * a + weightResource * r);
        double score = Math.round(raw * 100.0) / 100.0;
        return new PriorityResult(score, s, u, a, r);
    }

    /**
     * Computes R: the share of required units that current stock does not cover, i.e.
     * {@code sum(max(0, required[i] - available[i])) / sum(required[i])}. Returns 0 when nothing is
     * required. O(r) time, O(1) space.
     *
     * @param required  required units per resource type (non-negative)
     * @param available available stock per resource type (negative treated as 0), same length
     * @return shortfall share in [0, 1]
     * @throws IllegalArgumentException on null arrays, different lengths or negative requirement
     */
    public static double resourceShortfall(long[] required, long[] available) {
        if (required == null || available == null) {
            throw new IllegalArgumentException("required and available must not be null");
        }
        if (required.length != available.length) {
            throw new IllegalArgumentException("required and available must have the same length");
        }
        long totalRequired = 0;
        long missing = 0;
        for (int i = 0; i < required.length; i++) {
            if (required[i] < 0) {
                throw new IllegalArgumentException("required units must be >= 0");
            }
            totalRequired += required[i];
            missing += Math.max(0, required[i] - Math.max(0, available[i]));
        }
        return totalRequired == 0 ? 0.0 : (double) missing / (double) totalRequired;
    }

    /** @return weight of severity */
    public double getWeightSeverity() { return weightSeverity; }

    /** @return weight of urgency */
    public double getWeightUrgency() { return weightUrgency; }

    /** @return weight of affected people */
    public double getWeightAffected() { return weightAffected; }

    /** @return weight of resource shortfall */
    public double getWeightResource() { return weightResource; }

    private static double clamp01(double v) {
        if (Double.isNaN(v) || v < 0) {
            return 0.0;
        }
        return v > 1 ? 1.0 : v;
    }
}
