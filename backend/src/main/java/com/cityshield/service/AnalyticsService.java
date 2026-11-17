package com.cityshield.service;

import static com.cityshield.util.Json.obj;
import static com.cityshield.util.Json.round;

import com.cityshield.algorithm.quickselect.RandomizedQuickselect;
import com.cityshield.algorithm.quickselect.SelectionResult;
import com.cityshield.entity.EmergencyIncident;
import com.cityshield.exception.ApiException;
import com.cityshield.repository.EmergencyIncidentRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Percentile analytics (Module 3 / 8) powered by seeded randomized Quickselect (CO5). */
@Service
public class AnalyticsService {
    private static final List<String> METRICS = List.of("PRIORITY", "SEVERITY", "AFFECTED", "URGENCY");
    private final EmergencyIncidentRepository incidents;
    private final IncidentMapper mapper;
    private final ExecutionLogService exec;

    public AnalyticsService(EmergencyIncidentRepository incidents, IncidentMapper mapper, ExecutionLogService exec) {
        this.incidents = incidents;
        this.mapper = mapper;
        this.exec = exec;
    }

    private record Sample(List<EmergencyIncident> list, double[] values, IncidentMapper.Ctx ctx) {
    }

    private Sample sample(String metric, String scope) {
        if (!METRICS.contains(metric)) {
            throw ApiException.badRequest("metric must be one of " + METRICS);
        }
        boolean active = !"ALL".equals(scope);
        List<EmergencyIncident> list = new ArrayList<>();
        for (EmergencyIncident i : incidents.findAll()) {
            if (!active || IncidentMapper.isActive(i.status)) {
                list.add(i);
            }
        }
        if (list.isEmpty()) {
            throw ApiException.infeasible("No incidents in scope " + (active ? "ACTIVE" : "ALL") + " - nothing to analyse", null);
        }
        IncidentMapper.Ctx c = mapper.ctx(null);
        double[] v = new double[list.size()];
        for (int k = 0; k < v.length; k++) {
            EmergencyIncident i = list.get(k);
            v[k] = switch (metric) {
                case "PRIORITY" -> mapper.score(i, c).score();
                case "SEVERITY" -> i.severity;
                case "URGENCY" -> i.urgency;
                default -> i.affectedPeople;
            };
        }
        return new Sample(list, v, c);
    }

    private static void checkPercentile(double p) {
        if (Double.isNaN(p) || p <= 0 || p > 100) {
            throw ApiException.badRequest("percentile must be in (0, 100] but was " + p);
        }
    }

    /** P90 (or other percentile) of priority over active incidents; null when there are none. */
    public Double priorityThreshold(double percentile) {
        try {
            Sample s = sample("PRIORITY", "ACTIVE");
            return new RandomizedQuickselect(42).percentile(s.values(), percentile).value();
        } catch (ApiException e) {
            return null;
        }
    }

    public Map<String, Object> percentile(String metric, double percentile, long seed, String scope) {
        long t0 = System.nanoTime();
        String m = metric == null ? "PRIORITY" : metric.toUpperCase(Locale.ROOT);
        String sc = scope == null ? "ACTIVE" : scope.toUpperCase(Locale.ROOT);
        checkPercentile(percentile);
        Sample s = sample(m, sc);
        long q0 = System.nanoTime();
        SelectionResult r = new RandomizedQuickselect(seed).percentile(s.values(), percentile);
        double qMs = (System.nanoTime() - q0) / 1e6;
        long b0 = System.nanoTime();
        double[] sorted = s.values().clone();
        Arrays.sort(sorted); // baseline only: used to verify and to compare cost, never to answer the query
        double sortValue = sorted[r.k() - 1];
        double sMs = (System.nanoTime() - b0) / 1e6;

        List<Integer> high = new ArrayList<>();
        for (int k = 0; k < s.values().length; k++) {
            if (s.values()[k] >= r.value()) {
                high.add(k);
            }
        }
        high.sort((a, b) -> Double.compare(s.values()[b], s.values()[a]));
        List<Map<String, Object>> top = new ArrayList<>();
        for (int k = 0; k < Math.min(25, high.size()); k++) {
            top.add(mapper.dto(s.list().get(high.get(k)), s.ctx()));
        }
        Map<String, Object> out = obj("metric", m, "percentile", percentile, "seed", seed, "scope", sc, "n", s.values().length,
                "k", r.k(), "threshold", r.value(), "highRiskCount", high.size(), "highRiskIncidents", top,
                "distribution", distribution(m, s.values()),
                "quickselect", obj("comparisons", r.comparisons(), "pivotSelections", r.pivotSelections(),
                        "partitionRounds", r.partitionRounds(), "durationMs", round(qMs, 3)),
                "sortBaseline", obj("durationMs", round(sMs, 3), "agrees", sortValue == r.value()));
        out.put("execution", exec.record("QUICKSELECT", t0, s.values().length, "P" + percentile + " of " + m + " = " + r.value()));
        return out;
    }

    public Map<String, Object> sweep(String metric, long seed, String scope) {
        long t0 = System.nanoTime();
        String m = metric == null ? "PRIORITY" : metric.toUpperCase(Locale.ROOT);
        String sc = scope == null ? "ACTIVE" : scope.toUpperCase(Locale.ROOT);
        Sample s = sample(m, sc);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (double p : new double[]{50, 75, 90, 95, 99}) {
            SelectionResult r = new RandomizedQuickselect(seed).percentile(s.values(), p);
            rows.add(obj("percentile", p, "threshold", r.value(), "k", r.k(), "comparisons", r.comparisons()));
        }
        List<Map<String, Object>> runs = new ArrayList<>();
        for (long sd = 1; sd <= 5; sd++) {
            SelectionResult r = new RandomizedQuickselect(sd).percentile(s.values(), 90);
            runs.add(obj("seed", sd, "threshold", r.value(), "comparisons", r.comparisons(), "pivotSelections", r.pivotSelections()));
        }
        Map<String, Object> out = obj("metric", m, "scope", sc, "n", s.values().length, "rows", rows, "seedRuns", runs);
        out.put("execution", exec.record("QUICKSELECT_SWEEP", t0, s.values().length, "percentile sweep of " + m));
        return out;
    }

    private static List<Map<String, Object>> distribution(String metric, double[] v) {
        List<Map<String, Object>> out = new ArrayList<>();
        switch (metric) {
            case "PRIORITY" -> {
                int[] b = new int[5];
                for (double x : v) {
                    b[Math.min(4, (int) (x / 20))]++;
                }
                String[] l = {"0-20", "20-40", "40-60", "60-80", "80-100"};
                for (int k = 0; k < 5; k++) {
                    out.add(obj("label", l[k], "count", b[k]));
                }
            }
            case "AFFECTED" -> {
                int[] b = new int[5];
                for (double x : v) {
                    b[x == 0 ? 0 : x <= 10 ? 1 : x <= 100 ? 2 : x <= 1000 ? 3 : 4]++;
                }
                String[] l = {"0", "1-10", "11-100", "101-1000", "1000+"};
                for (int k = 0; k < 5; k++) {
                    out.add(obj("label", l[k], "count", b[k]));
                }
            }
            default -> {
                int[] b = new int[6];
                for (double x : v) {
                    b[(int) x]++;
                }
                for (int k = 1; k <= 5; k++) {
                    out.add(obj("label", String.valueOf(k), "count", b[k]));
                }
            }
        }
        return out;
    }
}
