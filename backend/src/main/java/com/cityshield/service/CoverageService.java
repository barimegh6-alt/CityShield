package com.cityshield.service;

import static com.cityshield.util.Json.obj;
import static com.cityshield.util.Json.round;

import com.cityshield.algorithm.quickselect.RandomizedQuickselect;
import com.cityshield.algorithm.setcover.*;
import com.cityshield.algorithm.tsp.GeoDistance;
import com.cityshield.entity.*;
import com.cityshield.exception.ApiException;
import com.cityshield.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Emergency centre coverage planner (Module 6) using weighted greedy Set Cover with an exact comparison. */
@Service
public class CoverageService {
    private final EmergencyCentreRepository centreRepo;
    private final EmergencyIncidentRepository incidents;
    private final NetworkService network;
    private final IncidentMapper mapper;
    private final ExecutionLogService exec;

    public CoverageService(EmergencyCentreRepository centreRepo, EmergencyIncidentRepository incidents, NetworkService network,
                           IncidentMapper mapper, ExecutionLogService exec) {
        this.centreRepo = centreRepo;
        this.incidents = incidents;
        this.network = network;
        this.mapper = mapper;
        this.exec = exec;
    }

    private static double km(CityZone a, CityZone b) {
        return GeoDistance.haversine(a.latitude, a.longitude, b.latitude, b.longitude);
    }

    /** Coverage of EXISTING centres over all zones for a uniform radius: {covered, total}. */
    public int[] existingCoverage(double radiusKm) {
        List<CityZone> zones = network.zonesOrdered();
        Map<Long, CityZone> zm = network.zoneMap();
        boolean[] cov = new boolean[zones.size()];
        for (EmergencyCentre c : centreRepo.findAll()) {
            if (!"EXISTING".equals(c.status)) {
                continue;
            }
            for (int i = 0; i < zones.size(); i++) {
                if (km(zm.get(c.zoneId), zones.get(i)) <= radiusKm) {
                    cov[i] = true;
                }
            }
        }
        int n = 0;
        for (boolean b : cov) {
            if (b) {
                n++;
            }
        }
        return new int[]{n, zones.size()};
    }

    public Map<String, Object> setCover(Double radiusKm, String universeType, Integer maxNewCentres) {
        long t0 = System.nanoTime();
        if (radiusKm != null && (Double.isNaN(radiusKm) || radiusKm <= 0 || radiusKm > 50)) {
            throw ApiException.badRequest("radiusKm must be in (0, 50]");
        }
        if (maxNewCentres != null && maxNewCentres < 0) {
            throw ApiException.badRequest("maxNewCentres must be >= 0");
        }
        String ut = universeType == null ? "ALL" : universeType.toUpperCase(Locale.ROOT);
        if (!ut.equals("ALL") && !ut.equals("ACTIVE_INCIDENT_ZONES")) {
            throw ApiException.badRequest("universe must be ALL or ACTIVE_INCIDENT_ZONES");
        }
        Map<Long, CityZone> zm = network.zoneMap();
        List<EmergencyIncident> active = incidents.findByStatusIn(IncidentMapper.ACTIVE);
        Set<Long> activeZones = new HashSet<>();
        for (EmergencyIncident i : active) {
            activeZones.add(i.zoneId);
        }
        List<CityZone> universe = new ArrayList<>();
        for (CityZone z : network.zonesOrdered()) {
            if (ut.equals("ALL") || activeZones.contains(z.zoneId)) {
                universe.add(z);
            }
        }
        if (universe.isEmpty()) {
            throw ApiException.infeasible("The universe is empty: no zones to cover", null);
        }
        List<EmergencyCentre> centres = new ArrayList<>(centreRepo.findAll());
        centres.sort(Comparator.comparingLong(c -> c.centreId));
        Map<Long, EmergencyCentre> byId = new HashMap<>();
        List<CoverSet> sets = new ArrayList<>();
        Set<Long> forced = new HashSet<>();
        for (EmergencyCentre c : centres) {
            byId.put(c.centreId, c);
            if ("UNAVAILABLE".equals(c.status)) {
                continue;
            }
            double r = radiusKm != null ? radiusKm : c.coverageRadiusKm;
            List<Integer> el = new ArrayList<>();
            for (int i = 0; i < universe.size(); i++) {
                if (km(zm.get(c.zoneId), universe.get(i)) <= r) {
                    el.add(i);
                }
            }
            sets.add(new CoverSet(c.centreId, c.cost, el.stream().mapToInt(Integer::intValue).toArray()));
            if ("EXISTING".equals(c.status)) {
                forced.add(c.centreId);
            }
        }
        // coverage of existing centres only
        boolean[] ex = new boolean[universe.size()];
        for (CoverSet s : sets) {
            if (forced.contains(s.id())) {
                for (int e : s.elements()) {
                    ex[e] = true;
                }
            }
        }
        CoverResult greedy = GreedySetCover.solve(universe.size(), sets, forced, maxNewCentres);
        int candidates = sets.size() - forced.size();
        Map<String, Object> exact;
        double exactCost = Double.NaN;
        if (maxNewCentres != null) {
            exact = obj("available", false, "reason", "Budget-limited run: the unconstrained exact optimum is not comparable",
                    "centreIds", List.of(), "totalCost", null, "candidateCount", candidates, "subsetsChecked", 0);
        } else if (candidates > ExactSetCover.MAX_SETS) {
            exact = obj("available", false, "reason", "Too many candidate centres (" + candidates + ") for exhaustive search",
                    "centreIds", List.of(), "totalCost", null, "candidateCount", candidates, "subsetsChecked", 0);
        } else {
            CoverResult er = ExactSetCover.solve(universe.size(), sets, forced);
            exactCost = er.totalCost();
            List<Long> ids = new ArrayList<>();
            for (long id : er.selectedIds()) {
                if (!forced.contains(id)) {
                    ids.add(id);
                }
            }
            exact = obj("available", true, "centreIds", ids, "totalCost", round(er.totalCost(), 2), "candidateCount", candidates,
                    "subsetsChecked", er.subsetsChecked(), "coveredZones", er.coveredCount());
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        List<Long> greedyIds = new ArrayList<>();
        for (CoverStep s : greedy.steps()) {
            EmergencyCentre c = byId.get(s.setId());
            List<Long> zid = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int e : s.newlyCovered()) {
                zid.add(universe.get(e).zoneId);
                names.add(universe.get(e).name);
            }
            if (!s.forced()) {
                greedyIds.add(s.setId());
            }
            steps.add(obj("step", s.step(), "centreId", c.centreId, "centreName", c.name, "centreStatus", c.status,
                    "zoneName", zm.get(c.zoneId).name, "newlyCoveredZoneIds", zid, "newlyCoveredNames", names,
                    "cost", s.forced() ? 0.0 : c.cost, "ratio", round(s.ratio(), 3), "forced", s.forced()));
        }
        Set<Integer> unc = new HashSet<>(greedy.uncovered());
        boolean[] fin = new boolean[universe.size()];
        for (int i = 0; i < fin.length; i++) {
            fin[i] = !unc.contains(i);
        }
        // suggested sites for what is still uncovered after the greedy run
        double sr = radiusKm != null ? radiusKm : 4.0;
        Set<Long> hosting = new HashSet<>();
        for (EmergencyCentre c : centres) {
            hosting.add(c.zoneId);
        }
        List<Map<String, Object>> suggested = new ArrayList<>();
        if (!unc.isEmpty()) {
            List<Object[]> cands = new ArrayList<>();
            for (CityZone z : network.zonesOrdered()) {
                if (hosting.contains(z.zoneId)) {
                    continue;
                }
                int would = 0;
                for (int u : unc) {
                    if (km(z, universe.get(u)) <= sr) {
                        would++;
                    }
                }
                if (would > 0) {
                    cands.add(new Object[]{z, would});
                }
            }
            cands.sort((a, b) -> Integer.compare((Integer) b[1], (Integer) a[1]));
            for (int k = 0; k < Math.min(5, cands.size()); k++) {
                CityZone z = (CityZone) cands.get(k)[0];
                suggested.add(obj("zoneId", z.zoneId, "zoneName", z.name, "latitude", z.latitude, "longitude", z.longitude,
                        "wouldCover", cands.get(k)[1]));
            }
        }
        // zones above the Quickselect P90 of zone risk (sum of active-incident priority scores) that are still uncovered
        IncidentMapper.Ctx ctx = mapper.ctx(null);
        Map<Long, Double> risk = new HashMap<>();
        for (EmergencyIncident i : active) {
            risk.merge(i.zoneId, mapper.score(i, ctx).score(), Double::sum);
        }
        List<Map<String, Object>> highRisk = new ArrayList<>();
        if (!risk.isEmpty()) {
            double[] vals = risk.values().stream().mapToDouble(Double::doubleValue).toArray();
            double p90 = new RandomizedQuickselect(42).percentile(vals, 90).value();
            for (int u : unc) {
                Double rs = risk.get(universe.get(u).zoneId);
                if (rs != null && rs >= p90) {
                    highRisk.add(obj("zoneId", universe.get(u).zoneId, "zoneName", universe.get(u).name, "riskScore", round(rs, 2)));
                }
            }
            highRisk.sort((a, b) -> Double.compare((Double) b.get("riskScore"), (Double) a.get("riskScore")));
        }
        int n = universe.size();
        Map<String, Object> out = obj("radiusKm", radiusKm, "universe", ut, "universeSize", n,
                "existing", cov(universe, ex), "steps", steps, "final", cov(universe, fin),
                "greedy", obj("centreIds", greedyIds, "totalCost", round(greedy.totalCost(), 2)), "exact", exact,
                "approximationRatio", Double.isNaN(exactCost) || exactCost <= 0 ? null : round(greedy.totalCost() / exactCost, 3),
                "note", "Greedy set cover is an approximation with factor at most H(n) = ln(n) + 1. It is NOT guaranteed to be optimal; "
                        + "the exact optimum is found by exhaustive search only on small candidate sets.",
                "suggestedSites", suggested, "highRiskUncoveredZones", highRisk);
        List<Map<String, Object>> cl = new ArrayList<>();
        for (EmergencyCentre c : centres) {
            cl.add(Dto.centre(c, zm));
        }
        List<Map<String, Object>> zl = new ArrayList<>();
        for (CityZone z : universe) {
            zl.add(Dto.zone(z));
        }
        out.put("centres", cl);
        out.put("zones", zl);
        out.put("execution", exec.record("GREEDY_SET_COVER", t0, n + sets.size(), greedyIds.size() + " new centres chosen"));
        return out;
    }

    private static Map<String, Object> cov(List<CityZone> universe, boolean[] covered) {
        List<Long> yes = new ArrayList<>();
        List<Long> no = new ArrayList<>();
        for (int i = 0; i < covered.length; i++) {
            (covered[i] ? yes : no).add(universe.get(i).zoneId);
        }
        return obj("coveredZoneIds", yes, "uncoveredZoneIds", no, "coveragePct", round(100.0 * yes.size() / universe.size(), 1));
    }
}
