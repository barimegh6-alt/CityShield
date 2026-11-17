package com.cityshield.service;

import static com.cityshield.util.Json.obj;

import com.cityshield.entity.*;
import com.cityshield.repository.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Read-only reference data, health and database consistency checks. */
@Service
public class ReferenceService {
    static final String DISCLAIMER = "Academic simulation with synthetic data - not a live emergency dispatch service.";
    private static final String[] TABLES = {"city_zones", "road_connections", "distribution_edges", "emergency_centres", "hospitals",
            "emergency_types", "emergency_type_team_compat", "resource_types", "resource_inventory", "response_teams",
            "emergency_incidents", "incident_resource_requirements", "incident_status_history", "incident_logs",
            "incident_assignments", "resource_allocations", "relief_destinations", "delivery_routes", "delivery_route_stops",
            "algorithm_execution_logs", "benchmark_results"};

    private final NetworkService network;
    private final RoadConnectionRepository roadRepo;
    private final EmergencyCentreRepository centres;
    private final HospitalRepository hospitals;
    private final ResourceTypeRepository types;
    private final ReliefDestinationRepository destinations;
    private final JdbcTemplate jdbc;

    public ReferenceService(NetworkService network, RoadConnectionRepository roadRepo, EmergencyCentreRepository centres, HospitalRepository hospitals,
                            ResourceTypeRepository types, ReliefDestinationRepository destinations, JdbcTemplate jdbc) {
        this.network = network;
        this.roadRepo = roadRepo;
        this.centres = centres;
        this.hospitals = hospitals;
        this.types = types;
        this.destinations = destinations;
        this.jdbc = jdbc;
    }

    public Map<String, Object> health() {
        String db;
        try {
            jdbc.queryForObject("SELECT 1", Integer.class);
            db = "UP";
        } catch (Exception e) {
            db = "DOWN";
        }
        return obj("status", "UP".equals(db) ? "UP" : "DEGRADED", "service", "CityShield", "database", db,
                "time", LocalDateTime.now().withNano(0).toString(), "disclaimer", DISCLAIMER);
    }

    public List<Map<String, Object>> zones() {
        return network.zonesOrdered().stream().map(Dto::zone).collect(Collectors.toList());
    }

    public List<Map<String, Object>> roads() {
        Map<Long, CityZone> zm = network.zoneMap();
        List<RoadConnection> list = new ArrayList<>(roadRepo.findAll());
        list.sort((a, b) -> Long.compare(a.roadId, b.roadId));
        return list.stream().map(r -> Dto.road(r, zm)).collect(Collectors.toList());
    }

    public List<Map<String, Object>> centres() {
        Map<Long, CityZone> zm = network.zoneMap();
        List<EmergencyCentre> list = new ArrayList<>(centres.findAll());
        list.sort((a, b) -> Long.compare(a.centreId, b.centreId));
        return list.stream().map(c -> Dto.centre(c, zm)).collect(Collectors.toList());
    }

    public List<Map<String, Object>> hospitals() {
        Map<Long, CityZone> zm = network.zoneMap();
        List<Hospital> list = new ArrayList<>(hospitals.findAll());
        list.sort((a, b) -> Long.compare(a.hospitalId, b.hospitalId));
        return list.stream().map(h -> Dto.hospital(h, zm)).collect(Collectors.toList());
    }

    public List<Map<String, Object>> resourceTypes() {
        List<ResourceType> list = new ArrayList<>(types.findAll());
        list.sort((a, b) -> Long.compare(a.resourceTypeId, b.resourceTypeId));
        return list.stream().map(Dto::resourceType).collect(Collectors.toList());
    }

    public List<Map<String, Object>> destinations() {
        Map<Long, CityZone> zm = network.zoneMap();
        List<ReliefDestination> list = new ArrayList<>(destinations.findAll());
        list.sort((a, b) -> Long.compare(a.destinationId, b.destinationId));
        return list.stream().map(d -> Dto.destination(d, zm)).collect(Collectors.toList());
    }

    /** Row counts plus referential and business-rule checks (used as Phase 3/7 verification evidence). */
    public Map<String, Object> dbCheck() {
        Map<String, Object> counts = new LinkedHashMap<>();
        for (String t : TABLES) {
            counts.put(t, jdbc.queryForObject("SELECT COUNT(*) FROM " + t, Long.class));
        }
        List<Map<String, Object>> checks = new ArrayList<>();
        check(checks, "Every road joins two existing zones",
                "SELECT COUNT(*) FROM road_connections r LEFT JOIN city_zones a ON a.zone_id = r.zone_a LEFT JOIN city_zones b ON b.zone_id = r.zone_b WHERE a.zone_id IS NULL OR b.zone_id IS NULL");
        check(checks, "Every incident references an existing zone and type",
                "SELECT COUNT(*) FROM emergency_incidents i LEFT JOIN city_zones z ON z.zone_id = i.zone_id LEFT JOIN emergency_types t ON t.type_id = i.type_id WHERE z.zone_id IS NULL OR t.type_id IS NULL");
        check(checks, "No negative stock and stock never exceeds capacity",
                "SELECT COUNT(*) FROM resource_inventory WHERE quantity_available < 0 OR quantity_available > quantity_capacity");
        check(checks, "No BUSY team without an ACTIVE assignment",
                "SELECT COUNT(*) FROM response_teams t WHERE t.availability = 'BUSY' AND NOT EXISTS (SELECT 1 FROM incident_assignments a WHERE a.team_id = t.team_id AND a.status = 'ACTIVE')");
        check(checks, "No team has more than one ACTIVE assignment",
                "SELECT COUNT(*) FROM (SELECT team_id FROM incident_assignments WHERE status = 'ACTIVE' GROUP BY team_id HAVING COUNT(*) > 1) x");
        check(checks, "Incident codes are unique",
                "SELECT COUNT(*) FROM (SELECT incident_code FROM emergency_incidents GROUP BY incident_code HAVING COUNT(*) > 1) x");
        boolean healthy = checks.stream().allMatch(c -> Boolean.TRUE.equals(c.get("passed")));
        return obj("tables", counts, "checks", checks, "healthy", healthy);
    }

    private void check(List<Map<String, Object>> out, String name, String violationSql) {
        Long bad = jdbc.queryForObject(violationSql, Long.class);
        long n = bad == null ? 0 : bad;
        out.add(obj("name", name, "passed", n == 0, "detail", n == 0 ? "OK" : n + " violating rows"));
    }
}
