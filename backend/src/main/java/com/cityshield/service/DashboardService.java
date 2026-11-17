package com.cityshield.service;

import static com.cityshield.util.Json.obj;
import static com.cityshield.util.Json.round;

import com.cityshield.entity.*;
import com.cityshield.repository.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;

/** Aggregates the Command Dashboard summary (Module 8). Read-only. */
@Service
public class DashboardService {
    private static final double COVERAGE_RADIUS_KM = 4.0;
    private final EmergencyIncidentRepository incidents;
    private final ResponseTeamRepository teams;
    private final HospitalRepository hospitals;
    private final EmergencyCentreRepository centres;
    private final IncidentMapper mapper;
    private final AnalyticsService analytics;
    private final ResourceService resources;
    private final NetworkService network;
    private final CoverageService coverage;

    public DashboardService(EmergencyIncidentRepository incidents, ResponseTeamRepository teams, HospitalRepository hospitals,
                            EmergencyCentreRepository centres, IncidentMapper mapper, AnalyticsService analytics,
                            ResourceService resources, NetworkService network, CoverageService coverage) {
        this.incidents = incidents;
        this.teams = teams;
        this.hospitals = hospitals;
        this.centres = centres;
        this.mapper = mapper;
        this.analytics = analytics;
        this.resources = resources;
        this.network = network;
        this.coverage = coverage;
    }

    public Map<String, Object> summary() {
        List<EmergencyIncident> all = incidents.findAll();
        IncidentMapper.Ctx ctx = mapper.ctx(null);

        int active = 0;
        int critical = 0;
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (String s : List.of("REPORTED", "ASSIGNED", "IN_PROGRESS", "RESOLVED", "CANCELLED", "ARCHIVED")) {
            byStatus.put(s, 0);
        }
        Map<String, Integer> byCategory = new TreeMap<>();
        int[] buckets = new int[5];
        Double p90 = analytics.priorityThreshold(90);
        int highRisk = 0;
        LocalDate latest = null;
        for (EmergencyIncident i : all) {
            byStatus.merge(i.status, 1, Integer::sum);
            byCategory.merge(String.valueOf(ctx.typeCode.get(i.typeId)), 1, Integer::sum);
            if (i.reportedAt != null && (latest == null || i.reportedAt.toLocalDate().isAfter(latest))) {
                latest = i.reportedAt.toLocalDate();
            }
            if (IncidentMapper.isActive(i.status)) {
                active++;
                if (i.severity != null && i.severity == 5) {
                    critical++;
                }
                double score = mapper.score(i, ctx).score();
                buckets[Math.min(4, (int) (score / 20.0))]++;
                if (p90 != null && score >= p90) {
                    highRisk++;
                }
            }
        }

        int avail = 0;
        int busy = 0;
        int off = 0;
        List<ResponseTeam> allTeams = teams.findAll();
        for (ResponseTeam t : allTeams) {
            switch (t.availability) {
                case "AVAILABLE" -> avail++;
                case "BUSY" -> busy++;
                default -> off++;
            }
        }

        long beds = 0;
        long bedsFree = 0;
        List<Hospital> hs = hospitals.findAll();
        for (Hospital h : hs) {
            beds += h.bedCapacity;
            bedsFree += h.bedsAvailable;
        }

        int existing = 0;
        int proposed = 0;
        int unavailable = 0;
        for (EmergencyCentre c : centres.findAll()) {
            switch (c.status) {
                case "EXISTING" -> existing++;
                case "PROPOSED" -> proposed++;
                default -> unavailable++;
            }
        }

        NetworkService.Analysis a = network.analyze(null, true);
        int[] cov = coverage.existingCoverage(COVERAGE_RADIUS_KM);

        List<EmergencyIncident> sorted = new ArrayList<>(all);
        sorted.sort(Comparator.comparing((EmergencyIncident i) -> i.reportedAt).reversed()
                .thenComparing(i -> i.incidentId, Comparator.reverseOrder()));
        List<Map<String, Object>> recent = new ArrayList<>();
        for (int k = 0; k < Math.min(10, sorted.size()); k++) {
            recent.add(mapper.dto(sorted.get(k), ctx));
        }

        List<Map<String, Object>> dist = new ArrayList<>();
        String[] labels = {"0-20", "20-40", "40-60", "60-80", "80-100"};
        for (int k = 0; k < 5; k++) {
            dist.add(obj("label", labels[k], "count", buckets[k]));
        }
        List<Map<String, Object>> statusList = new ArrayList<>();
        byStatus.forEach((k, v) -> statusList.add(obj("status", k, "count", v)));
        List<Map<String, Object>> catList = new ArrayList<>();
        byCategory.forEach((k, v) -> catList.add(obj("category", k, "count", v)));

        // Anchor the 14-day window to the newest incident so the seeded data set always renders.
        List<Map<String, Object>> perDay = new ArrayList<>();
        if (latest != null) {
            Map<LocalDate, Integer> counts = new TreeMap<>();
            for (int d = 13; d >= 0; d--) {
                counts.put(latest.minusDays(d), 0);
            }
            for (EmergencyIncident i : all) {
                if (i.reportedAt != null) {
                    counts.computeIfPresent(i.reportedAt.toLocalDate(), (k, v) -> v + 1);
                }
            }
            counts.forEach((k, v) -> perDay.add(obj("date", k.toString(), "count", v)));
        }

        int blockedRoads = a.blocked().size();
        return obj("totalIncidents", all.size(), "activeIncidents", active, "criticalIncidents", critical,
                "highRiskIncidents", highRisk, "highRiskThreshold", p90 == null ? null : round(p90, 2),
                "teams", obj("total", allTeams.size(), "available", avail, "busy", busy, "offDuty", off),
                "hospitals", obj("count", hs.size(), "totalBeds", beds, "availableBeds", bedsFree,
                        "occupancyPct", beds == 0 ? 0.0 : round(100.0 * (beds - bedsFree) / beds, 1)),
                "centres", obj("existing", existing, "proposed", proposed, "unavailable", unavailable),
                "zones", a.zones().size(), "inventory", resources.summary(),
                "connectivity", obj("componentCount", a.result().componentCount(),
                        "isolatedZones", a.result().isolatedZones().size(), "blockedRoads", blockedRoads),
                "coverage", obj("radiusKm", COVERAGE_RADIUS_KM, "coveredZones", cov[0], "totalZones", cov[1],
                        "coveragePct", cov[1] == 0 ? 0.0 : round(100.0 * cov[0] / cov[1], 1)),
                "recentIncidents", recent, "priorityDistribution", dist, "statusBreakdown", statusList,
                "categoryBreakdown", catList, "incidentsPerDay", perDay);
    }
}
