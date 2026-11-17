package com.cityshield.service;

import static com.cityshield.util.Json.obj;

import com.cityshield.algorithm.dsu.Component;
import com.cityshield.algorithm.dsu.ConnectivityAnalyzer;
import com.cityshield.algorithm.dsu.ConnectivityResult;
import com.cityshield.algorithm.dsu.Edge;
import com.cityshield.entity.CityZone;
import com.cityshield.entity.RoadConnection;
import com.cityshield.exception.ApiException;
import com.cityshield.repository.CityZoneRepository;
import com.cityshield.repository.RoadConnectionRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** City road network (Module 6): DSU connectivity analysis, blocked-road simulation and road status changes. */
@Service
public class NetworkService {
    private final CityZoneRepository zones;
    private final RoadConnectionRepository roads;
    private final ExecutionLogService exec;

    public NetworkService(CityZoneRepository zones, RoadConnectionRepository roads, ExecutionLogService exec) {
        this.zones = zones;
        this.roads = roads;
        this.exec = exec;
    }

    /** Zones ordered by zone_index (array index == zone_index for all algorithms). */
    public List<CityZone> zonesOrdered() {
        List<CityZone> list = new ArrayList<>(zones.findAll());
        list.sort((a, b) -> Integer.compare(a.zoneIndex, b.zoneIndex));
        return list;
    }

    public Map<Long, CityZone> zoneMap() {
        Map<Long, CityZone> m = new LinkedHashMap<>();
        for (CityZone z : zonesOrdered()) {
            m.put(z.zoneId, z);
        }
        return m;
    }

    /** Result of a DSU run together with the lookup tables needed to translate it. */
    public record Analysis(ConnectivityResult result, List<CityZone> zones, Map<Long, Integer> indexOfZone, Set<Long> blocked, List<RoadConnection> roads) {
        /** Component id of a zone id. */
        public int componentOf(long zoneId) {
            return result.zoneComponent()[indexOfZone.get(zoneId)];
        }
    }

    /** Runs the DSU analysis with the database-blocked roads plus any extra blocked ids. */
    public Analysis analyze(Set<Long> extraBlocked, boolean includeDatabaseBlocked) {
        List<CityZone> zl = zonesOrdered();
        Map<Long, Integer> idx = new HashMap<>();
        for (int i = 0; i < zl.size(); i++) {
            idx.put(zl.get(i).zoneId, i);
        }
        List<RoadConnection> rl = roads.findAll();
        Set<Long> blocked = new HashSet<>(extraBlocked == null ? Set.of() : extraBlocked);
        if (includeDatabaseBlocked) {
            for (RoadConnection r : rl) {
                if ("BLOCKED".equals(r.status)) {
                    blocked.add(r.roadId);
                }
            }
        }
        List<Edge> edges = new ArrayList<>();
        for (RoadConnection r : rl) {
            edges.add(new Edge(r.roadId, idx.get(r.zoneA), idx.get(r.zoneB)));
        }
        return new Analysis(ConnectivityAnalyzer.analyze(zl.size(), edges, blocked), zl, idx, blocked, rl);
    }

    public Map<String, Object> graph() {
        Map<Long, CityZone> zm = zoneMap();
        return obj("zones", zm.values().stream().map(Dto::zone).collect(Collectors.toList()),
                "roads", roads.findAll().stream().map(r -> Dto.road(r, zm)).collect(Collectors.toList()));
    }

    public Map<String, Object> connectivity(List<Long> blockedRoadIds, boolean includeDatabaseBlocked) {
        long t0 = System.nanoTime();
        Set<Long> extra = new HashSet<>(blockedRoadIds == null ? List.<Long>of() : blockedRoadIds);
        Map<Long, RoadConnection> byId = new HashMap<>();
        List<RoadConnection> all = roads.findAll();
        for (RoadConnection r : all) {
            byId.put(r.roadId, r);
        }
        for (Long id : extra) {
            if (!byId.containsKey(id)) {
                throw ApiException.badRequest("Unknown road id " + id);
            }
        }
        Analysis a = analyze(extra, includeDatabaseBlocked);
        Map<Long, CityZone> zm = zoneMap();
        List<Map<String, Object>> comps = new ArrayList<>();
        for (Component c : a.result().components()) {
            List<Long> zoneIds = new ArrayList<>();
            List<String> names = new ArrayList<>();
            for (int m : c.members()) {
                zoneIds.add(a.zones().get(m).zoneId);
                names.add(a.zones().get(m).name);
            }
            comps.add(obj("id", c.id(), "size", c.size(), "zoneIds", zoneIds, "zoneNames", names, "isLargest", c.isLargest()));
        }
        Map<String, Object> zoneComponent = new LinkedHashMap<>();
        int[] zc = a.result().zoneComponent();
        for (int i = 0; i < a.zones().size(); i++) {
            zoneComponent.put(String.valueOf(a.zones().get(i).zoneId), zc[i]);
        }
        List<Map<String, Object>> isolated = new ArrayList<>();
        for (int i : a.result().isolatedZones()) {
            isolated.add(Dto.zone(a.zones().get(i)));
        }
        List<Map<String, Object>> critical = new ArrayList<>();
        for (long id : a.result().criticalEdgeIds()) {
            critical.add(Dto.road(byId.get(id), zm));
        }
        int blockedCount = a.blocked().size();
        Map<String, Object> out = obj("zoneCount", a.zones().size(), "roadCount", all.size(),
                "openRoadCount", a.result().openEdgeCount(), "blockedRoadIds", new ArrayList<>(a.blocked()),
                "componentCount", a.result().componentCount(), "components", comps, "zoneComponent", zoneComponent,
                "isolatedZones", isolated, "criticalRoads", critical,
                "dsuStats", obj("unions", a.result().unions(), "finds", a.result().finds()));
        out.put("execution", exec.record("DSU_CONNECTIVITY", t0, all.size(),
                a.result().componentCount() + " components, " + blockedCount + " blocked roads"));
        return out;
    }

    @Transactional
    public Map<String, Object> setRoadStatus(long id, String status) {
        if (!"OPEN".equals(status) && !"BLOCKED".equals(status)) {
            throw ApiException.badRequest("status must be OPEN or BLOCKED");
        }
        RoadConnection r = roads.findById(id).orElseThrow(() -> ApiException.notFound("Road " + id + " not found"));
        r.status = status;
        roads.save(r);
        return Dto.road(r, zoneMap());
    }
}
