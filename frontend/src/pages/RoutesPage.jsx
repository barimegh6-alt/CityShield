import { useMemo, useState } from 'react';
import { Route as RouteIcon, Play } from 'lucide-react';
import { Button, Card, Table, StatCard, Badge, AlgorithmInfo, PageHeader, LoadingBlock, ErrorBanner, EmptyState, Select, fmt } from '../components/ui.jsx';
import CityMap, { ZoneDot, LinkLine, FitBounds } from '../components/CityMap.jsx';
import { useApi, useAction } from '../hooks/useApi.js';
import { get, post } from '../api/client.js';

export default function RoutesPage() {
  const centres = useApi(() => get('/centres'), []);
  const dests = useApi(() => get('/relief-destinations'), []);
  const [depot, setDepot] = useState('');
  const [picked, setPicked] = useState([]);
  const [hk, setHk] = useState(true);
  const [localErr, setLocalErr] = useState(null);
  const run = useAction((body) => post('/routes/tsp', body));

  const list = dests.data || [];
  const existing = (centres.data || []).filter((c) => c.status === 'EXISTING');
  const toggle = (id) => setPicked((p) => (p.includes(id) ? p.filter((v) => v !== id) : [...p, id]));
  const r = run.data;

  const submit = () => {
    if (!depot) { setLocalErr('Choose a depot (emergency centre).'); return; }
    if (picked.length < 1) { setLocalErr('Select at least one destination.'); return; }
    if (picked.length > 30) { setLocalErr('At most 30 destinations are allowed.'); return; }
    setLocalErr(null);
    run.run({ depotCentreId: Number(depot), destinationIds: picked, compareHeldKarp: hk });
  };

  const path = useMemo(() => (r?.route || []).map((s) => [s.latitude, s.longitude]), [r]);
  const stops = (r?.route || []).filter((s, i, a) => i > 0 && i < a.length - 1);

  return (
    <div className="space-y-4">
      <PageHeader title="Relief Route Optimisation" subtitle="Shortest delivery tour from a depot through the chosen destinations. Metric TSP 2-approximation: minimum spanning tree + preorder walk. Distances are a Haversine × 1.3 simulation estimate, not road navigation." />
      {(centres.loading || dests.loading) && !dests.data ? <LoadingBlock /> : (
        <>
          <ErrorBanner error={centres.error || dests.error} onRetry={() => { centres.reload(); dests.reload(); }} />
          <Card title="Plan a route" actions={<Button variant="primary" loading={run.loading} onClick={submit}><Play size={14} /> Compute route</Button>}>
            <div className="grid gap-4 lg:grid-cols-3">
              <div className="space-y-3">
                <label className="block">
                  <span className="label-caps mb-1 block">Depot (existing centre)</span>
                  <Select aria-label="Depot" value={depot} onChange={(e) => setDepot(e.target.value)}
                    options={[{ value: '', label: 'Select depot…' }, ...existing.map((c) => ({ value: String(c.id), label: `${c.name} — ${c.zoneName}` }))]} />
                </label>
                <label className="flex items-center gap-2 text-sm text-slate-300"><input type="checkbox" checked={hk} onChange={(e) => setHk(e.target.checked)} /> Compare with exact Held-Karp (only when depot + stops ≤ 12)</label>
                <div className="text-xs text-slate-400">{picked.length} destination(s) selected</div>
                <div className="flex gap-2">
                  <Button onClick={() => setPicked(list.slice(0, 8).map((d) => d.id))}>First 8</Button>
                  <Button onClick={() => setPicked([])}>Clear</Button>
                </div>
              </div>
              <div className="lg:col-span-2">
                <span className="label-caps mb-1 block">Relief destinations</span>
                {list.length === 0 ? <EmptyState title="No relief destinations" /> : (
                  <div className="grid max-h-56 gap-1 overflow-y-auto sm:grid-cols-2">
                    {list.map((d) => (
                      <label key={d.id} className="flex items-center gap-2 rounded border border-line px-2 py-1 text-sm text-slate-200">
                        <input type="checkbox" aria-label={`Destination ${d.name}`} checked={picked.includes(d.id)} onChange={() => toggle(d.id)} />
                        <span className="flex-1 truncate">{d.name}</span><span className="text-xs text-slate-400">{fmt.num(d.demandUnits)} u</span>
                      </label>))}
                  </div>)}
              </div>
            </div>
            {localErr && <p role="alert" className="mt-3 text-sm text-red-300">{localErr}</p>}
            <ErrorBanner error={run.error} />
          </Card>

          {!r && !run.loading && <EmptyState title="No route computed yet" hint="Pick a depot and destinations, then press Compute route." />}
          {run.loading && <LoadingBlock label="Computing route…" />}
          {r && (
            <>
              <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
                <StatCard label="2-approx tour" value={fmt.km(r.totalKm)} tone="orange" icon={RouteIcon} />
                <StatCard label="MST weight (lower bound)" value={fmt.km(r.mstWeightKm)} />
                <StatCard label="Nearest neighbour" value={fmt.km(r.nearestNeighbour?.totalKm)} />
                <StatCard label="Exact (Held-Karp)" value={r.heldKarp?.available ? fmt.km(r.heldKarp.totalKm) : 'n/a'} hint={r.heldKarp?.available ? (r.ratioToOptimal != null ? `approx / optimal = ${Number(r.ratioToOptimal).toFixed(3)}` : '') : r.heldKarp?.reason} tone="green" />
              </div>
              <AlgorithmInfo name="Metric TSP 2-approximation (Prim MST + preorder shortcut)" complexity="O(n²) Prim on the complete graph; tour length ≤ 2 × optimal when the triangle inequality holds"
                notes={r.guarantee} execution={r.execution} />
              <Card title="Route map" subtitle={r.distanceModel}>
                <CityMap height={420}>
                  <FitBounds points={path} />
                  <LinkLine positions={path} color="#f97316" weight={3} />
                  {(r.mstEdges || []).length > 0 && null}
                  <ZoneDot position={[r.depot.latitude, r.depot.longitude]} color="#22c55e" radius={11} label={`Depot: ${r.depot.name}`} />
                  {stops.map((s) => <ZoneDot key={s.id} position={[s.latitude, s.longitude]} color="#38bdf8" label={`${s.seq}. ${s.name}`} />)}
                </CityMap>
              </Card>
              <div className="grid gap-4 xl:grid-cols-2">
                <Card title="Ordered stops">
                  <Table dense rows={r.route} rowKey="seq" columns={[{ key: 'seq', header: '#', align: 'right' }, { key: 'name', header: 'Stop' }, { key: 'legKm', header: 'Leg', align: 'right', render: (s) => fmt.km(s.legKm) }]} />
                </Card>
                <Card title="Minimum spanning tree edges" subtitle={`Preorder walk: ${(r.preorder || []).join(' → ')}`}>
                  <div className="max-h-72 overflow-y-auto"><Table dense rows={r.mstEdges} rowKey={(e) => `${e.from}-${e.to}`} columns={[{ key: 'from', header: 'From' }, { key: 'to', header: 'To' }, { key: 'km', header: 'Distance', align: 'right', render: (e) => fmt.km(e.km) }]} /></div>
                  <div className="mt-3 text-xs text-slate-400">Nearest neighbour: {(r.nearestNeighbour?.route || []).join(' → ')}</div>
                  {r.heldKarp?.available && <div className="mt-1 text-xs text-slate-400">Held-Karp optimum: {r.heldKarp.route.join(' → ')} <Badge tone="green">exact</Badge></div>}
                </Card>
              </div>
            </>
          )}
        </>
      )}
    </div>
  );
}
