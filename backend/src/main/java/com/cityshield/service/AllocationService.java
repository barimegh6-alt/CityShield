package com.cityshield.service;

import static com.cityshield.util.Json.obj;
import static com.cityshield.util.Json.round;

import com.cityshield.algorithm.matching.KuhnMatcher;
import com.cityshield.algorithm.matching.MatchingResult;
import com.cityshield.algorithm.tsp.GeoDistance;
import com.cityshield.entity.*;
import com.cityshield.exception.ApiException;
import com.cityshield.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Response team allocation (Module 4) using Kuhn's augmenting-path bipartite matching. */
@Service
public class AllocationService {
    private final ResponseTeamRepository teams;
    private final EmergencyCentreRepository centres;
    private final EmergencyIncidentRepository incidents;
    private final EmergencyTypeRepository typeRepo;
    private final EmergencyTypeTeamCompatRepository compatRepo;
    private final IncidentAssignmentRepository assignments;
    private final IncidentMapper mapper;
    private final NetworkService network;
    private final IncidentService incidentService;
    private final ExecutionLogService exec;

    public AllocationService(ResponseTeamRepository teams, EmergencyCentreRepository centres, EmergencyIncidentRepository incidents,
                             EmergencyTypeRepository typeRepo, EmergencyTypeTeamCompatRepository compatRepo,
                             IncidentAssignmentRepository assignments, IncidentMapper mapper, NetworkService network,
                             IncidentService incidentService, ExecutionLogService exec) {
        this.teams = teams;
        this.centres = centres;
        this.incidents = incidents;
        this.typeRepo = typeRepo;
        this.compatRepo = compatRepo;
        this.assignments = assignments;
        this.mapper = mapper;
        this.network = network;
        this.incidentService = incidentService;
        this.exec = exec;
    }

    // ------------------------------------------------------------------ teams
    private Map<Long, EmergencyCentre> centreMap() {
        Map<Long, EmergencyCentre> m = new HashMap<>();
        for (EmergencyCentre c : centres.findAll()) {
            m.put(c.centreId, c);
        }
        return m;
    }

    public List<Map<String, Object>> listTeams() {
        Map<Long, CityZone> zm = network.zoneMap();
        Map<Long, EmergencyCentre> cm = centreMap();
        List<ResponseTeam> list = new ArrayList<>(teams.findAll());
        list.sort(Comparator.comparingLong(t -> t.teamId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ResponseTeam t : list) {
            out.add(Dto.team(t, zm, cm));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> saveTeam(Long id, String name, String teamType, Long baseZoneId, Long centreId, String availability) {
        List<String> errors = new ArrayList<>();
        if (name == null || name.isBlank() || name.length() > 80) {
            errors.add("name: required, at most 80 characters");
        }
        if (teamType == null || !List.of("FIRE", "MEDICAL", "RESCUE", "POLICE", "HAZMAT", "ENGINEERING").contains(teamType)) {
            errors.add("teamType: must be FIRE, MEDICAL, RESCUE, POLICE, HAZMAT or ENGINEERING");
        }
        if (baseZoneId == null || !network.zoneMap().containsKey(baseZoneId)) {
            errors.add("baseZoneId: zone does not exist");
        }
        if (centreId != null && !centres.existsById(centreId)) {
            errors.add("centreId: centre does not exist");
        }
        String av = availability == null ? "AVAILABLE" : availability;
        if (!List.of("AVAILABLE", "BUSY", "OFF_DUTY").contains(av)) {
            errors.add("availability: must be AVAILABLE, BUSY or OFF_DUTY");
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        ResponseTeam t = id == null ? new ResponseTeam() : teams.findById(id).orElseThrow(() -> ApiException.notFound("Team " + id + " not found"));
        if (id != null && "BUSY".equals(t.availability) && !"BUSY".equals(av)) {
            throw ApiException.conflict("A BUSY team must be released from its assignment first");
        }
        if (id == null && "BUSY".equals(av)) {
            throw ApiException.conflict("A new team cannot start BUSY; BUSY is set by assigning it to an incident");
        }
        t.name = name.trim();
        t.teamType = teamType;
        t.baseZoneId = baseZoneId;
        t.centreId = centreId;
        if (!"BUSY".equals(t.availability)) {
            t.availability = av;
        }
        teams.save(t);
        return Dto.team(t, network.zoneMap(), centreMap());
    }

    @Transactional
    public Map<String, Object> setAvailability(long id, String availability) {
        ResponseTeam t = teams.findById(id).orElseThrow(() -> ApiException.notFound("Team " + id + " not found"));
        if (!List.of("AVAILABLE", "OFF_DUTY").contains(availability)) {
            throw ApiException.badRequest("availability must be AVAILABLE or OFF_DUTY (BUSY is set by assigning)");
        }
        if ("BUSY".equals(t.availability)) {
            throw ApiException.conflict("Team is BUSY with incident " + t.currentIncidentId + "; release the assignment first");
        }
        t.availability = availability;
        teams.save(t);
        return Dto.team(t, network.zoneMap(), centreMap());
    }

    // ------------------------------------------------------------------ eligibility
    private Map<Long, Set<String>> compatByType() {
        Map<Long, Set<String>> m = new HashMap<>();
        for (EmergencyTypeTeamCompat c : compatRepo.findAll()) {
            m.computeIfAbsent(c.typeId, k -> new HashSet<>()).add(c.teamType);
        }
        return m;
    }

    public List<Map<String, Object>> eligibility(long incidentId) {
        EmergencyIncident inc = incidents.findById(incidentId).orElseThrow(() -> ApiException.notFound("Incident " + incidentId + " not found"));
        Set<String> okTypes = compatByType().getOrDefault(inc.typeId, Set.of());
        Map<Long, CityZone> zm = network.zoneMap();
        Map<Long, EmergencyCentre> cm = centreMap();
        NetworkService.Analysis an = network.analyze(Set.of(), true);
        List<Map<String, Object>> out = new ArrayList<>();
        List<ResponseTeam> all = new ArrayList<>(teams.findAll());
        all.sort(Comparator.comparingLong(t -> t.teamId));
        for (ResponseTeam t : all) {
            List<String> reasons = new ArrayList<>();
            if (!"AVAILABLE".equals(t.availability)) {
                reasons.add("Team is " + t.availability);
            }
            if (!okTypes.contains(t.teamType)) {
                reasons.add("Team type " + t.teamType + " is not compatible with this incident (needs " + okTypes + ")");
            }
            if (an.componentOf(t.baseZoneId) != an.componentOf(inc.zoneId)) {
                reasons.add("Team base zone is disconnected from the incident zone by blocked roads");
            }
            out.add(obj("team", Dto.team(t, zm, cm), "eligible", reasons.isEmpty(), "reasons", reasons));
        }
        return out;
    }

    // ------------------------------------------------------------------ preview (no data is modified)
    public Map<String, Object> preview(List<Long> incidentIds, boolean requireReachability, List<Long> blockedRoadIds) {
        long t0 = System.nanoTime();
        IncidentMapper.Ctx c = mapper.ctx(null);
        List<EmergencyIncident> list = new ArrayList<>();
        if (incidentIds == null || incidentIds.isEmpty()) {
            list.addAll(incidents.findByStatusIn(List.of("REPORTED")));
        } else {
            for (Long id : incidentIds) {
                EmergencyIncident i = incidents.findById(id).orElseThrow(() -> ApiException.notFound("Incident " + id + " not found"));
                if (!IncidentMapper.isActive(i.status)) {
                    throw ApiException.badRequest("Incident " + i.incidentCode + " is " + i.status + " and cannot be staffed");
                }
                list.add(i);
            }
        }
        Map<Long, Double> score = new HashMap<>();
        for (EmergencyIncident i : list) {
            score.put(i.incidentId, mapper.score(i, c).score());
        }
        list.sort((a, b) -> {
            int r = Double.compare(score.get(b.incidentId), score.get(a.incidentId));
            if (r == 0) {
                r = a.reportedAt.compareTo(b.reportedAt);
            }
            return r != 0 ? r : Long.compare(a.incidentId, b.incidentId);
        });
        Set<Long> blocked = new HashSet<>(blockedRoadIds == null ? List.<Long>of() : blockedRoadIds);
        NetworkService.Analysis an = network.analyze(blocked, true);
        Map<Long, CityZone> zm = network.zoneMap();
        Map<Long, Set<String>> compat = compatByType();

        List<ResponseTeam> avail = new ArrayList<>();
        int teamTotal = 0;
        for (ResponseTeam t : teams.findAll()) {
            teamTotal++;
            if ("AVAILABLE".equals(t.availability)) {
                avail.add(t);
            }
        }
        avail.sort(Comparator.comparingLong(t -> t.teamId));

        List<EmergencyIncident> slotIncident = new ArrayList<>();
        List<Integer> slotNo = new ArrayList<>();
        List<List<Integer>> adjacency = new ArrayList<>();
        Map<Long, String> reason = new HashMap<>();
        for (EmergencyIncident inc : list) {
            int need = inc.severity >= 4 ? 2 : 1;
            Set<String> okTypes = compat.getOrDefault(inc.typeId, Set.of());
            CityZone iz = zm.get(inc.zoneId);
            List<Integer> cand = new ArrayList<>();
            boolean anyType = false;
            for (int k = 0; k < avail.size(); k++) {
                ResponseTeam t = avail.get(k);
                if (!okTypes.contains(t.teamType)) {
                    continue;
                }
                anyType = true;
                if (requireReachability && an.componentOf(t.baseZoneId) != an.componentOf(inc.zoneId)) {
                    continue;
                }
                cand.add(k);
            }
            final CityZone izf = iz;
            cand.sort((a, b) -> {
                CityZone za = zm.get(avail.get(a).baseZoneId);
                CityZone zb = zm.get(avail.get(b).baseZoneId);
                int r = Double.compare(GeoDistance.haversine(za.latitude, za.longitude, izf.latitude, izf.longitude),
                        GeoDistance.haversine(zb.latitude, zb.longitude, izf.latitude, izf.longitude));
                return r != 0 ? r : Long.compare(avail.get(a).teamId, avail.get(b).teamId);
            });
            if (!anyType) {
                reason.put(inc.incidentId, "No available team of a compatible type (needs one of " + new TreeSet<>(okTypes) + ")");
            } else if (cand.isEmpty()) {
                reason.put(inc.incidentId, "Compatible teams exist but all are cut off from " + iz.name + " by blocked roads");
            } else {
                reason.put(inc.incidentId, "All compatible teams are already assigned to higher-priority incidents");
            }
            for (int s = 0; s < need; s++) {
                slotIncident.add(inc);
                slotNo.add(s + 1);
                adjacency.add(cand);
            }
        }
        MatchingResult mr = adjacency.isEmpty() ? null : KuhnMatcher.match(adjacency, avail.size());
        List<Map<String, Object>> asg = new ArrayList<>();
        Map<Long, Integer> filled = new HashMap<>();
        Map<Long, Integer> needed = new HashMap<>();
        Set<Integer> usedTeams = new HashSet<>();
        for (int s = 0; s < slotIncident.size(); s++) {
            EmergencyIncident inc = slotIncident.get(s);
            needed.merge(inc.incidentId, 1, Integer::sum);
            int team = mr == null ? -1 : mr.matchOfSlot()[s];
            if (team >= 0) {
                ResponseTeam t = avail.get(team);
                CityZone tz = zm.get(t.baseZoneId);
                CityZone iz = zm.get(inc.zoneId);
                filled.merge(inc.incidentId, 1, Integer::sum);
                usedTeams.add(team);
                asg.add(obj("incidentId", inc.incidentId, "incidentCode", inc.incidentCode, "category", c.typeCode.get(inc.typeId),
                        "priorityScore", score.get(inc.incidentId), "slot", slotNo.get(s), "teamId", t.teamId, "teamName", t.name,
                        "teamType", t.teamType,
                        "distanceKm", round(GeoDistance.roadKm(tz.latitude, tz.longitude, iz.latitude, iz.longitude), 2)));
            }
        }
        List<Map<String, Object>> unassigned = new ArrayList<>();
        int full = 0;
        int partial = 0;
        int none = 0;
        for (EmergencyIncident inc : list) {
            int f = filled.getOrDefault(inc.incidentId, 0);
            int n = needed.getOrDefault(inc.incidentId, 1);
            if (f == n) {
                full++;
                continue;
            }
            if (f == 0) {
                none++;
            } else {
                partial++;
            }
            unassigned.add(obj("incidentId", inc.incidentId, "incidentCode", inc.incidentCode, "category", c.typeCode.get(inc.typeId),
                    "priorityScore", score.get(inc.incidentId), "slotsNeeded", n, "slotsFilled", f, "reason", reason.get(inc.incidentId)));
        }
        int slots = slotIncident.size();
        int matched = mr == null ? 0 : mr.size();
        Map<String, Object> metrics = obj("incidentsConsidered", list.size(), "slotsTotal", slots, "slotsMatched", matched,
                "slotsUnmatched", slots - matched, "incidentsFullyStaffed", full, "incidentsPartial", partial,
                "incidentsUnstaffed", none, "teamsAvailable", avail.size(), "teamsUsed", usedTeams.size(), "matchingSize", matched,
                "efficiencyPct", slots == 0 ? 0.0 : round(100.0 * matched / slots, 1));
        Map<String, Object> out = obj("assignments", asg, "unassigned", unassigned, "metrics", metrics,
                "componentCount", an.result().componentCount());
        out.put("execution", exec.record("KUHN_MATCHING", t0, slots + avail.size(), matched + " of " + slots + " slots matched"));
        return out;
    }

    // ------------------------------------------------------------------ commit (re-validates in one transaction)
    @Transactional
    public Map<String, Object> commit(List<long[]> pairs) {
        if (pairs == null || pairs.isEmpty()) {
            throw ApiException.badRequest("assignments must not be empty");
        }
        Set<Long> seenTeams = new HashSet<>();
        Map<Long, Set<String>> compat = compatByType();
        List<Map<String, Object>> done = new ArrayList<>();
        for (long[] p : pairs) {
            long incidentId = p[0];
            long teamId = p[1];
            if (!seenTeams.add(teamId)) {
                throw ApiException.conflict("Team " + teamId + " appears more than once in the request");
            }
            EmergencyIncident inc = incidents.findById(incidentId).orElseThrow(() -> ApiException.notFound("Incident " + incidentId + " not found"));
            ResponseTeam t = teams.findById(teamId).orElseThrow(() -> ApiException.notFound("Team " + teamId + " not found"));
            if (!IncidentMapper.isActive(inc.status)) {
                throw ApiException.conflict("Incident " + inc.incidentCode + " is no longer active (" + inc.status + ") - preview is stale");
            }
            if (!"AVAILABLE".equals(t.availability)) {
                throw ApiException.conflict("Team " + t.name + " is " + t.availability + " now - preview is stale");
            }
            if (!compat.getOrDefault(inc.typeId, Set.of()).contains(t.teamType)) {
                throw ApiException.conflict("Team " + t.name + " is not eligible for incident " + inc.incidentCode);
            }
            IncidentAssignment a = new IncidentAssignment();
            a.incidentId = incidentId;
            a.teamId = teamId;
            a.status = "ACTIVE";
            a.assignedAt = LocalDateTime.now().withNano(0);
            a = assignments.save(a);
            t.availability = "BUSY";
            t.currentIncidentId = incidentId;
            teams.save(t);
            incidentService.markAssigned(inc);
            done.add(obj("id", a.assignmentId, "incidentId", incidentId, "incidentCode", inc.incidentCode, "teamId", teamId, "teamName", t.name));
        }
        return obj("committed", done.size(), "assignments", done);
    }

    public List<Map<String, Object>> listAssignments(boolean activeOnly) {
        Map<Long, ResponseTeam> tm = new HashMap<>();
        for (ResponseTeam t : teams.findAll()) {
            tm.put(t.teamId, t);
        }
        Map<Long, EmergencyIncident> im = new HashMap<>();
        for (EmergencyIncident i : incidents.findAll()) {
            im.put(i.incidentId, i);
        }
        List<IncidentAssignment> rows = activeOnly ? assignments.findByStatus("ACTIVE") : assignments.findAll();
        rows = new ArrayList<>(rows);
        rows.sort((a, b) -> b.assignedAt.compareTo(a.assignedAt));
        List<Map<String, Object>> out = new ArrayList<>();
        for (IncidentAssignment a : rows) {
            ResponseTeam t = tm.get(a.teamId);
            EmergencyIncident i = im.get(a.incidentId);
            out.add(obj("id", a.assignmentId, "incidentId", a.incidentId, "incidentCode", i == null ? null : i.incidentCode,
                    "teamId", a.teamId, "teamName", t == null ? null : t.name, "teamType", t == null ? null : t.teamType,
                    "status", a.status, "assignedAt", a.assignedAt));
        }
        return out;
    }

    @Transactional
    public Map<String, Object> release(long assignmentId) {
        IncidentAssignment a = assignments.findById(assignmentId).orElseThrow(() -> ApiException.notFound("Assignment " + assignmentId + " not found"));
        if (!"ACTIVE".equals(a.status)) {
            throw ApiException.conflict("Assignment is already " + a.status);
        }
        a.status = "RELEASED";
        assignments.save(a);
        ResponseTeam t = teams.findById(a.teamId).orElse(null);
        if (t != null && "BUSY".equals(t.availability)) {
            t.availability = "AVAILABLE";
            t.currentIncidentId = null;
            teams.save(t);
        }
        return obj("id", a.assignmentId, "status", a.status, "teamId", a.teamId);
    }
}
