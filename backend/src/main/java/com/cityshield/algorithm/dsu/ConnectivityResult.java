package com.cityshield.algorithm.dsu;

import java.util.List;

/**
 * Immutable output of {@link ConnectivityAnalyzer#analyze}.
 *
 * @param zoneCount      number of zones
 * @param openEdgeCount  number of edges not blocked
 * @param componentCount number of connected components
 * @param components     components ordered by id
 * @param zoneComponent  component id for each zone index (copy)
 * @param isolatedZones  zone indices of size-1 components, ascending
 * @param criticalEdgeIds ids of open edges (in input order) whose removal increases the component count
 * @param unions         successful unions performed in the main DSU build
 * @param finds          find calls performed in the main DSU build
 */
public record ConnectivityResult(int zoneCount, int openEdgeCount, int componentCount,
                                 List<Component> components, int[] zoneComponent,
                                 List<Integer> isolatedZones, List<Long> criticalEdgeIds,
                                 long unions, long finds) {
    /** Defensive copies for immutability. */
    public ConnectivityResult {
        components = List.copyOf(components);
        zoneComponent = zoneComponent.clone();
        isolatedZones = List.copyOf(isolatedZones);
        criticalEdgeIds = List.copyOf(criticalEdgeIds);
    }

    /** @return a copy of the zone-to-component mapping */
    @Override
    public int[] zoneComponent() {
        return zoneComponent.clone();
    }
}
