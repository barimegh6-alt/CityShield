package com.cityshield.service;

import static com.cityshield.util.Json.obj;
import static com.cityshield.util.Json.round;

import com.cityshield.algorithm.heap.PriorityCalculator;
import com.cityshield.algorithm.heap.PriorityResult;
import com.cityshield.entity.*;
import com.cityshield.repository.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Computes incident priority scores and builds IncidentDto maps.
 *
 * <p>R (resource shortfall) is computed against the stock held AT THE INCIDENT'S OWN ZONE: for each
 * required resource, units not covered by local stock count as missing. This makes R meaningful because
 * demand zones keep little stock, so incidents there that need supplies are more urgent.</p>
 */
@Component
public class IncidentMapper {
    public static final List<String> ACTIVE = List.of("REPORTED", "ASSIGNED", "IN_PROGRESS");
    private static final Map<String, List<String>> NEXT = Map.of(
            "REPORTED", List.of("ASSIGNED", "IN_PROGRESS", "CANCELLED"),
            "ASSIGNED", List.of("IN_PROGRESS", "RESOLVED", "CANCELLED"),
            "IN_PROGRESS", List.of("RESOLVED", "CANCELLED"),
            "RESOLVED", List.of("ARCHIVED"),
            "CANCELLED", List.of("ARCHIVED"),
            "ARCHIVED", List.of());
    private static final PriorityCalculator CALC = new PriorityCalculator();

    private final NetworkService network;
    private final EmergencyTypeRepository typeRepo;
    private final ResourceTypeRepository resRepo;
    private final IncidentResourceRequirementRepository reqRepo;
    private final ResourceInventoryRepository invRepo;

    public IncidentMapper(NetworkService network, EmergencyTypeRepository typeRepo, ResourceTypeRepository resRepo,
                          IncidentResourceRequirementRepository reqRepo, ResourceInventoryRepository invRepo) {
        this.network = network;
        this.typeRepo = typeRepo;
        this.resRepo = resRepo;
        this.reqRepo = reqRepo;
        this.invRepo = invRepo;
    }

    /** Preloaded lookup tables so scoring many incidents does not hit the database per row. */
    public static final class Ctx {
        public Map<Long, CityZone> zones = new HashMap<>();
        public Map<Long, String> typeCode = new HashMap<>();
        public Map<Long, ResourceType> resTypes = new HashMap<>();
        public Map<Long, List<IncidentResourceRequirement>> reqs = new HashMap<>();
        public Map<String, Integer> stock = new HashMap<>();
    }

    public static List<String> allowedNext(String status) {
        return NEXT.getOrDefault(status, List.of());
    }

    public static boolean isActive(String status) {
        return ACTIVE.contains(status);
    }

    /** @param incidentIds incidents whose requirements are needed, or null for all */
    public Ctx ctx(Collection<Long> incidentIds) {
        Ctx c = new Ctx();
        c.zones = network.zoneMap();
        for (EmergencyType t : typeRepo.findAll()) {
            c.typeCode.put(t.typeId, t.code);
        }
        for (ResourceType r : resRepo.findAll()) {
            c.resTypes.put(r.resourceTypeId, r);
        }
        List<IncidentResourceRequirement> reqs = incidentIds == null ? reqRepo.findAll()
                : incidentIds.isEmpty() ? new ArrayList<>() : reqRepo.findByIncidentIdIn(incidentIds);
        for (IncidentResourceRequirement q : reqs) {
            c.reqs.computeIfAbsent(q.incidentId, k -> new ArrayList<>()).add(q);
        }
        for (ResourceInventory inv : invRepo.findAll()) {
            c.stock.put(inv.zoneId + ":" + inv.resourceTypeId, inv.quantityAvailable);
        }
        return c;
    }

    public PriorityResult score(EmergencyIncident i, Ctx c) {
        List<IncidentResourceRequirement> rq = c.reqs.getOrDefault(i.incidentId, List.of());
        long[] required = new long[rq.size()];
        long[] available = new long[rq.size()];
        for (int k = 0; k < rq.size(); k++) {
            required[k] = rq.get(k).quantityRequired;
            available[k] = c.stock.getOrDefault(i.zoneId + ":" + rq.get(k).resourceTypeId, 0);
        }
        double shortfall = PriorityCalculator.resourceShortfall(required, available);
        return CALC.calculate(i.severity, i.urgency, i.affectedPeople, shortfall);
    }

    public Map<String, Object> dto(EmergencyIncident i, Ctx c) {
        PriorityResult p = score(i, c);
        CityZone z = c.zones.get(i.zoneId);
        List<Map<String, Object>> reqs = new ArrayList<>();
        for (IncidentResourceRequirement q : c.reqs.getOrDefault(i.incidentId, List.of())) {
            ResourceType rt = c.resTypes.get(q.resourceTypeId);
            reqs.add(obj("resourceTypeId", q.resourceTypeId, "resourceName", rt == null ? null : rt.name,
                    "unit", rt == null ? null : rt.unit, "quantity", q.quantityRequired));
        }
        return obj("id", i.incidentId, "code", i.incidentCode, "category", c.typeCode.get(i.typeId), "zoneId", i.zoneId,
                "zoneName", z == null ? null : z.name, "latitude", z == null ? null : z.latitude,
                "longitude", z == null ? null : z.longitude, "description", i.description, "severity", i.severity,
                "urgency", i.urgency, "affectedPeople", i.affectedPeople, "status", i.status, "reportedAt", i.reportedAt,
                "priorityScore", p.score(),
                "factors", obj("s", round(p.severityFactor(), 4), "u", round(p.urgencyFactor(), 4),
                        "a", round(p.affectedFactor(), 4), "r", round(p.resourceFactor(), 4)),
                "requirements", reqs, "allowedNextStatuses", allowedNext(i.status));
    }
}
