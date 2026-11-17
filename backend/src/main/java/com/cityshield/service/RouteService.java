package com.cityshield.service;

import static com.cityshield.util.Json.obj;
import static com.cityshield.util.Json.round;

import com.cityshield.algorithm.tsp.*;
import com.cityshield.entity.*;
import com.cityshield.exception.ApiException;
import com.cityshield.repository.*;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Relief delivery route optimisation (Module 7): metric TSP 2-approximation with baselines. */
@Service
public class RouteService {
    private final EmergencyCentreRepository centres;
    private final ReliefDestinationRepository destinations;
    private final DeliveryRouteRepository routeRepo;
    private final DeliveryRouteStopRepository stopRepo;
    private final NetworkService network;
    private final ExecutionLogService exec;

    public RouteService(EmergencyCentreRepository centres, ReliefDestinationRepository destinations, DeliveryRouteRepository routeRepo,
                        DeliveryRouteStopRepository stopRepo, NetworkService network, ExecutionLogService exec) {
        this.centres = centres;
        this.destinations = destinations;
        this.routeRepo = routeRepo;
        this.stopRepo = stopRepo;
        this.network = network;
        this.exec = exec;
    }

    @Transactional
    public Map<String, Object> tsp(Long depotCentreId, List<Long> destinationIds, boolean compareHeldKarp) {
        long t0 = System.nanoTime();
        if (depotCentreId == null) {
            throw ApiException.badRequest("depotCentreId is required");
        }
        if (destinationIds == null || destinationIds.isEmpty() || destinationIds.size() > 30) {
            throw ApiException.badRequest("Select between 1 and 30 delivery destinations");
        }
        if (new HashSet<>(destinationIds).size() != destinationIds.size()) {
            throw ApiException.badRequest("Destinations must be distinct");
        }
        EmergencyCentre depot = centres.findById(depotCentreId).orElseThrow(() -> ApiException.notFound("Centre " + depotCentreId + " not found"));
        if ("UNAVAILABLE".equals(depot.status)) {
            throw ApiException.badRequest("Depot centre " + depot.name + " is UNAVAILABLE");
        }
        Map<Long, CityZone> zm = network.zoneMap();
        List<ReliefDestination> dests = new ArrayList<>();
        for (Long id : destinationIds) {
            dests.add(destinations.findById(id).orElseThrow(() -> ApiException.badRequest("Unknown relief destination " + id)));
        }
        int n = dests.size() + 1;
        double[] lat = new double[n];
        double[] lon = new double[n];
        String[] name = new String[n];
        CityZone dz = zm.get(depot.zoneId);
        lat[0] = dz.latitude;
        lon[0] = dz.longitude;
        name[0] = depot.name;
        for (int i = 0; i < dests.size(); i++) {
            CityZone z = zm.get(dests.get(i).zoneId);
            lat[i + 1] = z.latitude;
            lon[i + 1] = z.longitude;
            name[i + 1] = z.name;
        }
        double[][] d = GeoDistance.matrix(lat, lon);
        TspResult approx = MetricTspApproximation.solve(d);
        TspResult nn = NearestNeighbourTsp.solve(d);

        List<Map<String, Object>> route = new ArrayList<>();
        for (int s = 0; s < approx.tour().size(); s++) {
            int k = approx.tour().get(s);
            double leg = s == 0 ? 0 : d[approx.tour().get(s - 1)][k];
            route.add(obj("seq", s, "id", k == 0 ? depot.centreId : dests.get(k - 1).destinationId, "name", name[k],
                    "latitude", lat[k], "longitude", lon[k], "legKm", round(leg, 3)));
        }
        List<Map<String, Object>> mst = new ArrayList<>();
        for (int[] e : approx.mstEdges()) {
            mst.add(obj("from", name[e[0]], "to", name[e[1]], "km", round(d[e[0]][e[1]], 3)));
        }
        Map<String, Object> hk;
        Double ratio = null;
        if (!compareHeldKarp) {
            hk = obj("available", false, "reason", "Comparison not requested", "route", List.of(), "totalKm", null);
        } else if (n > 12) {
            hk = obj("available", false, "reason", "Held-Karp exact solver is limited to 12 nodes (depot + stops); this run has " + n,
                    "route", List.of(), "totalKm", null);
        } else {
            TspResult opt = HeldKarpTsp.solve(d);
            hk = obj("available", true, "reason", null, "route", names(opt.tour(), name), "totalKm", round(opt.length(), 3));
            ratio = opt.length() > 0 ? round(approx.length() / opt.length(), 4) : 1.0;
        }
        DeliveryRoute dr = new DeliveryRoute();
        dr.depotCentreId = depot.centreId;
        dr.algorithm = "TSP_2APPROX";
        dr.totalKm = approx.length();
        dr.createdAt = LocalDateTime.now().withNano(0);
        dr = routeRepo.save(dr);
        int order = 0;
        for (int k : approx.tour().subList(0, approx.tour().size() - 1)) {
            DeliveryRouteStop st = new DeliveryRouteStop();
            st.routeId = dr.routeId;
            st.stopOrder = order++;
            st.zoneId = k == 0 ? depot.zoneId : dests.get(k - 1).zoneId;
            stopRepo.save(st);
        }
        Map<String, Object> out = obj("routeId", dr.routeId,
                "depot", obj("name", depot.name, "latitude", lat[0], "longitude", lon[0]), "route", route,
                "totalKm", round(approx.length(), 3), "mstWeightKm", round(approx.mstWeight(), 3), "mstEdges", mst,
                "preorder", names(approx.preorder(), name),
                "nearestNeighbour", obj("route", names(nn.tour(), name), "totalKm", round(nn.length(), 3)),
                "heldKarp", hk, "ratioToOptimal", ratio,
                "guarantee", "The double-tree algorithm returns a tour of length at most 2 x OPT because MST weight <= OPT and "
                        + "shortcutting preserves length under the triangle inequality. This holds only for metric distances; "
                        + "the tour is NOT guaranteed to be the shortest route.",
                "distanceModel", "Haversine x 1.3 simulation estimate, not road navigation");
        out.put("execution", exec.record("TSP_2_APPROX", t0, n, "tour " + round(approx.length(), 2) + " km"));
        return out;
    }

    private static List<String> names(List<Integer> idx, String[] name) {
        List<String> out = new ArrayList<>();
        for (int i : idx) {
            out.add(name[i]);
        }
        return out;
    }
}
