package com.cityshield.benchmark;

/** One measured data point: wall-clock milliseconds of a variant at an input size. */
public record BenchmarkRow(String algorithm, String variant, int inputSize, double medianMs, double minMs, double maxMs,
                           int repetitions, String note) {
}
