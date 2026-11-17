package com.cityshield.algorithm.heap;

/**
 * Immutable result of {@link PriorityCalculator#calculate}.
 *
 * @param score          final priority P in [0, 100], rounded to 2 decimals
 * @param severityFactor S, clamped to [0, 1]
 * @param urgencyFactor  U, clamped to [0, 1]
 * @param affectedFactor A, clamped to [0, 1]
 * @param resourceFactor R, clamped to [0, 1]
 */
public record PriorityResult(double score, double severityFactor, double urgencyFactor,
                             double affectedFactor, double resourceFactor) {
}
