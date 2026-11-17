package com.cityshield.service;

import static com.cityshield.util.Json.obj;

import com.cityshield.entity.*;
import java.util.Map;

/** Static mappers from entities to the JSON shapes documented in docs/API_DOCUMENTATION.md. */
public final class Dto {
    private Dto() {
    }

    public static Map<String, Object> zone(CityZone z) {
        return obj("id", z.zoneId, "name", z.name, "zoneIndex", z.zoneIndex, "latitude", z.latitude, "longitude", z.longitude,
                "role", z.zoneRole, "population", z.population);
    }

    public static Map<String, Object> road(RoadConnection r, Map<Long, CityZone> zones) {
        CityZone a = zones.get(r.zoneA);
        CityZone b = zones.get(r.zoneB);
        return obj("id", r.roadId, "zoneA", r.zoneA, "zoneB", r.zoneB, "zoneAName", a == null ? null : a.name,
                "zoneBName", b == null ? null : b.name, "distanceKm", r.distanceKm, "status", r.status);
    }

    public static Map<String, Object> centre(EmergencyCentre c, Map<Long, CityZone> zones) {
        CityZone z = zones.get(c.zoneId);
        return obj("id", c.centreId, "name", c.name, "zoneId", c.zoneId, "zoneName", z == null ? null : z.name,
                "latitude", z == null ? null : z.latitude, "longitude", z == null ? null : z.longitude,
                "coverageRadiusKm", c.coverageRadiusKm, "cost", c.cost, "status", c.status, "centreType", c.centreType);
    }

    public static Map<String, Object> hospital(Hospital h, Map<Long, CityZone> zones) {
        CityZone z = zones.get(h.zoneId);
        return obj("id", h.hospitalId, "name", h.name, "zoneId", h.zoneId, "zoneName", z == null ? null : z.name,
                "latitude", z == null ? null : z.latitude, "longitude", z == null ? null : z.longitude,
                "bedCapacity", h.bedCapacity, "bedsAvailable", h.bedsAvailable);
    }

    public static Map<String, Object> resourceType(ResourceType r) {
        return obj("id", r.resourceTypeId, "name", r.name, "unit", r.unit);
    }

    public static Map<String, Object> destination(ReliefDestination d, Map<Long, CityZone> zones) {
        CityZone z = zones.get(d.zoneId);
        return obj("id", d.destinationId, "zoneId", d.zoneId, "name", z == null ? null : z.name,
                "latitude", z == null ? null : z.latitude, "longitude", z == null ? null : z.longitude, "demandUnits", d.demandUnits);
    }

    public static Map<String, Object> team(ResponseTeam t, Map<Long, CityZone> zones, Map<Long, EmergencyCentre> centres) {
        CityZone z = zones.get(t.baseZoneId);
        EmergencyCentre c = t.centreId == null ? null : centres.get(t.centreId);
        return obj("id", t.teamId, "name", t.name, "teamType", t.teamType, "baseZoneId", t.baseZoneId,
                "baseZoneName", z == null ? null : z.name, "latitude", z == null ? null : z.latitude,
                "longitude", z == null ? null : z.longitude, "centreId", t.centreId, "centreName", c == null ? null : c.name,
                "availability", t.availability, "currentIncidentId", t.currentIncidentId);
    }
}
