package com.cityshield.benchmark;

import com.cityshield.algorithm.dsu.DisjointSetUnion;
import com.cityshield.algorithm.dsu.Edge;
import com.cityshield.algorithm.heap.HeapEntry;
import com.cityshield.algorithm.heap.IncidentMaxHeap;
import com.cityshield.algorithm.kmp.KmpMatcher;
import com.cityshield.algorithm.kmp.NaiveMatcher;
import com.cityshield.algorithm.matching.KuhnMatcher;
import com.cityshield.algorithm.maxflow.EdmondsKarp;
import com.cityshield.algorithm.maxflow.FlowNetwork;
import com.cityshield.algorithm.quickselect.RandomizedQuickselect;
import com.cityshield.algorithm.segmenttree.LinearScan;
import com.cityshield.algorithm.segmenttree.SegmentTree;
import com.cityshield.algorithm.setcover.CoverResult;
import com.cityshield.algorithm.setcover.CoverSet;
import com.cityshield.algorithm.setcover.ExactSetCover;
import com.cityshield.algorithm.setcover.GreedySetCover;
import com.cityshield.algorithm.setcover.SizeBaselineCover;
import com.cityshield.algorithm.tsp.GeoDistance;
import com.cityshield.algorithm.tsp.HeldKarpTsp;
import com.cityshield.algorithm.tsp.MetricTspApproximation;
import com.cityshield.algorithm.tsp.NearestNeighbourTsp;
import com.cityshield.algorithm.tsp.TspResult;
import com.cityshield.algorithm.trie.Trie;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Measures every assessed algorithm against a simple baseline on generated inputs (fixed seed, so inputs are
 * reproducible; timings of course depend on the machine). Nothing here is hard-coded: every number in the
 * returned rows is a wall-clock measurement taken by {@link #time}. Pure Java, no Spring.
 */
public final class BenchmarkHarness {
    public static final List<String> ALGORITHMS = List.of("HEAP", "DSU", "SEGMENT_TREE", "TRIE", "KMP", "MAX_FLOW",
            "MATCHING", "SET_COVER", "TSP", "QUICKSELECT");
    private static final long SEED = 42L;

    /** Keeps results alive so the JIT cannot discard the measured work. */
    private static volatile long sink;

    private final int reps;

    public BenchmarkHarness(int repetitions) {
        this.reps = Math.max(1, Math.min(repetitions, 25));
    }

    public List<BenchmarkRow> run(String algorithm) {
        String a = algorithm == null ? "ALL" : algorithm.toUpperCase();
        List<BenchmarkRow> out = new ArrayList<>();
        for (String name : ALGORITHMS) {
            if (!"ALL".equals(a) && !a.equals(name)) {
                continue;
            }
            switch (name) {
                case "HEAP" -> heap(out);
                case "DSU" -> dsu(out);
                case "SEGMENT_TREE" -> segmentTree(out);
                case "TRIE" -> trie(out);
                case "KMP" -> kmp(out);
                case "MAX_FLOW" -> maxFlow(out);
                case "MATCHING" -> matching(out);
                case "SET_COVER" -> setCover(out);
                case "TSP" -> tsp(out);
                default -> quickselect(out);
            }
        }
        return out;
    }

    // ---- timing -----------------------------------------------------------------------------------------

    /** One untimed warm-up, then {@code reps} timed runs of a fresh task each; returns the row. */
    private BenchmarkRow time(String algo, String variant, int n, String note, Supplier<Runnable> factory) {
        factory.get().run();
        double[] ms = new double[reps];
        for (int r = 0; r < reps; r++) {
            Runnable task = factory.get();
            long t0 = System.nanoTime();
            task.run();
            ms[r] = (System.nanoTime() - t0) / 1_000_000.0;
        }
        Arrays.sort(ms);
        double median = reps % 2 == 1 ? ms[reps / 2] : (ms[reps / 2 - 1] + ms[reps / 2]) / 2.0;
        return new BenchmarkRow(algo, variant, n, r3(median), r3(ms[0]), r3(ms[reps - 1]), reps, note);
    }

    private static double r3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    // ---- 1. heap vs unsorted list ----------------------------------------------------------------------

    private void heap(List<BenchmarkRow> out) {
        for (int n : new int[]{100, 1_000, 10_000}) {
            double[] pri = new double[n];
            Random rnd = new Random(SEED);
            for (int i = 0; i < n; i++) {
                pri[i] = rnd.nextDouble() * 100;
            }
            int pops = Math.max(1, n / 10);
            String note = n + " inserts then " + pops + " extract-max";
            out.add(time("HEAP", "CUSTOM", n, note, () -> () -> {
                IncidentMaxHeap h = new IncidentMaxHeap();
                for (int i = 0; i < n; i++) {
                    h.insert(new HeapEntry(i, pri[i], i));
                }
                long s = 0;
                for (int i = 0; i < pops; i++) {
                    s += h.extractMax().id();
                }
                sink = s;
            }));
            out.add(time("HEAP", "UNSORTED_LIST", n, note, () -> () -> {
                int[] ids = new int[n];
                double[] p = new double[n];
                int size = 0;
                for (int i = 0; i < n; i++) {
                    ids[size] = i;
                    p[size++] = pri[i];
                }
                long s = 0;
                for (int k = 0; k < pops; k++) {
                    int best = 0;
                    for (int i = 1; i < size; i++) {
                        if (p[i] > p[best]) {
                            best = i;
                        }
                    }
                    s += ids[best];
                    size--;
                    ids[best] = ids[size];
                    p[best] = p[size];
                }
                sink = s;
            }));
        }
    }

    // ---- 2. DSU vs BFS ---------------------------------------------------------------------------------

    private void dsu(List<BenchmarkRow> out) {
        for (int n : new int[]{1_000, 10_000, 100_000}) {
            Random rnd = new Random(SEED);
            int m = n * 2;
            List<Edge> edges = new ArrayList<>(m);
            for (int i = 0; i < m; i++) {
                edges.add(new Edge(i, rnd.nextInt(n), rnd.nextInt(n)));
            }
            Set<Long> blocked = new HashSet<>();
            for (int i = 0; i < m; i += 10) {
                blocked.add((long) i);
            }
            String note = n + " zones, " + m + " roads, " + blocked.size() + " blocked: count components";
            out.add(time("DSU", "CUSTOM", n, note, () -> () -> {
                DisjointSetUnion d = new DisjointSetUnion(n);
                for (Edge e : edges) {
                    if (!blocked.contains(e.id())) {
                        d.union(e.a(), e.b());
                    }
                }
                sink = d.getComponentCount();
            }));
            out.add(time("DSU", "BFS", n, note, () -> () -> {
                List<List<Integer>> adj = new ArrayList<>(n);
                for (int i = 0; i < n; i++) {
                    adj.add(new ArrayList<>());
                }
                for (Edge e : edges) {
                    if (!blocked.contains(e.id())) {
                        adj.get(e.a()).add(e.b());
                        adj.get(e.b()).add(e.a());
                    }
                }
                boolean[] seen = new boolean[n];
                int comps = 0;
                ArrayDeque<Integer> q = new ArrayDeque<>();
                for (int s = 0; s < n; s++) {
                    if (seen[s]) {
                        continue;
                    }
                    comps++;
                    seen[s] = true;
                    q.add(s);
                    while (!q.isEmpty()) {
                        int u = q.poll();
                        for (int v : adj.get(u)) {
                            if (!seen[v]) {
                                seen[v] = true;
                                q.add(v);
                            }
                        }
                    }
                }
                sink = comps;
            }));
        }
    }

    // ---- 3. segment tree vs linear scan ----------------------------------------------------------------

    private void segmentTree(List<BenchmarkRow> out) {
        for (int n : new int[]{1_000, 10_000, 100_000}) {
            Random rnd = new Random(SEED);
            long[] base = new long[n];
            for (int i = 0; i < n; i++) {
                base[i] = rnd.nextInt(1000);
            }
            int q = 2_000;
            int[][] ops = new int[q][3];
            for (int i = 0; i < q; i++) {
                int a = rnd.nextInt(n);
                int b = rnd.nextInt(n);
                ops[i][0] = Math.min(a, b);
                ops[i][1] = Math.max(a, b);
                ops[i][2] = rnd.nextInt(1000);
            }
            String note = q + " range sums, each followed by one point update";
            out.add(time("SEGMENT_TREE", "CUSTOM", n, note, () -> () -> {
                SegmentTree t = new SegmentTree(base);
                long s = 0;
                for (int[] o : ops) {
                    s += t.sum(o[0], o[1]);
                    t.set(o[0], o[2]);
                }
                sink = s;
            }));
            out.add(time("SEGMENT_TREE", "LINEAR_SCAN", n, note, () -> () -> {
                long[] v = base.clone();
                long s = 0;
                for (int[] o : ops) {
                    s += LinearScan.sum(v, o[0], o[1]).sum();
                    v[o[0]] = o[2];
                }
                sink = s;
            }));
        }
    }

    // ---- 4. trie vs linear prefix scan -----------------------------------------------------------------

    private void trie(List<BenchmarkRow> out) {
        for (int n : new int[]{1_000, 10_000, 100_000}) {
            Random rnd = new Random(SEED);
            String[] words = new String[n];
            for (int i = 0; i < n; i++) {
                words[i] = word(rnd, 5 + rnd.nextInt(8));
            }
            int q = 500;
            String[] prefixes = new String[q];
            for (int i = 0; i < q; i++) {
                prefixes[i] = words[rnd.nextInt(n)].substring(0, 3);
            }
            String note = "build once + " + q + " 3-letter prefix queries (limit 10)";
            out.add(time("TRIE", "CUSTOM", n, note, () -> () -> {
                Trie t = new Trie();
                for (int i = 0; i < n; i++) {
                    t.insert(words[i], "ZONE", i);
                }
                long s = 0;
                for (String p : prefixes) {
                    s += t.search(p, 10).suggestions().size();
                }
                sink = s;
            }));
            out.add(time("TRIE", "LINEAR_SCAN", n, note, () -> () -> {
                long s = 0;
                for (String p : prefixes) {
                    int found = 0;
                    for (String w : words) { // full scan: no early exit, so the cost is the honest O(n) per query
                        if (w.startsWith(p)) {
                            found++;
                        }
                    }
                    s += Math.min(found, 10);
                }
                sink = s;
            }));
        }
    }

    private static String word(Random r, int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) ('a' + r.nextInt(26)));
        }
        return sb.toString();
    }

    // ---- 5. KMP vs naive -------------------------------------------------------------------------------

    private void kmp(List<BenchmarkRow> out) {
        for (int n : new int[]{10_000, 100_000, 1_000_000}) {
            Random rnd = new Random(SEED);
            char[] c = new char[n];
            for (int i = 0; i < n; i++) {
                c[i] = rnd.nextInt(10) < 9 ? 'a' : 'b'; // repetitive text: a hard case for the naive matcher
            }
            String text = new String(c);
            String pattern = "a".repeat(30) + "b";
            String note = "text of " + n + " chars over {a,b} (90% a), pattern of 31 chars";
            out.add(time("KMP", "CUSTOM", n, note, () -> () -> sink = KmpMatcher.search(text, pattern).positions().size()));
            out.add(time("KMP", "NAIVE", n, note, () -> () -> sink = NaiveMatcher.search(text, pattern).positions().size()));
        }
    }

    // ---- 6. Edmonds-Karp vs greedy (no residual edges) -------------------------------------------------

    private void maxFlow(List<BenchmarkRow> out) {
        for (int n : new int[]{50, 500, 5_000}) {
            Random rnd = new Random(SEED);
            int m = n * 4;
            int[] from = new int[m];
            int[] to = new int[m];
            int[] cap = new int[m];
            for (int i = 0; i < m; i++) {
                int u = rnd.nextInt(n - 1);
                int v = u + 1 + rnd.nextInt(Math.min(10, n - 1 - u));
                from[i] = u;
                to[i] = v;
                cap[i] = 1 + rnd.nextInt(20);
            }
            String note = n + " nodes, " + m + " edges, source 0, sink " + (n - 1);
            long[] flows = new long[2];
            out.add(time("MAX_FLOW", "CUSTOM", n, note, () -> () -> {
                FlowNetwork net = new FlowNetwork();
                for (int i = 0; i < n; i++) {
                    net.addNode("n" + i);
                }
                for (int i = 0; i < m; i++) {
                    net.addEdge(from[i], to[i], cap[i]);
                }
                flows[0] = EdmondsKarp.solve(net, 0, n - 1).maxFlow();
                sink = flows[0];
            }));
            out.add(time("MAX_FLOW", "GREEDY", n, note, () -> () -> {
                flows[1] = greedyFlow(n, from, to, cap);
                sink = flows[1];
            }));
            // Annotate the quality gap using the values the runs actually produced.
            int k = out.size();
            out.set(k - 2, withNote(out.get(k - 2), note + "; flow value " + flows[0]));
            out.set(k - 1, withNote(out.get(k - 1), note + "; flow value " + flows[1] + " (no residual edges)"));
        }
    }

    private static long greedyFlow(int n, int[] from, int[] to, int[] cap) {
        int m = from.length;
        int[] rem = cap.clone();
        List<List<Integer>> adj = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
        for (int i = 0; i < m; i++) {
            adj.get(from[i]).add(i);
        }
        long total = 0;
        while (true) {
            int[] viaEdge = new int[n];
            Arrays.fill(viaEdge, -1);
            boolean[] seen = new boolean[n];
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(0);
            seen[0] = true;
            while (!q.isEmpty() && !seen[n - 1]) {
                int u = q.poll();
                for (int e : adj.get(u)) {
                    if (rem[e] > 0 && !seen[to[e]]) {
                        seen[to[e]] = true;
                        viaEdge[to[e]] = e;
                        q.add(to[e]);
                    }
                }
            }
            if (!seen[n - 1]) {
                return total;
            }
            int bottleneck = Integer.MAX_VALUE;
            for (int v = n - 1; v != 0; v = from[viaEdge[v]]) {
                bottleneck = Math.min(bottleneck, rem[viaEdge[v]]);
            }
            for (int v = n - 1; v != 0; v = from[viaEdge[v]]) {
                rem[viaEdge[v]] -= bottleneck;
            }
            total += bottleneck;
        }
    }

    private static BenchmarkRow withNote(BenchmarkRow r, String note) {
        return new BenchmarkRow(r.algorithm(), r.variant(), r.inputSize(), r.medianMs(), r.minMs(), r.maxMs(), r.repetitions(), note);
    }

    // ---- 7. Kuhn vs greedy matching --------------------------------------------------------------------

    private void matching(List<BenchmarkRow> out) {
        for (int n : new int[]{100, 1_000, 3_000}) {
            Random rnd = new Random(SEED);
            int[][] adj = new int[n][5];
            for (int s = 0; s < n; s++) {
                for (int k = 0; k < 5; k++) {
                    adj[s][k] = rnd.nextInt(n);
                }
            }
            String note = n + " slots, " + n + " teams, 5 eligible teams per slot";
            int[] size = new int[2];
            out.add(time("MATCHING", "CUSTOM", n, note, () -> () -> {
                size[0] = KuhnMatcher.match(adj, n).size();
                sink = size[0];
            }));
            out.add(time("MATCHING", "GREEDY", n, note, () -> () -> {
                boolean[] used = new boolean[n];
                int matched = 0;
                for (int s = 0; s < n; s++) {
                    for (int t : adj[s]) {
                        if (!used[t]) {
                            used[t] = true;
                            matched++;
                            break;
                        }
                    }
                }
                size[1] = matched;
                sink = matched;
            }));
            int k = out.size();
            out.set(k - 2, withNote(out.get(k - 2), note + "; matched " + size[0]));
            out.set(k - 1, withNote(out.get(k - 1), note + "; matched " + size[1] + " (first-free, no augmenting)"));
        }
    }

    // ---- 8. greedy set cover vs largest-first baseline (+ exact on a small instance) ---------------------

    private List<CoverSet> randomSets(Random rnd, int universe, int count, int width) {
        List<CoverSet> sets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Set<Integer> e = new HashSet<>();
            int w = 2 + rnd.nextInt(width);
            while (e.size() < Math.min(w, universe)) {
                e.add(rnd.nextInt(universe));
            }
            int[] arr = e.stream().mapToInt(Integer::intValue).sorted().toArray();
            sets.add(new CoverSet(i, 1.0 + rnd.nextInt(5), arr));
        }
        return sets;
    }

    private void setCover(List<BenchmarkRow> out) {
        for (int n : new int[]{50, 500, 2_000}) {
            List<CoverSet> sets = randomSets(new Random(SEED), n, n, 12);
            String note = "universe " + n + ", " + n + " candidate sets";
            CoverResult[] res = new CoverResult[2];
            out.add(time("SET_COVER", "CUSTOM", n, note, () -> () -> {
                res[0] = GreedySetCover.solve(n, sets, Set.<Long>of(), null);
                sink = res[0].selectedIds().size();
            }));
            out.add(time("SET_COVER", "SIZE_BASELINE", n, note, () -> () -> {
                res[1] = SizeBaselineCover.solve(n, sets, Set.<Long>of());
                sink = res[1].selectedIds().size();
            }));
            int k = out.size();
            out.set(k - 2, withNote(out.get(k - 2), note + "; " + res[0].selectedIds().size() + " sets, cost " + res[0].totalCost()
                    + ", covered " + res[0].coveredCount()));
            out.set(k - 1, withNote(out.get(k - 1), note + "; " + res[1].selectedIds().size() + " sets, cost " + res[1].totalCost()
                    + ", covered " + res[1].coveredCount()));
        }
        // Small instance where the exact optimum is tractable (m = 18 <= 24).
        List<CoverSet> small = randomSets(new Random(SEED), 40, 18, 10);
        CoverResult[] r = new CoverResult[2];
        out.add(time("SET_COVER", "CUSTOM", 18, "greedy on exact-comparable instance", () -> () -> {
            r[0] = GreedySetCover.solve(40, small, Set.<Long>of(), null);
            sink = r[0].selectedIds().size();
        }));
        out.add(time("SET_COVER", "EXACT", 18, "exhaustive subsets, 18 sets", () -> () -> {
            r[1] = ExactSetCover.solve(40, small, Set.<Long>of());
            sink = r[1].selectedIds().size();
        }));
        int k = out.size();
        out.set(k - 2, withNote(out.get(k - 2), "universe 40, 18 sets; greedy cost " + r[0].totalCost() + ", covered " + r[0].coveredCount()));
        out.set(k - 1, withNote(out.get(k - 1), "universe 40, 18 sets; optimal cost " + r[1].totalCost() + ", covered " + r[1].coveredCount()));
    }

    // ---- 9. TSP: MST 2-approximation vs nearest neighbour (+ Held-Karp exact at 10) ---------------------

    private double[][] points(int n) {
        Random rnd = new Random(SEED + n);
        double[] lat = new double[n];
        double[] lon = new double[n];
        for (int i = 0; i < n; i++) {
            lat[i] = 12.90 + rnd.nextDouble() * 0.2;
            lon[i] = 77.50 + rnd.nextDouble() * 0.2;
        }
        return GeoDistance.matrix(lat, lon);
    }

    private void tsp(List<BenchmarkRow> out) {
        for (int n : new int[]{10, 50, 200}) {
            double[][] d = points(n);
            String note = n + " random points, haversine x 1.3 road factor";
            TspResult[] r = new TspResult[3];
            out.add(time("TSP", "CUSTOM", n, note, () -> () -> {
                r[0] = MetricTspApproximation.solve(d);
                sink = r[0].tour().size();
            }));
            out.add(time("TSP", "NEAREST_NEIGHBOUR", n, note, () -> () -> {
                r[1] = NearestNeighbourTsp.solve(d);
                sink = r[1].tour().size();
            }));
            int k = out.size();
            out.set(k - 2, withNote(out.get(k - 2), note + "; tour " + r3(r[0].length()) + " km, MST " + r3(r[0].mstWeight()) + " km"));
            out.set(k - 1, withNote(out.get(k - 1), note + "; tour " + r3(r[1].length()) + " km"));
            if (n <= HeldKarpTsp.MAX_NODES) {
                out.add(time("TSP", "HELD_KARP_EXACT", n, note, () -> () -> {
                    r[2] = HeldKarpTsp.solve(d);
                    sink = r[2].tour().size();
                }));
                out.set(out.size() - 1, withNote(out.get(out.size() - 1), note + "; optimal tour " + r3(r[2].length()) + " km"));
            }
        }
    }

    // ---- 10. Quickselect vs full sort ------------------------------------------------------------------

    private void quickselect(List<BenchmarkRow> out) {
        for (int n : new int[]{1_000, 10_000, 100_000, 1_000_000}) {
            Random rnd = new Random(SEED);
            double[] v = new double[n];
            for (int i = 0; i < n; i++) {
                v[i] = rnd.nextDouble() * 100;
            }
            String note = "90th percentile of " + n + " random values";
            out.add(time("QUICKSELECT", "CUSTOM", n, note, () -> () -> sink = (long) new RandomizedQuickselect(SEED).percentile(v, 90).value()));
            out.add(time("QUICKSELECT", "ARRAYS_SORT", n, note, () -> () -> {
                double[] c = v.clone();
                Arrays.sort(c);
                sink = (long) c[RandomizedQuickselect.nearestRank(n, 90) - 1];
            }));
        }
    }

    // ---- command line ----------------------------------------------------------------------------------

    /** {@code java com.cityshield.benchmark.BenchmarkHarness [outputCsv] [repetitions]} writes real measurements to CSV. */
    public static void main(String[] args) throws Exception {
        String path = args.length > 0 ? args[0] : "benchmark_results.csv";
        int reps = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        List<BenchmarkRow> rows = new BenchmarkHarness(reps).run("ALL");
        StringBuilder sb = new StringBuilder("algorithm,variant,input_size,median_ms,min_ms,max_ms,repetitions,note\n");
        for (BenchmarkRow r : rows) {
            sb.append(r.algorithm()).append(',').append(r.variant()).append(',').append(r.inputSize()).append(',')
                    .append(r.medianMs()).append(',').append(r.minMs()).append(',').append(r.maxMs()).append(',')
                    .append(r.repetitions()).append(",\"").append(r.note().replace("\"", "'")).append("\"\n");
        }
        java.nio.file.Files.writeString(java.nio.file.Path.of(path), sb.toString());
        System.out.println("Wrote " + rows.size() + " rows to " + path + " (" + System.getProperty("os.name") + ", Java "
                + System.getProperty("java.version") + ", " + Runtime.getRuntime().availableProcessors() + " CPUs)");
    }
}
