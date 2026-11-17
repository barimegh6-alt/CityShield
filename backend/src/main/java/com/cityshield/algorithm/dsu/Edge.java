package com.cityshield.algorithm.dsu;

/**
 * Undirected road between two zone indices.
 *
 * @param id road identifier (unique within one analysis)
 * @param a  zone index of one endpoint, in [0, zoneCount)
 * @param b  zone index of the other endpoint, in [0, zoneCount)
 */
public record Edge(long id, int a, int b) {
}
