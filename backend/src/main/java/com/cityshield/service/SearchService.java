package com.cityshield.service;

import static com.cityshield.util.Json.obj;
import static com.cityshield.util.Json.round;

import com.cityshield.algorithm.kmp.KmpMatcher;
import com.cityshield.algorithm.kmp.MatchResult;
import com.cityshield.algorithm.kmp.NaiveMatcher;
import com.cityshield.algorithm.trie.Trie;
import com.cityshield.algorithm.trie.TrieEntry;
import com.cityshield.algorithm.trie.TrieSearchResult;
import com.cityshield.entity.*;
import com.cityshield.exception.ApiException;
import com.cityshield.repository.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** Search lab (Module 8): Trie autocomplete over places and categories, KMP phrase search over incident text. */
@Service
public class SearchService {
    private final NetworkService network;
    private final HospitalRepository hospitals;
    private final EmergencyCentreRepository centres;
    private final EmergencyTypeRepository types;
    private final EmergencyIncidentRepository incidents;
    private final IncidentLogRepository logs;
    private final ExecutionLogService exec;
    private Trie trie;

    public SearchService(NetworkService network, HospitalRepository hospitals, EmergencyCentreRepository centres,
                         EmergencyTypeRepository types, EmergencyIncidentRepository incidents, IncidentLogRepository logs,
                         ExecutionLogService exec) {
        this.network = network;
        this.hospitals = hospitals;
        this.centres = centres;
        this.types = types;
        this.incidents = incidents;
        this.logs = logs;
        this.exec = exec;
    }

    private synchronized Trie trie() {
        if (trie == null) {
            Trie t = new Trie();
            for (CityZone z : network.zonesOrdered()) {
                t.insert(z.name, "ZONE", z.zoneId);
            }
            for (Hospital h : hospitals.findAll()) {
                t.insert(h.name, "HOSPITAL", h.hospitalId);
            }
            for (EmergencyCentre c : centres.findAll()) {
                t.insert(c.name, "CENTRE", c.centreId);
            }
            for (EmergencyType e : types.findAll()) {
                t.insert(e.name, "CATEGORY", e.typeId);
            }
            trie = t;
        }
        return trie;
    }

    public Map<String, Object> autocomplete(String q, String typesCsv, int limit) {
        long t0 = System.nanoTime();
        if (q == null || q.isBlank()) {
            throw ApiException.badRequest("Query must not be empty");
        }
        if (limit < 1 || limit > 50) {
            throw ApiException.badRequest("limit must be between 1 and 50");
        }
        Set<String> filter = new HashSet<>();
        if (typesCsv != null && !typesCsv.isBlank()) {
            for (String s : typesCsv.split(",")) {
                filter.add(s.trim().toUpperCase(Locale.ROOT));
            }
        }
        TrieSearchResult r = trie().search(q, limit, filter);
        List<Map<String, Object>> sug = new ArrayList<>();
        for (TrieEntry e : r.suggestions()) {
            sug.add(obj("text", e.text(), "type", e.type(), "refId", e.refId()));
        }
        double ms = round((System.nanoTime() - t0) / 1e6, 3);
        exec.record("TRIE_AUTOCOMPLETE", t0, trie().size(), "'" + q + "' -> " + sug.size() + " suggestions");
        return obj("query", q, "suggestions", sug, "nodesVisited", r.nodesVisited(), "durationMs", ms);
    }

    public Map<String, Object> searchIncidents(String pattern, List<String> fields, boolean caseSensitive, int limit) {
        long t0 = System.nanoTime();
        if (pattern == null || pattern.isEmpty() || pattern.isBlank()) {
            throw ApiException.badRequest("Search pattern must not be empty");
        }
        if (limit < 1 || limit > 500) {
            throw ApiException.badRequest("limit must be between 1 and 500");
        }
        boolean wantDesc = fields == null || fields.isEmpty() || fields.contains("DESCRIPTION");
        boolean wantLogs = fields == null || fields.isEmpty() || fields.contains("LOGS");
        boolean ic = !caseSensitive;
        Map<Long, CityZone> zm = network.zoneMap();
        Map<Long, EmergencyIncident> byId = new HashMap<>();
        for (EmergencyIncident i : incidents.findAll()) {
            byId.put(i.incidentId, i);
        }
        long kmpCmp = 0;
        long naiveCmp = 0;
        double kmpMs = 0;
        double naiveMs = 0;
        int scanned = 0;
        int total = 0;
        List<Map<String, Object>> matches = new ArrayList<>();
        List<Object[]> corpus = new ArrayList<>();
        if (wantDesc) {
            for (EmergencyIncident i : byId.values()) {
                corpus.add(new Object[]{i, "DESCRIPTION", i.description});
            }
        }
        if (wantLogs) {
            for (IncidentLog l : logs.findAll()) {
                EmergencyIncident i = byId.get(l.incidentId);
                if (i != null) {
                    corpus.add(new Object[]{i, "LOGS", l.logText});
                }
            }
        }
        corpus.sort((a, b) -> Long.compare(((EmergencyIncident) a[0]).incidentId, ((EmergencyIncident) b[0]).incidentId));
        for (Object[] row : corpus) {
            String text = (String) row[2];
            scanned++;
            long k0 = System.nanoTime();
            MatchResult kr = KmpMatcher.search(text, pattern, ic);
            kmpMs += (System.nanoTime() - k0) / 1e6;
            kmpCmp += kr.comparisons();
            long n0 = System.nanoTime();
            MatchResult nr = NaiveMatcher.search(text, pattern, ic);
            naiveMs += (System.nanoTime() - n0) / 1e6;
            naiveCmp += nr.comparisons();
            if (!kr.positions().isEmpty()) {
                total++;
                if (matches.size() < limit) {
                    EmergencyIncident i = (EmergencyIncident) row[0];
                    matches.add(obj("incidentId", i.incidentId, "incidentCode", i.incidentCode, "zoneName", zm.get(i.zoneId).name,
                            "field", row[1], "text", text, "positions", kr.positions()));
                }
            }
        }
        List<Integer> lps = new ArrayList<>();
        for (int v : KmpMatcher.buildLps(pattern, ic)) {
            lps.add(v);
        }
        Map<String, Object> out = obj("pattern", pattern, "lps", lps, "recordsScanned", scanned, "totalMatches", total, "matches", matches,
                "kmp", obj("comparisons", kmpCmp, "durationMs", round(kmpMs, 3)),
                "naive", obj("comparisons", naiveCmp, "durationMs", round(naiveMs, 3)));
        out.put("execution", exec.record("KMP_SEARCH", t0, scanned, total + " matching records for '" + pattern + "'"));
        return out;
    }
}
