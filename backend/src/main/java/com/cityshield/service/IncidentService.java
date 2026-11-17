package com.cityshield.service;

import static com.cityshield.util.Json.obj;

import com.cityshield.dto.IncidentRequest;
import com.cityshield.dto.RequirementReq;
import com.cityshield.entity.*;
import com.cityshield.exception.ApiException;
import com.cityshield.repository.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Incident management (Module 2): CRUD, status workflow, history and priority-heap synchronisation. */
@Service
public class IncidentService {
    private final EmergencyIncidentRepository incidents;
    private final IncidentResourceRequirementRepository reqRepo;
    private final IncidentStatusHistoryRepository historyRepo;
    private final IncidentLogRepository logRepo;
    private final IncidentAssignmentRepository assignRepo;
    private final ResponseTeamRepository teamRepo;
    private final EmergencyTypeRepository typeRepo;
    private final ResourceTypeRepository resRepo;
    private final IncidentMapper mapper;
    private final PriorityService priority;
    private final NetworkService network;

    public IncidentService(EmergencyIncidentRepository incidents, IncidentResourceRequirementRepository reqRepo,
                           IncidentStatusHistoryRepository historyRepo, IncidentLogRepository logRepo,
                           IncidentAssignmentRepository assignRepo, ResponseTeamRepository teamRepo,
                           EmergencyTypeRepository typeRepo, ResourceTypeRepository resRepo, IncidentMapper mapper,
                           PriorityService priority, NetworkService network) {
        this.incidents = incidents;
        this.reqRepo = reqRepo;
        this.historyRepo = historyRepo;
        this.logRepo = logRepo;
        this.assignRepo = assignRepo;
        this.teamRepo = teamRepo;
        this.typeRepo = typeRepo;
        this.resRepo = resRepo;
        this.mapper = mapper;
        this.priority = priority;
        this.network = network;
    }

    // ------------------------------------------------------------------ queries
    public Map<String, Object> list(String status, String category, Long zoneId, String q, String sort, String dir, int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw ApiException.badRequest("page must be >= 0 and size between 1 and 200");
        }
        IncidentMapper.Ctx c = mapper.ctx(null);
        String qq = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<EmergencyIncident> filtered = new ArrayList<>();
        for (EmergencyIncident i : incidents.findAll()) {
            if (status != null && !status.isBlank()) {
                boolean ok = "ACTIVE".equalsIgnoreCase(status) ? IncidentMapper.isActive(i.status) : i.status.equalsIgnoreCase(status);
                if (!ok) {
                    continue;
                }
            }
            if (category != null && !category.isBlank() && !category.equalsIgnoreCase(c.typeCode.get(i.typeId))) {
                continue;
            }
            if (zoneId != null && !zoneId.equals(i.zoneId)) {
                continue;
            }
            if (!qq.isEmpty() && !(i.incidentCode.toLowerCase(Locale.ROOT).contains(qq) || i.description.toLowerCase(Locale.ROOT).contains(qq))) {
                continue;
            }
            filtered.add(i);
        }
        Map<Long, Double> score = new HashMap<>();
        for (EmergencyIncident i : filtered) {
            score.put(i.incidentId, mapper.score(i, c).score());
        }
        boolean byTime = "time".equalsIgnoreCase(sort);
        boolean asc = "asc".equalsIgnoreCase(dir);
        filtered.sort((a, b) -> {
            int r = byTime ? a.reportedAt.compareTo(b.reportedAt) : Double.compare(score.get(a.incidentId), score.get(b.incidentId));
            if (r == 0) {
                r = Long.compare(a.incidentId, b.incidentId);
            }
            return asc ? r : -r;
        });
        int total = filtered.size();
        int from = Math.min(total, page * size);
        int to = Math.min(total, from + size);
        List<Map<String, Object>> content = new ArrayList<>();
        for (EmergencyIncident i : filtered.subList(from, to)) {
            content.add(mapper.dto(i, c));
        }
        return obj("content", content, "totalElements", total, "page", page, "size", size, "totalPages", (total + size - 1) / size);
    }

    public Map<String, Object> detail(long id) {
        EmergencyIncident i = find(id);
        IncidentMapper.Ctx c = mapper.ctx(List.of(id));
        Map<String, Object> d = mapper.dto(i, c);
        List<Map<String, Object>> hist = new ArrayList<>();
        for (IncidentStatusHistory h : historyRepo.findByIncidentIdOrderByChangedAtAsc(id)) {
            hist.add(obj("oldStatus", h.oldStatus, "newStatus", h.newStatus, "changedAt", h.changedAt, "note", h.note));
        }
        List<Map<String, Object>> logs = new ArrayList<>();
        for (IncidentLog l : logRepo.findByIncidentIdOrderByLoggedAtAsc(id)) {
            logs.add(obj("loggedAt", l.loggedAt, "text", l.logText));
        }
        Map<Long, ResponseTeam> teams = teamRepo.findAll().stream().collect(Collectors.toMap(t -> t.teamId, t -> t));
        List<Map<String, Object>> asg = new ArrayList<>();
        for (IncidentAssignment a : assignRepo.findByIncidentId(id)) {
            ResponseTeam t = teams.get(a.teamId);
            asg.add(obj("id", a.assignmentId, "teamId", a.teamId, "teamName", t == null ? null : t.name, "status", a.status,
                    "assignedAt", a.assignedAt));
        }
        d.put("history", hist);
        d.put("logs", logs);
        d.put("assignments", asg);
        return d;
    }

    public EmergencyIncident find(long id) {
        return incidents.findById(id).orElseThrow(() -> ApiException.notFound("Incident " + id + " not found"));
    }

    // ------------------------------------------------------------------ commands
    @Transactional
    public Map<String, Object> create(IncidentRequest r) {
        Long typeId = validate(r);
        LocalDateTime now = LocalDateTime.now().withNano(0);
        EmergencyIncident i = new EmergencyIncident();
        i.incidentCode = nextCode();
        i.typeId = typeId;
        apply(i, r);
        i.status = "REPORTED";
        i.reportedAt = now;
        i.createdAt = now;
        i.updatedAt = now;
        i = incidents.save(i);
        saveRequirements(i.incidentId, r.requirements());
        history(i.incidentId, null, "REPORTED", "Registered through CityShield");
        log(i.incidentId, "Incident registered by control-room operator");
        priority.sync(i);
        return detail(i.incidentId);
    }

    @Transactional
    public Map<String, Object> update(long id, IncidentRequest r) {
        EmergencyIncident i = find(id);
        if ("ARCHIVED".equals(i.status)) {
            throw ApiException.conflict("Archived incidents cannot be edited");
        }
        Long typeId = validate(r);
        i.typeId = typeId;
        apply(i, r);
        i.updatedAt = LocalDateTime.now().withNano(0);
        i = incidents.save(i);
        reqRepo.deleteByIncidentId(id);
        reqRepo.flush();
        saveRequirements(id, r.requirements());
        log(id, "Incident details edited");
        priority.sync(i);
        return detail(id);
    }

    @Transactional
    public Map<String, Object> changeStatus(long id, String status, String note) {
        EmergencyIncident i = find(id);
        String target = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!IncidentMapper.allowedNext(i.status).contains(target)) {
            throw ApiException.conflict("Illegal status transition " + i.status + " -> " + target
                    + (IncidentMapper.allowedNext(i.status).isEmpty() ? "" : " (allowed: " + IncidentMapper.allowedNext(i.status) + ")"));
        }
        move(i, target, note);
        return detail(id);
    }

    /** Soft delete: ARCHIVED from any non-archived status. */
    @Transactional
    public Map<String, Object> archive(long id) {
        EmergencyIncident i = find(id);
        if ("ARCHIVED".equals(i.status)) {
            throw ApiException.conflict("Incident is already archived");
        }
        move(i, "ARCHIVED", "Archived by operator");
        return detail(id);
    }

    /** Applies a status change, writes history, releases teams when the incident ends and updates the heap. */
    void move(EmergencyIncident i, String target, String note) {
        String old = i.status;
        i.status = target;
        i.updatedAt = LocalDateTime.now().withNano(0);
        incidents.save(i);
        history(i.incidentId, old, target, note == null || note.isBlank() ? "Status updated" : note);
        if (!IncidentMapper.isActive(target)) {
            for (IncidentAssignment a : assignRepo.findByIncidentIdAndStatus(i.incidentId, "ACTIVE")) {
                a.status = "RESOLVED".equals(target) ? "COMPLETED" : "RELEASED";
                assignRepo.save(a);
                teamRepo.findById(a.teamId).ifPresent(t -> {
                    if ("BUSY".equals(t.availability)) {
                        t.availability = "AVAILABLE";
                        t.currentIncidentId = null;
                        teamRepo.save(t);
                    }
                });
            }
        }
        priority.sync(i);
    }

    /** Used by allocation commit: REPORTED -> ASSIGNED without the operator-facing transition check. */
    @Transactional
    public void markAssigned(EmergencyIncident i) {
        if ("REPORTED".equals(i.status)) {
            move(i, "ASSIGNED", "Team assigned");
        }
    }

    // ------------------------------------------------------------------ helpers
    private Long validate(IncidentRequest r) {
        List<String> errors = new ArrayList<>();
        Long typeId = null;
        for (EmergencyType t : typeRepo.findAll()) {
            if (t.code.equalsIgnoreCase(r.category().trim())) {
                typeId = t.typeId;
            }
        }
        if (typeId == null) {
            errors.add("category: unknown emergency category '" + r.category() + "'");
        }
        if (!network.zoneMap().containsKey(r.zoneId())) {
            errors.add("zoneId: zone " + r.zoneId() + " does not exist");
        }
        Set<Long> seen = new HashSet<>();
        Set<Long> known = new HashSet<>();
        for (ResourceType t : resRepo.findAll()) {
            known.add(t.resourceTypeId);
        }
        if (r.requirements() != null) {
            for (RequirementReq q : r.requirements()) {
                if (!known.contains(q.resourceTypeId())) {
                    errors.add("requirements: unknown resource type " + q.resourceTypeId());
                }
                if (!seen.add(q.resourceTypeId())) {
                    errors.add("requirements: resource type " + q.resourceTypeId() + " appears more than once");
                }
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        return typeId;
    }

    private void apply(EmergencyIncident i, IncidentRequest r) {
        i.zoneId = r.zoneId();
        i.description = r.description().trim();
        i.severity = r.severity();
        i.urgency = r.urgency();
        i.affectedPeople = r.affectedPeople();
    }

    private void saveRequirements(long incidentId, List<RequirementReq> reqs) {
        if (reqs == null) {
            return;
        }
        for (RequirementReq q : reqs) {
            IncidentResourceRequirement row = new IncidentResourceRequirement();
            row.incidentId = incidentId;
            row.resourceTypeId = q.resourceTypeId();
            row.quantityRequired = q.quantity();
            reqRepo.save(row);
        }
    }

    private void history(long id, String old, String now, String note) {
        IncidentStatusHistory h = new IncidentStatusHistory();
        h.incidentId = id;
        h.oldStatus = old;
        h.newStatus = now;
        h.changedAt = LocalDateTime.now().withNano(0);
        h.note = note;
        historyRepo.save(h);
    }

    private void log(long id, String text) {
        IncidentLog l = new IncidentLog();
        l.incidentId = id;
        l.logText = text;
        l.loggedAt = LocalDateTime.now().withNano(0);
        logRepo.save(l);
    }

    private String nextCode() {
        int max = 0;
        for (EmergencyIncident i : incidents.findAll()) {
            try {
                max = Math.max(max, Integer.parseInt(i.incidentCode.substring(4)));
            } catch (RuntimeException ignored) {
                // non-numeric codes are ignored
            }
        }
        String code = String.format("INC-%04d", max + 1);
        while (incidents.existsByIncidentCode(code)) {
            max++;
            code = String.format("INC-%04d", max + 1);
        }
        return code;
    }
}
