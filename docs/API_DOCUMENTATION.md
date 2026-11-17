# CityShield REST API (v1)

Base URL: `http://localhost:8080/api/v1` — JSON only. CityShield is an **academic simulation**; all data is synthetic.

## Conventions

* Lists are plain JSON arrays, except `GET /incidents`, which is paged.
* Timestamps are ISO-8601 strings (`2026-11-14T09:30:00`), server local time.
* Every algorithm endpoint returns an `execution` object: `{ "algorithm": "KUHN_MATCHING", "durationMs": 1.23, "inputSize": 120 }` (`durationMs` is a decimal number).
* **Error body** (HTTP 400, 404, 409, 422, 500):

```json
{ "timestamp": "2026-11-16T11:30:00", "status": 422, "code": "INFEASIBLE",
  "message": "Human readable message", "details": ["optional extra line"], "path": "/api/v1/..." }
```

Codes: `VALIDATION_FAILED` (400, `details` = one string per field), `INVALID_ARGUMENT` (400), `NOT_FOUND` (404), `CONFLICT` (409), `INFEASIBLE` (422), `INTERNAL_ERROR` (500).

## Enumerations

| Name | Values |
| --- | --- |
| Category | `FIRE, MEDICAL, ROAD_ACCIDENT, FLOOD, BUILDING_COLLAPSE, UTILITY_FAILURE, PUBLIC_SAFETY, OTHER` |
| IncidentStatus | `REPORTED, ASSIGNED, IN_PROGRESS, RESOLVED, CANCELLED, ARCHIVED` (active = first three) |
| TeamType | `FIRE, MEDICAL, RESCUE, POLICE, HAZMAT, ENGINEERING` |
| Availability | `AVAILABLE, BUSY, OFF_DUTY` |
| ZoneRole | `WAREHOUSE, HUB, DEMAND` |
| RoadStatus | `OPEN, BLOCKED` |
| CentreStatus | `EXISTING, PROPOSED, UNAVAILABLE` |

Category → compatible team types: FIRE → FIRE, RESCUE, HAZMAT · MEDICAL → MEDICAL · ROAD_ACCIDENT → MEDICAL, POLICE, RESCUE · FLOOD → RESCUE, ENGINEERING · BUILDING_COLLAPSE → RESCUE, MEDICAL, ENGINEERING · UTILITY_FAILURE → ENGINEERING, HAZMAT · PUBLIC_SAFETY → POLICE · OTHER → RESCUE, POLICE, MEDICAL.

## 1. Health and reference data

| Method and path | Response |
| --- | --- |
| `GET /health` | `{ "status":"UP", "service":"CityShield", "database":"UP", "time":"...", "disclaimer":"Academic simulation ..." }` |
| `GET /zones` | `[ZoneDto]` |
| `GET /roads` | `[RoadDto]` |
| `PATCH /roads/{id}/status` body `{ "status":"BLOCKED" }` | `RoadDto` |
| `GET /centres` | `[CentreDto]` |
| `GET /hospitals` | `[HospitalDto]` |
| `GET /resource-types` | `[ResourceTypeDto]` |
| `GET /relief-destinations` | `[DestinationDto]` |
| `GET /admin/db-check` | `{ "tables": { "city_zones": 50, ... }, "checks": [ { "name":"...", "passed":true, "detail":"..." } ], "healthy": true }` |

```text
ZoneDto         { id, name, zoneIndex, latitude, longitude, role, population }
RoadDto         { id, zoneA, zoneB, zoneAName, zoneBName, distanceKm, status }
CentreDto       { id, name, zoneId, zoneName, latitude, longitude, coverageRadiusKm, cost, status, centreType }
HospitalDto     { id, name, zoneId, zoneName, latitude, longitude, bedCapacity, bedsAvailable }
ResourceTypeDto { id, name, unit }
DestinationDto  { id, zoneId, name, latitude, longitude, demandUnits }
```

## 2. Incidents (Module 2)

```text
IncidentDto {
  id, code, category, zoneId, zoneName, latitude, longitude, description,
  severity (1-5), urgency (1-5), affectedPeople, status, reportedAt,
  priorityScore (0-100, 2 decimals, computed by PriorityCalculator),
  factors { s, u, a, r }              // each 0..1
  requirements [ { resourceTypeId, resourceName, unit, quantity } ],
  allowedNextStatuses [ IncidentStatus ]
}
IncidentDetailDto = IncidentDto + { history [ { oldStatus, newStatus, changedAt, note } ], logs [ { loggedAt, text } ], assignments [ { id, teamId, teamName, status, assignedAt } ] }
```

| Method and path | Notes |
| --- | --- |
| `GET /incidents?status=&category=&zoneId=&q=&sort=priority&dir=desc&page=0&size=20` | `status` may be `ACTIVE` (the three active statuses) or a single status. `q` matches code or description (substring). `sort` is `priority` or `time`. Returns `{ content:[IncidentDto], totalElements, page, size, totalPages }`. |
| `GET /incidents/{id}` | `IncidentDetailDto` |
| `POST /incidents` | body `{ category, zoneId, description, severity, urgency, affectedPeople, requirements:[{resourceTypeId, quantity}] }` → 201 `IncidentDto`. Also inserts into the in-memory priority heap. |
| `PUT /incidents/{id}` | same body → `IncidentDto` (re-prioritises in the heap) |
| `PATCH /incidents/{id}/status` | body `{ "status":"IN_PROGRESS", "note":"optional" }`; illegal transition → 409 `CONFLICT` |
| `DELETE /incidents/{id}` | soft delete: sets status `ARCHIVED`; 200 `IncidentDto` |

Validation: description 5–500 chars; severity and urgency 1–5; affectedPeople 0–1,000,000; zoneId must exist; quantity ≥ 1; duplicate resource types in one request are rejected.

## 3. Priority engine (Module 3, Binary Heap)

The heap holds incidents with status `REPORTED` (awaiting dispatch). It is kept in memory and updated on every create, edit and status change.

| Method and path | Response |
| --- | --- |
| `GET /priority/queue?limit=25` | `{ size, items:[ { rank, incident:IncidentDto } ], heapStats:{ insertions, extractions, comparisons, swaps }, highRiskThreshold, execution }` — non-destructive, ordered by priority (ties: earlier report, then lower id). |
| `GET /priority/peek` | `{ incident:IncidentDto \| null }` |
| `POST /priority/extract-next` | Removes the top incident and sets it `IN_PROGRESS`. `{ incident:IncidentDto, remaining }`; 409 if empty. |
| `POST /priority/rebuild` | Rebuilds the heap from the database (bottom-up heapify). `{ size, execution }` |
| `GET /priority/formula` | `{ formula:"P = 100 x (0.40S + 0.25U + 0.20A + 0.15R)", weights:{ s:0.4, u:0.25, a:0.2, r:0.15 }, definitions:{ s:"severity / 5", ... } }` |

## 4. Percentile analytics (Randomized Quickselect, CO5)

`POST /analytics/percentile` body `{ metric:"PRIORITY"|"SEVERITY"|"AFFECTED"|"URGENCY", percentile:90, seed:42, scope:"ACTIVE"|"ALL" }`

```text
{ metric, percentile, seed, scope, n, k (1-based rank used), threshold,
  highRiskCount, highRiskIncidents [ IncidentDto ] (max 25, highest first),
  distribution [ { label, count } ],
  quickselect { comparisons, pivotSelections, partitionRounds, durationMs },
  sortBaseline { durationMs, agrees:true },
  execution }
```
`percentile` must be in (0, 100]; otherwise 400 `INVALID_ARGUMENT`. Empty scope → 422 `INFEASIBLE`.

`POST /analytics/percentile-sweep` body `{ metric, seed, scope }` → `{ rows:[ { percentile, threshold, k, comparisons } ] }` for P50, P75, P90, P95, P99, and `seedRuns:[ { seed, threshold(P90), comparisons, pivotSelections } ]` for seeds 1..5 (same answer, different work).

## 5. Team allocation (Module 4, Kuhn bipartite matching)

```text
TeamDto { id, name, teamType, baseZoneId, baseZoneName, latitude, longitude, centreId, centreName, availability, currentIncidentId }
```

| Method and path | Notes |
| --- | --- |
| `GET /teams` | `[TeamDto]` |
| `POST /teams` / `PUT /teams/{id}` | body `{ name, teamType, baseZoneId, centreId (optional), availability }` → `TeamDto` |
| `PATCH /teams/{id}/availability` | body `{ "availability":"OFF_DUTY" }`; changing a BUSY team returns 409 |
| `GET /allocation/eligibility/{incidentId}` | `[ { team:TeamDto, eligible, reasons:[string] } ]` for manual inspection |
| `POST /allocation/preview` | body `{ incidentIds:[...] (optional, default all REPORTED), requireReachability:true, blockedRoadIds:[...] }` → `AllocationPreview`. **Does not modify data.** |
| `POST /allocation/commit` | body `{ assignments:[ { incidentId, teamId } ] }`; re-validates in one transaction (team still AVAILABLE and eligible, incident still active); marks teams BUSY, incidents ASSIGNED. 409 on stale data. → `{ committed, assignments:[...] }` |
| `GET /allocation/assignments?activeOnly=true` | `[ { id, incidentId, incidentCode, teamId, teamName, teamType, status, assignedAt } ]` |
| `PATCH /allocation/assignments/{id}/release` | releases the team (AVAILABLE) |

```text
AllocationPreview {
  assignments [ { incidentId, incidentCode, category, priorityScore, slot, teamId, teamName, teamType, distanceKm } ],
  unassigned  [ { incidentId, incidentCode, category, priorityScore, slotsNeeded, slotsFilled, reason } ],
  metrics { incidentsConsidered, slotsTotal, slotsMatched, slotsUnmatched, incidentsFullyStaffed, incidentsPartial, incidentsUnstaffed,
            teamsAvailable, teamsUsed, matchingSize, efficiencyPct },
  componentCount, execution }
```
Slots: an incident needs 2 teams when severity ≥ 4, otherwise 1. Incidents are processed in heap (priority) order.

## 6. Resource distribution (Module 5, Segment Tree and Edmonds-Karp)

| Method and path | Response |
| --- | --- |
| `GET /inventory?resourceTypeId=1` | `[ { zoneId, zoneName, zoneIndex, zoneRole, quantity, capacity } ]` ordered by zoneIndex |
| `GET /inventory/summary` | `[ { resourceTypeId, name, unit, available, capacity, utilizationPct } ]` (totals come from the segment trees) |
| `POST /inventory/range-query` | body `{ resourceTypeId, fromIndex, toIndex, compareLinear:true }` → `{ resourceTypeId, fromIndex, toIndex, zones:[names], sum, segmentTree:{ nodesVisited, durationMs }, linearScan:{ sum, elementsScanned, durationMs }, execution }`; `fromIndex > toIndex` or out of range → 400 |
| `POST /inventory/adjust` | body `{ zoneId, resourceTypeId, delta, reason }` → `{ zoneId, resourceTypeId, before, after, capacity }`; result < 0 or > capacity → 422 `INFEASIBLE`. Updates MySQL and the segment tree. |
| `GET /distribution/network?resourceTypeId=1` | `{ nodes:[ { zoneId, name, role, latitude, longitude, supply, demand } ], edges:[ { id, fromZoneId, toZoneId, capacity } ] }` |
| `PUT /distribution/edges/{id}` | body `{ capacity }` (≥ 0) → edge |
| `POST /distribution/preview` | body `{ resourceTypeId, blockedRoadIds:[...] }` → `DistributionPlan`. **Never changes inventory.** |
| `POST /distribution/commit` | body `{ resourceTypeId, blockedRoadIds:[...], expectedMaxFlow, confirm:true }`; recomputes the plan; if its max flow differs from `expectedMaxFlow` → 409 `CONFLICT` (stale preview); otherwise one transaction moves stock and writes `resource_allocations`. → `{ committed:true, allocations:[...], maxFlow }` |
| `GET /distribution/allocations?limit=50` | `[ { id, resourceTypeId, resourceName, fromZoneName, toZoneName, quantity, createdAt } ]` |

```text
DistributionPlan {
  resourceTypeId, resourceName, unit, totalSupply, totalDemand, maxFlow, unmetDemand, feasible,
  deliveries [ { fromZoneId, fromZoneName, toZoneId, toZoneName, quantity, path:[zoneName] } ],
  edgeFlows  [ { fromZoneId, toZoneId, fromName, toName, capacity, flow, saturated } ],
  demandZones [ { zoneId, zoneName, demand, received, unmet } ],
  augmentingPaths [ { step, path:[names], bottleneck } ],
  bottlenecks [ { fromName, toName, capacity, kind:"EDGE"|"SUPPLY"|"DEMAND" } ],   // minimum cut
  zeroCapacityEdges, unusableEdges (edges ignored because the zones are disconnected),
  nodes [ { zoneId, name, role, latitude, longitude } ], execution }
```
Network: super-source → WAREHOUSE zones (capacity = stock), directed distribution edges, DEMAND-side zones → super-sink (capacity = shortage = max(0, required by active incidents − local stock), limited by free storage capacity).

## 7. City connectivity and coverage (Module 6, DSU and Set Cover)

| Method and path | Response |
| --- | --- |
| `GET /network/graph` | `{ zones:[ZoneDto], roads:[RoadDto] }` |
| `POST /network/connectivity` | body `{ blockedRoadIds:[...], includeBlockedInDatabase:true }` → `{ zoneCount, roadCount, openRoadCount, blockedRoadIds, componentCount, components:[ { id, size, zoneIds, zoneNames, isLargest } ], zoneComponent:{ "zoneId":componentId }, isolatedZones:[ZoneDto], criticalRoads:[ RoadDto ] (roads whose blocking would split a component), dsuStats:{ unions, finds }, execution }` |
| `POST /coverage/set-cover` | body `{ radiusKm:4.0, universe:"ALL"\|"ACTIVE_INCIDENT_ZONES", maxNewCentres:null }` → `CoverageResult` |

```text
CoverageResult {
  radiusKm, universe, universeSize,
  existing { coveredZoneIds, uncoveredZoneIds, coveragePct },
  steps [ { step, centreId, centreName, centreStatus, zoneName, newlyCoveredZoneIds, newlyCoveredNames, cost, ratio } ],
  final { coveredZoneIds, uncoveredZoneIds, coveragePct },
  greedy { centreIds, totalCost },
  exact  { available:true, centreIds, totalCost, candidateCount, subsetsChecked },
  approximationRatio (greedyCost / exactCost, null if undefined), note,
  suggestedSites [ { zoneId, zoneName, latitude, longitude, wouldCover } ],
  highRiskUncoveredZones [ { zoneId, zoneName, riskScore } ]   // zones above the Quickselect P90 of zone risk
  centres [ CentreDto ], zones [ ZoneDto ], execution }
```

## 8. Route optimisation (Module 7, Metric TSP 2-approximation)

`POST /routes/tsp` body `{ depotCentreId, destinationIds:[1,2,3], compareHeldKarp:true }` (1–30 destinations, all distinct)

```text
{ depot { name, latitude, longitude },
  route [ { seq, id, name, latitude, longitude, legKm } ],      // starts and ends at the depot
  totalKm, mstWeightKm, mstEdges [ { from, to, km } ], preorder [names],
  nearestNeighbour { route:[names], totalKm },
  heldKarp { available, reason, route:[names], totalKm },        // available only when depot + stops <= 12
  ratioToOptimal (approx / Held-Karp, null when unavailable), guarantee,
  distanceModel:"Haversine x 1.3 simulation estimate, not road navigation", execution }
```

## 9. Search and analytics lab (Module 8)

| Method and path | Response |
| --- | --- |
| `GET /search/autocomplete?q=ko&types=ZONE,HOSPITAL,CENTRE,CATEGORY&limit=10` | `{ query, suggestions:[ { text, type, refId } ], nodesVisited, durationMs }`; empty `q` → 400 |
| `POST /search/incidents` body `{ pattern, fields:["DESCRIPTION","LOGS"], caseSensitive:false, limit:50 }` | `{ pattern, lps:[int], recordsScanned, totalMatches, matches:[ { incidentId, incidentCode, zoneName, field, text, positions:[int] } ], kmp:{ comparisons, durationMs }, naive:{ comparisons, durationMs }, execution }`; empty pattern → 400 |

## 10. Dashboard, benchmarks, execution logs

`GET /dashboard/summary` →

```text
{ totalIncidents, activeIncidents, criticalIncidents (active with severity 5), highRiskIncidents (active, priority >= Quickselect P90), highRiskThreshold,
  teams { total, available, busy, offDuty }, hospitals { count, totalBeds, availableBeds, occupancyPct },
  centres { existing, proposed, unavailable }, zones, 
  inventory [ { resourceTypeId, name, unit, available, capacity, utilizationPct } ],
  connectivity { componentCount, isolatedZones, blockedRoads },
  coverage { radiusKm, coveredZones, totalZones, coveragePct },
  recentIncidents [IncidentDto] (10 newest),
  priorityDistribution [ { label:"0-20", count } ... ] (active incidents),
  statusBreakdown [ { status, count } ], categoryBreakdown [ { category, count } ],
  incidentsPerDay [ { date, count } ] (last 14 days) }
```

| Method and path | Response |
| --- | --- |
| `POST /benchmarks/run` body `{ algorithm:"ALL", repetitions:5 }` | Runs the benchmark harness on the server and stores rows. `{ rows:[BenchmarkRow], machine:{ os, javaVersion, cpus }, generatedAt }` |
| `GET /benchmarks/results?algorithm=` | latest stored rows `[BenchmarkRow]` |
| `GET /executions?algorithm=&limit=50` | `[ { id, algorithm, runType, inputSize, durationMs, summary, runAt } ]` |

```text
BenchmarkRow { algorithm, variant ("CUSTOM" or the baseline name), inputSize, medianMs, minMs, maxMs, repetitions, note }
```
