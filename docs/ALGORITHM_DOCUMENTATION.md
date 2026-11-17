# CityShield — Algorithm documentation

All ten algorithms are implemented by hand in `backend/src/main/java/com/cityshield/algorithm/` (pure Java, no Spring, no library data structures standing in for the assessed ones). Each is covered by JUnit 5 tests in `backend/src/test/java/com/cityshield/algorithm/` (98 tests in total).

## 1. Binary max-heap — `heap/IncidentMaxHeap`
**Used for:** the emergency priority queue (only `REPORTED` incidents).
Array-backed heap with an id→index map, so `remove(id)` and `updatePriority(id, p)` are O(log n). Ties break on earlier `reportedAt`, then lower id. `buildHeap` is bottom-up heapify, O(n). Counters (`insertions, extractions, comparisons, swaps`) are exposed for the UI.
Complexity: insert/extract/update/remove O(log n); peek O(1); build O(n).
**Priority formula** (`PriorityCalculator`): `P = 100 × (0.40·S + 0.25·U + 0.20·A + 0.15·R)` with S = severity/5, U = urgency/5, A = min(affected, 5000)/5000, R = shortfall of the incident's required units against stock in its own zone. Every factor is clamped to 0–1, so P is always 0–100 and higher always means more urgent.

## 2. Disjoint Set Union — `dsu/DisjointSetUnion`, `ConnectivityAnalyzer`
**Used for:** which zones are still connected when roads are blocked.
Path compression + union by rank; near-constant amortised time per operation. `ConnectivityAnalyzer` builds components from open roads, reports isolated zones and **critical roads** (roads whose removal would split a component). The structure is rebuilt whenever road status changes. Critical-road detection re-runs the union pass once per open road, so it is O(E·(V+E)); fine at city scale (114 roads) but it is why the benchmark measures the plain DSU component count rather than the full analyser.

## 3. Segment tree — `segmenttree/SegmentTree`
**Used for:** inventory range sums (one tree per resource type, leaves ordered by zone index).
Recursive tree with point update and range sum, O(log n); `getLastNodesVisited()` shows the work done. `LinearScan` is the O(n) baseline used for comparison in the UI.

## 4. Trie — `trie/Trie`
**Used for:** prefix autocomplete over zones, hospitals, centres and incident categories.
Insert and prefix search cost O(L) in the prefix length plus the number of results collected; `nodesVisited` is returned.

## 5. Knuth–Morris–Pratt — `kmp/KmpMatcher`
**Used for:** pattern search in incident descriptions and logs (case-insensitive option).
Failure table (LPS) in O(m), search in O(n + m). `NaiveMatcher` runs alongside and both report character comparisons.

## 6. Edmonds–Karp — `maxflow/FlowNetwork`, `EdmondsKarp`
**Used for:** relief distribution. Network: SUPPLY source → warehouse stock → distribution edges (unusable when blocked roads disconnect their endpoints) → shortage zones → DEMAND sink.
BFS augmenting paths on the residual graph, O(V·E²). Returns per-edge flow, augmenting steps, and the min cut. The service decomposes flow into deliveries. **Preview** computes the plan without writing; **commit** recomputes, compares `expectedMaxFlow` (409 if stale) and updates stock in one transaction.

## 7. Kuhn bipartite matching — `matching/KuhnMatcher`
**Used for:** assigning teams to incident *slots* (an incident needing two teams has two slots). Slots are processed in priority order; eligibility = compatible team type, available, and reachable if roads are considered. Augmenting-path DFS, O(V·E). Preview does not mark teams busy; commit does, transactionally.

## 8. Greedy set cover — `setcover/GreedySetCover`, `ExactSetCover`, `SizeBaselineCover`
**Used for:** choosing extra emergency centres so every zone is within a radius of one. Greedy repeatedly picks the set with the lowest cost per newly covered element (approximation ≤ H(n) ≈ ln n + 1). Existing centres are forced into the solution. `ExactSetCover` enumerates subsets (limited to 24 candidates) so the greedy answer can be compared with the optimum on small cases; the API marks "exact" unavailable beyond that limit.

## 9. Metric TSP 2-approximation — `tsp/MetricTspApproximation`
**Used for:** delivery route from a depot through relief destinations. Prim's MST on the complete distance graph, preorder walk, shortcut repeats. If the triangle inequality holds the tour is ≤ 2 × optimal; `validateMetric` checks it. Distance = haversine × 1.3 (a simulation estimate). `NearestNeighbourTsp` and exact `HeldKarpTsp` (≤ 12 nodes) are comparison baselines.

## 10. Randomized Quickselect — `quickselect/RandomizedQuickselect`
**Used for:** the P90 high-risk priority threshold and other percentiles. Three-way partitioning, seeded random pivots (the same seed reproduces the same run). Expected O(n). Percentile uses nearest-rank. The API also sorts the data and reports whether both methods agree.

## Measured performance
Real measurements from `data/benchmarks/benchmark_results.csv` (produced by `BenchmarkHarness`, 7 repetitions, median of timed runs after one warm-up; the machine is the cloud sandbox used to build the project — rerun on your own PC for your own numbers). Selected rows (milliseconds):

| Algorithm | Size | Custom | Baseline |
|---|---|---|---|
| Heap (n inserts + n/10 extractions) | 10,000 | 2.82 | unsorted list 4.24 |
| DSU component count | 100,000 | 7.88 | BFS 37.5 |
| Segment tree (2,000 queries + updates) | 100,000 | 1.44 | linear scan 28.0 |
| Trie (build + 500 prefix queries) | 100,000 | 146 | linear scan 297 |
| KMP (31-char pattern) | 1,000,000 | 5.35 | naive 16.4 |
| Edmonds–Karp | 500 nodes | 2.92 (flow 45) | greedy 1.50 (flow 40) |
| Kuhn matching | 3,000 | 7.96 (2,983 matched) | first-free 0.13 (2,644 matched) |
| Greedy set cover | 2,000 | 502 (cost 765) | size baseline 29.6 (cost 1,518) |
| Metric TSP | 200 | 5.12 (441 km) | nearest neighbour 0.15 (379 km) |
| Quickselect P90 | 1,000,000 | 11.9 | Arrays.sort 67.6 |

Honest reading of the data: the custom heap, DSU, segment tree, trie (large), KMP and Quickselect are faster at scale. Edmonds–Karp, Kuhn and greedy set cover are **slower** than their naive baselines but return better answers (more flow, more matches, cheaper cover). On the 18-set exact comparison the greedy cost was 31 against an optimum of 30. Nearest-neighbour produced *shorter* tours than the 2-approximation in every run here; the approximation's value is its worst-case guarantee, not typical quality. Small inputs (e.g. heap at n = 100, trie at n = 1,000) favour the simple baseline because constant factors and JIT warm-up dominate.
