package com.cityshield.service;

import static com.cityshield.util.Json.obj;
import static com.cityshield.util.Json.round;

import com.cityshield.algorithm.maxflow.AugmentingStep;
import com.cityshield.algorithm.maxflow.EdmondsKarp;
import com.cityshield.algorithm.maxflow.FlowNetwork;
import com.cityshield.algorithm.maxflow.MaxFlowResult;
import com.cityshield.algorithm.segmenttree.LinearScan;
import com.cityshield.algorithm.segmenttree.SegmentTree;
import com.cityshield.entity.*;
import com.cityshield.exception.ApiException;
import com.cityshield.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resource distribution (Module 5): inventory with one Segment Tree per resource type, and
 * Edmonds-Karp max-flow distribution with a preview / commit workflow.
 *
 * <p>Flow network: SOURCE -> WAREHOUSE zones (capacity = stock) -> directed distribution edges ->
 * zones with a shortage -> SINK (capacity = shortage). Shortage of a zone = max(0, units required by its
 * active incidents - local stock), limited by free storage capacity. Preview never touches the database.</p>
 */
@Service
public class ResourceService {
    private final ResourceInventoryRepository invRepo;
    private final ResourceTypeRepository typeRepo;
    private final DistributionEdgeRepository edgeRepo;
    private final ResourceAllocationRepository allocRepo;
    private final EmergencyIncidentRepository incidents;
    private final IncidentResourceRequirementRepository reqRepo;
    private final NetworkService network;
    private final ExecutionLogService exec;

    private final Map<Long, SegmentTree> trees = new HashMap<>();
    private boolean treesLoaded = false;

    public ResourceService(ResourceInventoryRepository invRepo, ResourceTypeRepository typeRepo, DistributionEdgeRepository edgeRepo,
                           ResourceAllocationRepository allocRepo, EmergencyIncidentRepository incidents,
                           IncidentResourceRequirementRepository reqRepo, NetworkService network, ExecutionLogService exec) {
        this.invRepo = invRepo;
        this.typeRepo = typeRepo;
        this.edgeRepo = edgeRepo;
        this.allocRepo = allocRepo;
        this.incidents = incidents;
        this.reqRepo = reqRepo;
        this.network = network;
        this.exec = exec;
    }

    // ------------------------------------------------------------------ segment trees
    private synchronized Map<Long, SegmentTree> trees() {
        if (!treesLoaded) {
            rebuildTrees();
        }
        return trees;
    }

    public synchronized void rebuildTrees() {
        List<CityZone> zones = network.zonesOrdered();
        Map<Long, Integer> idx = new HashMap<>();
        for (int i = 0; i < zones.size(); i++) {
            idx.put(zones.get(i).zoneId, i);
        }
        Map<Long, long[]> arrays = new HashMap<>();
        for (ResourceType t : typeRepo.findAll()) {
            arrays.put(t.resourceTypeId, new long[zones.size()]);
        }
        for (ResourceInventory r : invRepo.findAll()) {
            long[] a = arrays.get(r.resourceTypeId);
            Integer i = idx.get(r.zoneId);
            if (a != null && i != null) {
                a[i] = r.quantityAvailable;
            }
        }
        trees.clear();
        for (Map.Entry<Long, long[]> e : arrays.entrySet()) {
            trees.put(e.getKey(), new SegmentTree(e.getValue()));
        }
        treesLoaded = true;
    }

    private ResourceType type(long id) {
        return typeRepo.findById(id).orElseThrow(() -> ApiException.notFound("Resource type " + id + " not found"));
    }

    // ------------------------------------------------------------------ inventory
    public List<Map<String, Object>> inventory(long resourceTypeId) {
        type(resourceTypeId);
        Map<Long, CityZone> zm = network.zoneMap();
        List<Map<String, Object>> out = new ArrayList<>();
        for (ResourceInventory r : invRepo.findByResourceTypeId(resourceTypeId)) {
            CityZone z = zm.get(r.zoneId);
            out.add(obj("zoneId", r.zoneId, "zoneName", z.name, "zoneIndex", z.zoneIndex, "zoneRole", z.zoneRole,
                    "quantity", r.quantityAvailable, "capacity", r.quantityCapacity));
        }
        out.sort(Comparator.comparingInt(m -> (Integer) m.get("zoneIndex")));
        return out;
    }

    /** Per-type totals; "available" comes from the segment tree root. utilizationPct = share of capacity already consumed. */
    public List<Map<String, Object>> summary() {
        Map<Long, Long> cap = new HashMap<>();
        for (ResourceInventory r : invRepo.findAll()) {
            cap.merge(r.resourceTypeId, (long) r.quantityCapacity, Long::sum);
        }
        List<ResourceType> types = new ArrayList<>(typeRepo.findAll());
        types.sort(Comparator.comparingLong(t -> t.resourceTypeId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (ResourceType t : types) {
            long avail = trees().get(t.resourceTypeId).total();
            long c = cap.getOrDefault(t.resourceTypeId, 0L);
            out.add(obj("resourceTypeId", t.resourceTypeId, "name", t.name, "unit", t.unit, "available", avail, "capacity", c,
                    "utilizationPct", c == 0 ? 0.0 : round(100.0 * (c - avail) / c, 1)));
        }
        return out;
    }

    public Map<String, Object> rangeQuery(long resourceTypeId, int from, int to, boolean compareLinear) {
        long t0 = System.nanoTime();
        type(resourceTypeId);
        List<CityZone> zones = network.zonesOrdered();
        if (from < 0 || to < 0 || from >= zones.size() || to >= zones.size()) {
            throw ApiException.badRequest("fromIndex and toIndex must be between 0 and " + (zones.size() - 1));
        }
        if (from > to) {
            throw ApiException.badRequest("fromIndex must not be greater than toIndex");
        }
        SegmentTree tree = trees().get(resourceTypeId);
        long s0 = System.nanoTime();
        long sum = tree.sum(from, to);
        double stMs = (System.nanoTime() - s0) / 1e6;
        int visited = tree.getLastNodesVisited();
        List<String> names = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            names.add(zones.get(i).name);
        }
        Map<String, Object> out = obj("resourceTypeId", resourceTypeId, "fromIndex", from, "toIndex", to, "zones", names, "sum", sum,
                "segmentTree", obj("nodesVisited", visited, "durationMs", round(stMs, 4)));
        if (compareLinear) {
            long[] values = new long[zones.size()];
            for (int i = 0; i < values.length; i++) {
                values[i] = tree.get(i);
            }
            long l0 = System.nanoTime();
            LinearScan.Result lr = LinearScan.sum(values, from, to);
            double lMs = (System.nanoTime() - l0) / 1e6;
            out.put("linearScan", obj("sum", lr.sum(), "elementsScanned", lr.elementsScanned(), "durationMs", round(lMs, 4)));
        }
        out.put("execution", exec.record("SEGMENT_TREE_RANGE", t0, zones.size(), "sum[" + from + ".." + to + "]=" + sum));
        return out;
    }

    @Transactional
    public synchronized Map<String, Object> adjust(long zoneId, long resourceTypeId, int delta, String reason) {
        long t0 = System.nanoTime();
        type(resourceTypeId);
        CityZone z = network.zoneMap().get(zoneId);
        if (z == null) {
            throw ApiException.notFound("Zone " + zoneId + " not found");
        }
        ResourceInventory inv = invRepo.findByZoneIdAndResourceTypeId(zoneId, resourceTypeId);
        if (inv == null) {
            throw ApiException.notFound("No inventory row for zone " + zoneId + " and resource type " + resourceTypeId);
        }
        long after = (long) inv.quantityAvailable + delta;
        if (after < 0) {
            throw ApiException.infeasible("Insufficient stock: " + inv.quantityAvailable + " available, cannot remove " + (-delta), null);
        }
        if (after > inv.quantityCapacity) {
            throw ApiException.infeasible("Capacity exceeded: capacity is " + inv.quantityCapacity + ", result would be " + after, null);
        }
        int before = inv.quantityAvailable;
        inv.quantityAvailable = (int) after;
        invRepo.save(inv);
        trees().get(resourceTypeId).set(z.zoneIndex, after);
        Map<String, Object> out = obj("zoneId", zoneId, "resourceTypeId", resourceTypeId, "before", before, "after", (int) after,
                "capacity", inv.quantityCapacity, "reason", reason);
        out.put("execution", exec.record("SEGMENT_TREE_UPDATE", t0, network.zonesOrdered().size(), "stock " + before + " -> " + after));
        return out;
    }

    // ------------------------------------------------------------------ distribution network
    private record Demand(Map<Long, Long> stock, Map<Long, Long> capacity, Map<Long, Long> demand) {
    }

    private Demand demand(long typeId) {
        Map<Long, Long> stock = new HashMap<>();
        Map<Long, Long> cap = new HashMap<>();
        for (ResourceInventory r : invRepo.findByResourceTypeId(typeId)) {
            stock.put(r.zoneId, (long) r.quantityAvailable);
            cap.put(r.zoneId, (long) r.quantityCapacity);
        }
        Map<Long, Long> zoneOfIncident = new HashMap<>();
        for (EmergencyIncident i : incidents.findByStatusIn(IncidentMapper.ACTIVE)) {
            zoneOfIncident.put(i.incidentId, i.zoneId);
        }
        Map<Long, Long> required = new HashMap<>();
        for (IncidentResourceRequirement q : reqRepo.findAll()) {
            Long zone = zoneOfIncident.get(q.incidentId);
            if (zone != null && q.resourceTypeId == typeId) {
                required.merge(zone, (long) q.quantityRequired, Long::sum);
            }
        }
        Map<Long, Long> demand = new HashMap<>();
        for (CityZone z : network.zonesOrdered()) {
            if ("WAREHOUSE".equals(z.zoneRole)) {
                continue;
            }
            long need = Math.max(0, required.getOrDefault(z.zoneId, 0L) - stock.getOrDefault(z.zoneId, 0L));
            long free = Math.max(0, cap.getOrDefault(z.zoneId, 0L) - stock.getOrDefault(z.zoneId, 0L));
            long d = Math.min(need, free);
            if (d > 0) {
                demand.put(z.zoneId, d);
            }
        }
        return new Demand(stock, cap, demand);
    }

    public Map<String, Object> networkView(long typeId) {
        type(typeId);
        Demand d = demand(typeId);
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (CityZone z : network.zonesOrdered()) {
            nodes.add(obj("zoneId", z.zoneId, "name", z.name, "role", z.zoneRole, "latitude", z.latitude, "longitude", z.longitude,
                    "supply", "WAREHOUSE".equals(z.zoneRole) ? d.stock().getOrDefault(z.zoneId, 0L) : 0L,
                    "demand", d.demand().getOrDefault(z.zoneId, 0L)));
        }
        List<Map<String, Object>> edges = new ArrayList<>();
        List<DistributionEdge> el = new ArrayList<>(edgeRepo.findAll());
        el.sort(Comparator.comparingLong(e -> e.edgeId));
        for (DistributionEdge e : el) {
            edges.add(obj("id", e.edgeId, "fromZoneId", e.fromZone, "toZoneId", e.toZone, "capacity", e.capacityUnits));
        }
        return obj("nodes", nodes, "edges", edges);
    }

    @Transactional
    public Map<String, Object> setEdgeCapacity(long id, int capacity) {
        if (capacity < 0) {
            throw ApiException.badRequest("capacity must be >= 0");
        }
        DistributionEdge e = edgeRepo.findById(id).orElseThrow(() -> ApiException.notFound("Edge " + id + " not found"));
        e.capacityUnits = capacity;
        edgeRepo.save(e);
        return obj("id", e.edgeId, "fromZoneId", e.fromZone, "toZoneId", e.toZone, "capacity", e.capacityUnits);
    }

    /** Internal result of a max-flow computation. */
    public record Plan(Map<String, Object> dto, long maxFlow, List<long[]> deliveries /* {fromZone,toZone,qty} */, int inputSize) {
    }

    private enum Kind { SUPPLY, EDGE, DEMAND }

    private record EdgeMeta(Kind kind, long fromZone, long toZone, long capacity) {
    }

    Plan computePlan(long typeId, List<Long> blockedRoadIds) {
        ResourceType rt = type(typeId);
        Demand d = demand(typeId);
        NetworkService.Analysis an = network.analyze(new HashSet<>(blockedRoadIds == null ? List.<Long>of() : blockedRoadIds), true);
        Map<Long, CityZone> zm = network.zoneMap();
        FlowNetwork net = new FlowNetwork();
        int source = net.addNode("SUPPLY");
        int sink = net.addNode("DEMAND");
        Map<Long, Integer> node = new HashMap<>();
        for (CityZone z : network.zonesOrdered()) {
            node.put(z.zoneId, net.addNode(z.name));
        }
        List<EdgeMeta> meta = new ArrayList<>();
        long totalSupply = 0;
        for (CityZone z : network.zonesOrdered()) {
            if ("WAREHOUSE".equals(z.zoneRole)) {
                long s = d.stock().getOrDefault(z.zoneId, 0L);
                totalSupply += s;
                if (s > 0) {
                    net.addEdge(source, node.get(z.zoneId), s);
                    meta.add(new EdgeMeta(Kind.SUPPLY, -1, z.zoneId, s));
                }
            }
        }
        List<Map<String, Object>> zeroEdges = new ArrayList<>();
        List<Map<String, Object>> unusable = new ArrayList<>();
        List<DistributionEdge> dl = new ArrayList<>(edgeRepo.findAll());
        dl.sort(Comparator.comparingLong(e -> e.edgeId));
        for (DistributionEdge e : dl) {
            Map<String, Object> label = obj("fromName", zm.get(e.fromZone).name, "toName", zm.get(e.toZone).name, "capacity", e.capacityUnits);
            if (an.componentOf(e.fromZone) != an.componentOf(e.toZone)) {
                unusable.add(label);
                continue;
            }
            if (e.capacityUnits == 0) {
                zeroEdges.add(label);
            }
            net.addEdge(node.get(e.fromZone), node.get(e.toZone), e.capacityUnits);
            meta.add(new EdgeMeta(Kind.EDGE, e.fromZone, e.toZone, e.capacityUnits));
        }
        long totalDemand = 0;
        for (CityZone z : network.zonesOrdered()) {
            long dm = d.demand().getOrDefault(z.zoneId, 0L);
            if (dm > 0) {
                totalDemand += dm;
                net.addEdge(node.get(z.zoneId), sink, dm);
                meta.add(new EdgeMeta(Kind.DEMAND, z.zoneId, -1, dm));
            }
        }
        MaxFlowResult r = EdmondsKarp.solve(net, source, sink);

        // per-edge flows (distribution edges only) and received amounts
        List<Map<String, Object>> edgeFlows = new ArrayList<>();
        Map<Long, Long> received = new HashMap<>();
        long[] rem = new long[meta.size()];
        for (int i = 0; i < meta.size(); i++) {
            EdgeMeta m = meta.get(i);
            long f = r.flowOn(i);
            rem[i] = f;
            if (m.kind() == Kind.EDGE) {
                edgeFlows.add(obj("fromZoneId", m.fromZone(), "toZoneId", m.toZone(), "fromName", zm.get(m.fromZone()).name,
                        "toName", zm.get(m.toZone()).name, "capacity", m.capacity(), "flow", f, "saturated", m.capacity() > 0 && f == m.capacity()));
            } else if (m.kind() == Kind.DEMAND) {
                received.put(m.fromZone(), f);
            }
        }
        List<Map<String, Object>> demandZones = new ArrayList<>();
        long unmet = 0;
        for (CityZone z : network.zonesOrdered()) {
            long dm = d.demand().getOrDefault(z.zoneId, 0L);
            if (dm > 0) {
                long got = received.getOrDefault(z.zoneId, 0L);
                unmet += dm - got;
                demandZones.add(obj("zoneId", z.zoneId, "zoneName", z.name, "demand", dm, "received", got, "unmet", dm - got));
            }
        }
        List<Map<String, Object>> steps = new ArrayList<>();
        for (AugmentingStep s : r.steps()) {
            List<String> names = new ArrayList<>();
            for (int n : s.nodes()) {
                names.add(net.label(n));
            }
            steps.add(obj("step", s.step(), "path", names, "bottleneck", s.bottleneck()));
        }
        List<Map<String, Object>> bottlenecks = new ArrayList<>();
        for (int id : r.cutEdgeIds()) {
            EdgeMeta m = meta.get(id);
            String from = m.kind() == Kind.SUPPLY ? "SUPPLY (source)" : zm.get(m.fromZone()).name;
            String to = m.kind() == Kind.DEMAND ? "DEMAND (sink)" : zm.get(m.toZone()).name;
            bottlenecks.add(obj("fromName", from, "toName", to, "capacity", m.capacity(), "kind", m.kind().name()));
        }
        // decompose the flow into warehouse -> destination deliveries
        List<long[]> deliveries = new ArrayList<>();
        List<Map<String, Object>> deliveryDto = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        int guard = 0;
        while (guard++ < 100000) {
            int[] prevEdge = new int[net.nodeCount()];
            Arrays.fill(prevEdge, -2);
            prevEdge[source] = -1;
            Deque<Integer> stack = new ArrayDeque<>();
            stack.push(source);
            while (!stack.isEmpty() && prevEdge[sink] == -2) {
                int u = stack.pop();
                for (int e = 0; e < meta.size(); e++) {
                    if (rem[e] > 0 && net.edgeFrom(e) == u && prevEdge[net.edgeTo(e)] == -2) {
                        prevEdge[net.edgeTo(e)] = e;
                        stack.push(net.edgeTo(e));
                    }
                }
            }
            if (prevEdge[sink] == -2) {
                break;
            }
            List<Integer> pathEdges = new ArrayList<>();
            for (int v = sink; v != source; v = net.edgeFrom(prevEdge[v])) {
                pathEdges.add(prevEdge[v]);
            }
            Collections.reverse(pathEdges);
            long bn = Long.MAX_VALUE;
            for (int e : pathEdges) {
                bn = Math.min(bn, rem[e]);
            }
            for (int e : pathEdges) {
                rem[e] -= bn;
            }
            long fromZone = meta.get(pathEdges.get(0)).toZone();
            long toZone = meta.get(pathEdges.get(pathEdges.size() - 1)).fromZone();
            List<String> pathNames = new ArrayList<>();
            for (int k = 0; k < pathEdges.size() - 1; k++) {
                pathNames.add(net.label(net.edgeTo(pathEdges.get(k))));
            }
            String key = fromZone + ">" + toZone;
            if (seen.containsKey(key)) {
                long[] row = deliveries.get(seen.get(key));
                row[2] += bn;
                deliveryDto.get(seen.get(key)).put("quantity", row[2]);
            } else {
                seen.put(key, deliveries.size());
                deliveries.add(new long[]{fromZone, toZone, bn});
                deliveryDto.add(obj("fromZoneId", fromZone, "fromZoneName", zm.get(fromZone).name, "toZoneId", toZone,
                        "toZoneName", zm.get(toZone).name, "quantity", bn, "path", pathNames));
            }
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (CityZone z : network.zonesOrdered()) {
            nodes.add(obj("zoneId", z.zoneId, "name", z.name, "role", z.zoneRole, "latitude", z.latitude, "longitude", z.longitude));
        }
        Map<String, Object> dto = obj("resourceTypeId", typeId, "resourceName", rt.name, "unit", rt.unit, "totalSupply", totalSupply,
                "totalDemand", totalDemand, "maxFlow", r.maxFlow(), "unmetDemand", unmet, "feasible", unmet == 0,
                "deliveries", deliveryDto, "edgeFlows", edgeFlows, "demandZones", demandZones, "augmentingPaths", steps,
                "bottlenecks", bottlenecks, "zeroCapacityEdges", zeroEdges, "unusableEdges", unusable, "nodes", nodes);
        return new Plan(dto, r.maxFlow(), deliveries, net.nodeCount());
    }

    public Map<String, Object> preview(long typeId, List<Long> blockedRoadIds) {
        long t0 = System.nanoTime();
        Plan p = computePlan(typeId, blockedRoadIds);
        Map<String, Object> out = new LinkedHashMap<>(p.dto());
        out.put("execution", exec.record("EDMONDS_KARP", t0, p.inputSize(), "max flow " + p.maxFlow()));
        return out;
    }

    /** Recomputes the plan and applies it in ONE transaction; stale previews are rejected with 409. */
    @Transactional
    public synchronized Map<String, Object> commit(long typeId, List<Long> blockedRoadIds, Long expectedMaxFlow, boolean confirm) {
        long t0 = System.nanoTime();
        if (!confirm) {
            throw ApiException.badRequest("confirm must be true to commit a distribution plan");
        }
        Plan p = computePlan(typeId, blockedRoadIds);
        if (expectedMaxFlow == null || expectedMaxFlow != p.maxFlow()) {
            throw ApiException.conflict("Preview is stale: max flow is now " + p.maxFlow() + " but the preview showed " + expectedMaxFlow
                    + ". Run the preview again.");
        }
        if (p.deliveries().isEmpty()) {
            throw ApiException.infeasible("Nothing to distribute: the plan moves 0 units", null);
        }
        Map<Long, ResourceInventory> inv = new HashMap<>();
        for (ResourceInventory r : invRepo.findByResourceTypeId(typeId)) {
            inv.put(r.zoneId, r);
        }
        Map<Long, Long> after = new HashMap<>();
        for (long[] dl : p.deliveries()) {
            after.merge(dl[0], -dl[2], Long::sum);
            after.merge(dl[1], dl[2], Long::sum);
        }
        for (Map.Entry<Long, Long> e : after.entrySet()) {
            ResourceInventory r = inv.get(e.getKey());
            long result = r.quantityAvailable + e.getValue();
            if (result < 0 || result > r.quantityCapacity) {
                throw ApiException.infeasible("Plan would leave zone " + e.getKey() + " with invalid stock " + result, null);
            }
        }
        String batch = "FLOW-" + System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now().withNano(0);
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Long, CityZone> zm = network.zoneMap();
        for (long[] dl : p.deliveries()) {
            ResourceAllocation a = new ResourceAllocation();
            a.resourceTypeId = typeId;
            a.fromZone = dl[0];
            a.toZone = dl[1];
            a.quantity = (int) dl[2];
            a.createdAt = now;
            a.batchRef = batch;
            a = allocRepo.save(a);
            rows.add(obj("id", a.allocationId, "fromZoneName", zm.get(dl[0]).name, "toZoneName", zm.get(dl[1]).name, "quantity", dl[2]));
        }
        SegmentTree tree = trees().get(typeId);
        for (Map.Entry<Long, Long> e : after.entrySet()) {
            ResourceInventory r = inv.get(e.getKey());
            r.quantityAvailable = (int) (r.quantityAvailable + e.getValue());
            invRepo.save(r);
            tree.set(zm.get(e.getKey()).zoneIndex, r.quantityAvailable);
        }
        Map<String, Object> out = obj("committed", true, "allocations", rows, "maxFlow", p.maxFlow(), "batchRef", batch);
        out.put("execution", exec.record("EDMONDS_KARP_COMMIT", t0, p.inputSize(), "committed " + p.maxFlow() + " units"));
        return out;
    }

    public List<Map<String, Object>> allocations(int limit) {
        Map<Long, CityZone> zm = network.zoneMap();
        Map<Long, ResourceType> tm = new HashMap<>();
        for (ResourceType t : typeRepo.findAll()) {
            tm.put(t.resourceTypeId, t);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (ResourceAllocation a : allocRepo.findAllByOrderByCreatedAtDescAllocationIdDesc()) {
            if (out.size() >= Math.max(1, Math.min(limit, 500))) {
                break;
            }
            ResourceType t = tm.get(a.resourceTypeId);
            out.add(obj("id", a.allocationId, "resourceTypeId", a.resourceTypeId, "resourceName", t == null ? null : t.name,
                    "fromZoneName", zm.get(a.fromZone).name, "toZoneName", zm.get(a.toZone).name, "quantity", a.quantity,
                    "createdAt", a.createdAt));
        }
        return out;
    }
}
