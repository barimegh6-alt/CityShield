"""Deterministic synthetic Mumbai-inspired dataset generator for CityShield.

Usage:  python generate_dataset.py          (writes CSVs to ../synthetic and ../../backend/src/main/resources/seed)
All values are SYNTHETIC; place names/coordinates are approximate and used only for realism.
Seed is fixed (20261116) so output is byte-for-byte reproducible.
"""
import csv, math, os, random, shutil, sys
from datetime import datetime, timedelta
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from spec import TABLES, NO_SEED

SEED = 20261116
BASE = datetime(2026, 11, 15, 12, 0, 0)
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "data", "synthetic")
SEED_DIR = os.path.join(ROOT, "backend", "src", "main", "resources", "seed")
rnd = random.Random(SEED)

ZONES = [("Colaba",18.9067,72.8147),("Cuffe Parade",18.9145,72.8191),("Nariman Point",18.9256,72.8242),("Fort",18.9322,72.8347),
 ("Marine Lines",18.9430,72.8230),("Malabar Hill",18.9548,72.7985),("Girgaon",18.9540,72.8134),("Mumbai Central",18.9690,72.8193),
 ("Grant Road",18.9630,72.8150),("Tardeo",18.9715,72.8115),("Byculla",18.9760,72.8330),("Mazgaon",18.9690,72.8440),
 ("Lalbaug",18.9890,72.8340),("Worli",19.0090,72.8170),("Lower Parel",18.9950,72.8300),("Parel",19.0050,72.8400),
 ("Prabhadevi",19.0150,72.8300),("Dadar",19.0178,72.8478),("Matunga",19.0270,72.8570),("Sion",19.0430,72.8640),
 ("Wadala",19.0170,72.8590),("Dharavi",19.0430,72.8530),("Mahim",19.0410,72.8400),("Bandra",19.0596,72.8295),
 ("Bandra Kurla Complex",19.0690,72.8700),("Khar",19.0700,72.8360),("Santacruz",19.0810,72.8410),("Kurla",19.0726,72.8845),
 ("Chembur",19.0620,72.9005),("Govandi",19.0550,72.9150),("Mankhurd",19.0480,72.9320),("Antop Hill",19.0260,72.8700),
 ("Vile Parle",19.1000,72.8450),("Juhu",19.1075,72.8263),("Andheri",19.1197,72.8464),("Sakinaka",19.1030,72.8890),
 ("Ghatkopar",19.0860,72.9080),("Vikhroli",19.1100,72.9270),("Kanjurmarg",19.1300,72.9300),("Powai",19.1176,72.9060),
 ("Bhandup",19.1440,72.9370),("Mulund",19.1720,72.9560),("Jogeshwari",19.1360,72.8490),("Versova",19.1310,72.8140),
 ("Goregaon",19.1663,72.8526),("Aarey Colony",19.1550,72.8780),("Malad",19.1860,72.8480),("Kandivali",19.2050,72.8520),
 ("Borivali",19.2307,72.8567),("Dahisar",19.2500,72.8590)]
assert len(ZONES) == 50
WAREHOUSES = {"Andheri", "Kurla", "Dadar", "Borivali"}
HUBS = {"Bandra", "Ghatkopar", "Chembur", "Powai", "Goregaon", "Worli", "Sion", "Mumbai Central"}

def hav(a, b):
    p1, p2 = math.radians(a[1]), math.radians(b[1])
    dl = math.radians(b[2] - a[2]); dp = p2 - p1
    h = math.sin(dp/2)**2 + math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2
    return 2 * 6371.0088 * math.asin(math.sqrt(h))

def iso(dt): return dt.strftime("%Y-%m-%dT%H:%M:%S")

# ---- zones (ordered south->north; zone_index = order) -------------------------------------------
zs = sorted(ZONES, key=lambda z: (z[1], z[2]))
zone_rows, zid = [], {}
for i, (n, la, lo) in enumerate(zs):
    role = "WAREHOUSE" if n in WAREHOUSES else "HUB" if n in HUBS else "DEMAND"
    pop = int(rnd.uniform(60, 520) * 1000)
    zone_rows.append([i + 1, n, i, la, lo, role, pop]); zid[n] = i + 1
Z = {r[0]: r for r in zone_rows}
def zkm(a, b): return hav(("", Z[a][3], Z[a][4]), ("", Z[b][3], Z[b][4]))

# ---- roads: MST + k nearest neighbours; Dahisar limited to exactly 2 roads ------------------------
ids = [r[0] for r in zone_rows]
cand = sorted(((zkm(a, b), a, b) for a in ids for b in ids if a < b))
parent = {i: i for i in ids}
def find(x):
    while parent[x] != x: parent[x] = parent[parent[x]]; x = parent[x]
    return x
edges = set()
for d, a, b in cand:
    if find(a) != find(b): parent[find(a)] = find(b); edges.add((a, b))
for a in ids:
    near = sorted((zkm(a, b), b) for b in ids if b != a)[:3]
    for d, b in near: edges.add((min(a, b), max(a, b)))
for d, a, b in cand:
    if len(edges) >= 115: break
    if (a, b) not in edges: edges.add((a, b))
dah = zid["Dahisar"]
dah_edges = sorted([e for e in edges if dah in e], key=lambda e: zkm(*e))
mst_dah = None
for d, a, b in cand:  # keep the shortest edge that connects Dahisar plus the 2nd shortest
    pass
for e in dah_edges[2:]:
    edges.discard(e)
roads = sorted(edges)
deg = {i: 0 for i in ids}
for a, b in roads: deg[a] += 1; deg[b] += 1
road_rows = [[i + 1, a, b, round(zkm(a, b) * 1.3, 3), "OPEN"] for i, (a, b) in enumerate(roads)]

def connected(edge_list):
    adj = {i: [] for i in ids}
    for a, b in edge_list: adj[a].append(b); adj[b].append(a)
    seen, st = {ids[0]}, [ids[0]]
    while st:
        u = st.pop()
        for v in adj[u]:
            if v not in seen: seen.add(v); st.append(v)
    return len(seen) == len(ids)
assert connected(roads), "road graph must be connected"
assert len(roads) >= 100, len(roads)

# ---- distribution edges ----------------------------------------------------------------------------
role = {r[0]: r[5] for r in zone_rows}
W = [i for i in ids if role[i] == "WAREHOUSE"]; H = [i for i in ids if role[i] == "HUB"]; D = [i for i in ids if role[i] == "DEMAND"]
dedges = {}
def cap(): return rnd.randrange(50, 801, 10)
for w in W:
    for h in sorted(H, key=lambda h: zkm(w, h))[:3]: dedges[(w, h)] = cap()
for d in D:
    for h in sorted(H, key=lambda h: zkm(d, h))[:rnd.choice([1, 2, 2])]: dedges[(h, d)] = cap()
    if rnd.random() < 0.25:
        w = min(W, key=lambda w: zkm(w, d)); dedges[(w, d)] = cap()
for _ in range(4):
    a, b = rnd.sample(H, 2); dedges[(a, b)] = cap()
for _ in range(5):
    a, b = rnd.sample(D, 2)
    if zkm(a, b) < 6: dedges[(a, b)] = cap()
mank = zid["Mankhurd"]
for k in [k for k in dedges if k[1] == mank]: del dedges[k]
dedges[(min(H, key=lambda h: zkm(h, mank)), mank)] = 0          # only inbound edge has capacity 0
for k in rnd.sample([k for k in dedges if k[1] != mank], 4): dedges[k] = 0
dist_rows = [[i + 1, a, b, c] for i, ((a, b), c) in enumerate(sorted(dedges.items()))]

# ---- centres, hospitals ------------------------------------------------------------------------------
spread = ["Colaba", "Worli", "Dadar", "Bandra", "Kurla", "Chembur", "Andheri", "Powai", "Goregaon", "Borivali"]
ctype = ["FIRE_STATION", "POLICE", "HOSPITAL_HUB", "RESPONSE"]
centre_rows = []
for i, n in enumerate(spread):
    centre_rows.append([i + 1, f"{n} Emergency Centre", zid[n], round(rnd.uniform(3.5, 5.0), 1), round(rnd.uniform(40, 90), 1), "EXISTING", ctype[i % 4]])
prop = ["Mulund", "Ghatkopar", "Malad", "Mahim", "Sion", "Kandivali", "Mankhurd", "Jogeshwari"]
for j, n in enumerate(prop):
    centre_rows.append([11 + j, f"{n} Proposed Centre", zid[n], round(rnd.uniform(4.0, 6.0), 1), round(rnd.uniform(30, 80), 1), "PROPOSED", ctype[j % 4]])
hosp = [("KEM Hospital","Parel"),("Lilavati Hospital","Bandra"),("Sion Hospital","Sion"),("Nair Hospital","Mumbai Central"),("JJ Hospital","Byculla"),
 ("Cooper Hospital","Vile Parle"),("Kokilaben Hospital","Andheri"),("Rajawadi Hospital","Ghatkopar"),("Hinduja Hospital","Mahim"),
 ("Bhabha Hospital","Kurla"),("Fortis Hospital","Mulund"),("Shatabdi Hospital","Govandi"),("Bhagwati Hospital","Borivali"),("Sushrusha Hospital","Dadar"),("Holy Family Hospital","Bandra")]
hosp_rows = []
for i, (n, z) in enumerate(hosp):
    capb = rnd.randrange(120, 1200, 10); hosp_rows.append([i + 1, n, zid[z], capb, int(capb * rnd.uniform(0.1, 0.45))])

# ---- types, compat, resources -----------------------------------------------------------------------------
CATS = [("FIRE","Fire"),("MEDICAL","Medical emergency"),("ROAD_ACCIDENT","Road accident"),("FLOOD","Flooding"),
        ("BUILDING_COLLAPSE","Building collapse"),("UTILITY_FAILURE","Utility failure"),("PUBLIC_SAFETY","Public safety"),("OTHER","Other emergency")]
COMPAT = {"FIRE":["FIRE","RESCUE","HAZMAT"],"MEDICAL":["MEDICAL"],"ROAD_ACCIDENT":["MEDICAL","POLICE","RESCUE"],"FLOOD":["RESCUE","ENGINEERING"],
          "BUILDING_COLLAPSE":["RESCUE","MEDICAL","ENGINEERING"],"UTILITY_FAILURE":["ENGINEERING","HAZMAT"],"PUBLIC_SAFETY":["POLICE"],"OTHER":["RESCUE","POLICE","MEDICAL"]}
type_rows = [[i + 1, c, n] for i, (c, n) in enumerate(CATS)]; tid = {c: i + 1 for i, (c, _) in enumerate(CATS)}
compat_rows, k = [], 1
for c, _ in CATS:
    for t in COMPAT[c]: compat_rows.append([k, tid[c], t]); k += 1
RES = [("Drinking Water","L"),("Food Packs","packs"),("Blankets","units"),("Medical Kits","kits"),("Oxygen Cylinders","cylinders"),
       ("Rescue Boats","boats"),("Generators","units"),("Stretchers","units"),("Fuel","L"),("Tents","units")]
res_rows = [[i + 1, n, u] for i, (n, u) in enumerate(RES)]
SCALE = {1:3000,2:800,3:500,4:150,5:60,6:6,7:12,8:40,9:1500,10:80}
inv_rows, k = [], 1
for z in ids:
    for r in range(1, 11):
        base = SCALE[r]
        mult = 1.0 if role[z] == "WAREHOUSE" else 0.5 if role[z] == "HUB" else 0.08
        capq = max(1, int(base * mult * rnd.uniform(0.9, 1.4)))
        qty = int(capq * rnd.uniform(0.35, 0.95))
        inv_rows.append([k, z, r, qty, capq]); k += 1

# ---- teams ------------------------------------------------------------------------------------------------
TT = ["FIRE","MEDICAL","RESCUE","POLICE","HAZMAT","ENGINEERING"]
team_rows = []
centre_zone = {r[0]: r[2] for r in centre_rows}
existing = [r[0] for r in centre_rows if r[5] == "EXISTING"]
for i in range(40):
    t = TT[i % 6] if i < 36 else TT[(i - 36) % 3]
    cid = existing[i % len(existing)]
    nm = f"{t.title()} Unit {t[0]}-{i // 6 + 1:02d}" if i < 36 else f"{t.title()} Unit {t[0]}-X{i - 35}"
    team_rows.append([i + 1, nm, t, centre_zone[cid], cid, "AVAILABLE", ""])

# ---- incidents --------------------------------------------------------------------------------------------
CATW = [("FIRE",14),("MEDICAL",28),("ROAD_ACCIDENT",20),("FLOOD",12),("BUILDING_COLLAPSE",4),("UTILITY_FAILURE",10),("PUBLIC_SAFETY",8),("OTHER",4)]
SEVW = {"FIRE":[1,2,3,3,4,5],"MEDICAL":[1,2,2,3,3,4,5],"ROAD_ACCIDENT":[1,2,2,3,3,4],"FLOOD":[2,3,3,4,4,5],"BUILDING_COLLAPSE":[3,4,5,5],
        "UTILITY_FAILURE":[1,2,2,3,3,4],"PUBLIC_SAFETY":[1,2,2,3,4],"OTHER":[1,1,2,2,3]}
PLACE = ["near {z} station","on the main road in {z}","at a residential block in {z}","in the {z} market area","near the {z} flyover","at an industrial unit in {z}"]
DESC = {"FIRE":["Fire reported {p} with heavy smoke","Short-circuit fire {p}; residents evacuating","Warehouse fire {p} spreading to adjacent shops"],
 "MEDICAL":["Medical emergency {p}: patient with chest pain","Multiple people unwell {p} after food poisoning","Elderly resident unconscious {p}, ambulance needed"],
 "ROAD_ACCIDENT":["Road accident {p}: two vehicles collided","Bus and truck collision {p} with injuries","Motorcycle accident {p}, traffic blocked"],
 "FLOOD":["Severe water logging {p}; vehicles stranded","Flooding {p} entering ground-floor homes","Water logging {p}, drain overflow and stranded commuters"],
 "BUILDING_COLLAPSE":["Building collapse {p}; people trapped under debris","Partial building collapse {p} after heavy rain"],
 "UTILITY_FAILURE":["Power outage {p} affecting hundreds of homes","Gas leak {p}; area cordoned off","Water main burst {p}, supply disrupted"],
 "PUBLIC_SAFETY":["Crowd surge {p}; police assistance requested","Public safety incident {p}: unattended suspicious bag"],
 "OTHER":["Tree fall {p} blocking road","Stranded lift passengers {p}"]}
REQ = {"FIRE":[4,6,9,2],"MEDICAL":[4,5,8,1],"ROAD_ACCIDENT":[4,8,9],"FLOOD":[1,2,3,6,10],"BUILDING_COLLAPSE":[4,8,6,7,3],
       "UTILITY_FAILURE":[7,9,1],"PUBLIC_SAFETY":[8,3],"OTHER":[3,2,9]}
LOGS = ["Control room notified of {c} in {z}","Team dispatch requested for {c}","Local police informed; area secured","Residents report water logging near the site",
 "Fire brigade on standby; gas leak ruled out","Hospital alerted: expect {n} patients","Traffic diverted around {z}","Power outage reported in adjacent lanes",
 "Building collapse risk assessed by engineers","Situation update: conditions stable","Situation update: conditions worsening","Evacuation of nearby buildings in progress",
 "Resources requested: {r}","Water logging receding after pumps deployed","Gas leak contained by hazmat team","Incident closed after field verification"]
cats, w = zip(*CATW)
popw = [r[6] for r in zone_rows]
inc_rows, req_rows, hist_rows, log_rows = [], [], [], []
n_active = 0
ACTIVE = ["REPORTED", "ASSIGNED", "IN_PROGRESS"]; DONE = ["RESOLVED", "RESOLVED", "RESOLVED", "CANCELLED", "ARCHIVED"]
rk = hk = lk = 1
times = sorted(BASE - timedelta(minutes=rnd.randrange(0, 30 * 24 * 60)) for _ in range(1000))
for i in range(1000):
    cat = rnd.choices(cats, w)[0]
    z = rnd.choices(zone_rows, popw)[0]
    sev = rnd.choice(SEVW[cat]); urg = min(5, max(1, sev + rnd.choice([-1, 0, 0, 1])))
    aff = min(8000, int(rnd.paretovariate(1.4) * 3 * sev)) if cat != "MEDICAL" else min(60, 1 + int(rnd.expovariate(0.25)))
    recent = times[i] > BASE - timedelta(days=5)
    if recent and rnd.random() < 0.75: st = rnd.choices(ACTIVE, [6, 2, 2])[0]
    else: st = rnd.choice(DONE)
    if st in ACTIVE: n_active += 1
    place = rnd.choice(PLACE).format(z=z[1])
    desc = rnd.choice(DESC[cat]).format(p=place)
    t0 = times[i]
    inc_rows.append([i + 1, f"INC-{i + 1:04d}", tid[cat], z[0], desc, sev, urg, aff, st, iso(t0), iso(t0), iso(t0 + timedelta(minutes=5))])
    for r in rnd.sample(REQ[cat], rnd.randint(1, min(3, len(REQ[cat])))):
        q = max(1, int((1 + aff / 40) * rnd.uniform(0.5, 3) * (SCALE[r] / 400 + 1)))
        req_rows.append([rk, i + 1, r, q]); rk += 1
    seq = ["REPORTED"]
    if st in ("ASSIGNED", "IN_PROGRESS", "RESOLVED"): seq.append("ASSIGNED")
    if st in ("IN_PROGRESS", "RESOLVED"): seq.append("IN_PROGRESS")
    if st in ("RESOLVED", "CANCELLED", "ARCHIVED") and seq[-1] != st: seq.append(st)
    if st == "ARCHIVED" and seq[-1] != "ARCHIVED": seq.append("ARCHIVED")
    prev, tt = None, t0
    for s in seq:
        hist_rows.append([hk, i + 1, prev or "", s, iso(tt), "Initial report" if prev is None else "Status updated"]); hk += 1
        prev, tt = s, tt + timedelta(minutes=rnd.randint(5, 90))
    for _ in range(rnd.choice([2, 3, 3, 4])):
        txt = rnd.choice(LOGS).format(c=dict(CATS)[cat].lower(), z=z[1], n=rnd.randint(2, 12), r=dict(RES)[1] if False else RES[rnd.choice(REQ[cat]) - 1][0])
        if rnd.random() < 0.2: txt = txt.upper() if rnd.random() < 0.5 else txt + " - " + txt.split(";")[0]
        log_rows.append([lk, i + 1, txt, iso(t0 + timedelta(minutes=rnd.randint(1, 180)))]); lk += 1

# ---- assignments + BUSY teams (consistent) ----------------------------------------------------------------
compat_by_cat = {tid[c]: COMPAT[c] for c, _ in CATS}
assign_rows, busy = [], set()
ak = 1
active_ass = [r for r in inc_rows if r[8] in ("ASSIGNED", "IN_PROGRESS")]
for r in active_ass:
    need = 2 if r[5] >= 4 else 1
    for _ in range(need):
        free = [t for t in team_rows if t[0] not in busy and t[2] in compat_by_cat[r[2]]]
        if not free or len(busy) >= 14: break
        t = rnd.choice(free); busy.add(t[0]); t[5] = "BUSY"; t[6] = r[0]
        assign_rows.append([ak, r[0], t[0], "ACTIVE", iso(datetime.strptime(r[9], "%Y-%m-%dT%H:%M:%S") + timedelta(minutes=12))]); ak += 1
# incidents flagged ASSIGNED/IN_PROGRESS but left without a team fall back to REPORTED (keeps data consistent)
staffed = {a[1] for a in assign_rows}
for r in inc_rows:
    if r[8] in ("ASSIGNED", "IN_PROGRESS") and r[0] not in staffed:
        r[8] = "REPORTED"
        hist_rows[:] = [h for h in hist_rows if not (h[1] == r[0] and h[3] in ("ASSIGNED", "IN_PROGRESS"))]
for t in rnd.sample([t for t in team_rows if t[5] == "AVAILABLE"], 3): t[5] = "OFF_DUTY"
for r in inc_rows:
    if r[8] == "RESOLVED" and rnd.random() < 0.05 and r[0] not in staffed:
        t = rnd.choice(team_rows)
        assign_rows.append([ak, r[0], t[0], "COMPLETED", iso(datetime.strptime(r[9], "%Y-%m-%dT%H:%M:%S") + timedelta(minutes=15))]); ak += 1

# ---- relief destinations ------------------------------------------------------------------------------------
rd_zones = rnd.sample(D, 25)
rd_rows = [[i + 1, z, rnd.randrange(100, 1200, 50)] for i, z in enumerate(sorted(rd_zones))]

DATA = {"city_zones": zone_rows, "road_connections": road_rows, "distribution_edges": dist_rows, "emergency_centres": centre_rows,
 "hospitals": hosp_rows, "emergency_types": type_rows, "emergency_type_team_compat": compat_rows, "resource_types": res_rows,
 "resource_inventory": inv_rows, "response_teams": team_rows, "emergency_incidents": inc_rows, "incident_resource_requirements": req_rows,
 "incident_status_history": hist_rows, "incident_logs": log_rows, "incident_assignments": assign_rows, "relief_destinations": rd_rows}

def main():
    os.makedirs(OUT, exist_ok=True); os.makedirs(SEED_DIR, exist_ok=True)
    for name, cls, cols, _ in TABLES:
        if name in NO_SEED: continue
        header = [c for c, _, _ in cols]
        rows = DATA[name]
        for row in rows: assert len(row) == len(header), (name, row)
        for d in (OUT, SEED_DIR):
            with open(os.path.join(d, name + ".csv"), "w", newline="", encoding="utf-8") as f:
                wr = csv.writer(f, lineterminator="\n"); wr.writerow(header); wr.writerows(rows)
    # ---- verification
    print("zones", len(zone_rows), "| roads", len(road_rows), "| dist edges", len(dist_rows), "| zero-cap edges", sum(1 for r in dist_rows if r[3] == 0))
    print("centres", len(centre_rows), "| hospitals", len(hosp_rows), "| teams", len(team_rows), "| incidents", len(inc_rows), "| requirements", len(req_rows))
    print("history", len(hist_rows), "| logs", len(log_rows), "| assignments", len(assign_rows), "| inventory", len(inv_rows), "| relief dest", len(rd_rows))
    print("road graph connected:", connected(roads), "| degrees min/max", min(deg.values()), max(deg.values()), "| zones with exactly 2 roads:", [Z[i][1] for i in ids if deg[i] == 2])
    print("active incidents:", sum(1 for r in inc_rows if r[8] in ACTIVE), "| statuses:", {s: sum(1 for r in inc_rows if r[8] == s) for s in ACTIVE + ["RESOLVED","CANCELLED","ARCHIVED"]})
    print("BUSY teams:", sum(1 for t in team_rows if t[5] == "BUSY"), "| OFF_DUTY:", sum(1 for t in team_rows if t[5] == "OFF_DUTY"))
    inbound = [k for k in dedges if k[1] == mank]
    print("Mankhurd inbound edges:", inbound, "capacity", [dedges[k] for k in inbound])
    # FK sanity
    zset = set(ids); assert all(r[1] in zset and r[2] in zset for r in road_rows)
    assert all(r[1] in zset and r[2] in zset for r in dist_rows)
    iset = {r[0] for r in inc_rows}; assert all(r[1] in iset for r in req_rows + hist_rows + log_rows + assign_rows)
    assert len({r[1] for r in inc_rows}) == 1000
    for t in team_rows:
        if t[5] == "BUSY": assert any(a[2] == t[0] and a[3] == "ACTIVE" for a in assign_rows)
    print("FK and consistency checks passed")

if __name__ == "__main__":
    main()
