import { useMemo, useState } from 'react';
import { RefreshCw, Play, Ban, Unlock, RotateCcw } from 'lucide-react';
import { Button, Card, Table, StatCard, Badge, StatusBadge, AlgorithmInfo, PageHeader, LoadingBlock, ErrorBanner, EmptyState, Input, Select, fmt } from '../components/ui.jsx';
import CityMap, { ZoneDot, LinkLine, FitBounds } from '../components/CityMap.jsx';
import { COLORS } from '../components/charts.jsx';
import { useApi, useAction } from '../hooks/useApi.js';
import { get, post, patch } from '../api/client.js';

export default function NetworkPage() {
  const g = useApi(() => get('/network/graph'), []);
  const [sel, setSel] = useState([]);
  const [q, setQ] = useState('');
  const [statusF, setStatusF] = useState('');
  const [includeDb, setIncludeDb] = useState(true);
  const conn = useAction((body) => post('/network/connectivity', body));
  const setStatus = useAction((id, status) => patch(`/roads/${id}/status`, { status }));

  const zones = g.data?.zones || []; const roads = g.data?.roads || [];
  const zById = useMemo(() => new Map(zones.map((z) => [z.id, z])), [zones]);
  const r = conn.data;
  const compOf = (zid) => r?.zoneComponent?.[String(zid)];
  const critical = new Set((r?.criticalRoads || []).map((x) => x.id));
  const isolated = new Set((r?.isolatedZones || []).map((z) => z.id));
  const largest = (r?.components || []).find((c) => c.isLargest)?.id;
  const rows = roads.filter((x) => (!statusF || x.status === statusF) && (!q || `${x.zoneAName} ${x.zoneBName}`.toLowerCase().includes(q.toLowerCase())));
  const toggleSel = (id) => setSel((s) => (s.includes(id) ? s.filter((v) => v !== id) : [...s, id]));

  const runWhatIf = () => conn.run({ blockedRoadIds: sel, includeBlockedInDatabase: includeDb });
  const flip = async (road) => {
    const res = await setStatus.run(road.id, road.status === 'BLOCKED' ? 'OPEN' : 'BLOCKED');
    if (res) { g.reload(); if (conn.data) runWhatIf(); }
  };
  const zoneColor = (z) => { if (!r) return '#38bdf8'; if (isolated.has(z.id)) return '#ef4444'; const c = compOf(z.id); return c == null ? '#94a3b8' : COLORS[Number(c) % COLORS.length]; };
  const blockedNow = roads.filter((x) => x.status === 'BLOCKED').length;

  return (
    <div className="space-y-4">
      <PageHeader title="City Network & Connectivity" subtitle="Zones and roads on the map. Select roads to block in a what-if analysis (nothing is saved); the Disjoint Set Union finds the resulting components."
        actions={<Button onClick={() => { g.reload(); }}><RefreshCw size={14} /> Refresh</Button>} />
      {g.loading && !g.data ? <LoadingBlock label="Loading city graph…" /> : g.error && !g.data ? <ErrorBanner error={g.error} onRetry={g.reload} /> : zones.length === 0 ? <EmptyState title="No zones in the network" /> : (
        <>
          <ErrorBanner error={g.error} onRetry={g.reload} />
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <StatCard label="Zones" value={fmt.num(zones.length)} />
            <StatCard label="Roads" value={fmt.num(roads.length)} />
            <StatCard label="Blocked in database" value={fmt.num(blockedNow)} tone={blockedNow ? 'orange' : 'green'} />
            <StatCard label="What-if selection" value={fmt.num(sel.length)} tone={sel.length ? 'red' : 'default'} hint="roads to block" />
          </div>

          <Card title="Map" subtitle={r ? 'Zones coloured by connected component; red = isolated; dashed red = blocked; orange = critical road.' : 'Run the what-if analysis to colour zones by component.'}>
            <CityMap height={460}>
              <FitBounds points={zones.map((z) => [z.latitude, z.longitude])} />
              {roads.map((x) => {
                const a = zById.get(x.zoneA); const b = zById.get(x.zoneB);
                if (!a || !b) return null;
                const picked = sel.includes(x.id);
                const blocked = x.status === 'BLOCKED' || picked;
                const color = blocked ? '#ef4444' : critical.has(x.id) ? '#f97316' : '#64748b';
                return <LinkLine key={x.id} positions={[[a.latitude, a.longitude], [b.latitude, b.longitude]]} color={color} weight={critical.has(x.id) ? 4 : 2} dashArray={blocked ? '6 6' : undefined} label={`${a.name} – ${b.name} (${fmt.km(x.distanceKm)}) ${picked ? 'SELECTED' : x.status}`} />;
              })}
              {zones.map((z) => <ZoneDot key={z.id} position={[z.latitude, z.longitude]} color={zoneColor(z)} radius={isolated.has(z.id) ? 9 : 7} label={`${z.name}${compOf(z.id) != null ? ` — component ${compOf(z.id)}` : ''}`} />)}
            </CityMap>
          </Card>

          <Card title="What-if connectivity (DSU)" subtitle="POST /network/connectivity: does not modify any road."
            actions={<>
              <label className="flex items-center gap-1.5 text-xs text-slate-300"><input type="checkbox" checked={includeDb} onChange={(e) => setIncludeDb(e.target.checked)} /> Include roads already blocked in database</label>
              <Button onClick={() => { setSel([]); conn.reset(); }}><RotateCcw size={13} /> Clear</Button>
              <Button variant="primary" loading={conn.loading} onClick={runWhatIf}><Play size={14} /> Analyse connectivity</Button></>}>
            <ErrorBanner error={conn.error} />
            {!r && !conn.loading && !conn.error && <EmptyState title="No analysis yet" hint="Tick roads in the table below to block them virtually, then press Analyse connectivity (an empty selection analyses the current network)." />}
            {r && (
              <div className="space-y-3">
                <div className="grid grid-cols-2 gap-3 md:grid-cols-4 lg:grid-cols-6">
                  <StatCard label="Components" value={fmt.num(r.componentCount)} tone={r.componentCount > 1 ? 'orange' : 'green'} />
                  <StatCard label="Open roads" value={`${fmt.num(r.openRoadCount)} / ${fmt.num(r.roadCount)}`} />
                  <StatCard label="Blocked in analysis" value={fmt.num((r.blockedRoadIds || []).length)} tone="red" />
                  <StatCard label="Isolated zones" value={fmt.num((r.isolatedZones || []).length)} tone={(r.isolatedZones || []).length ? 'red' : 'green'} />
                  <StatCard label="DSU unions" value={fmt.num(r.dsuStats?.unions)} />
                  <StatCard label="DSU finds" value={fmt.num(r.dsuStats?.finds)} />
                </div>
                <AlgorithmInfo name="Disjoint Set Union (path compression + union by rank)" complexity="Near O(alpha(n)) per operation; building components O((V + E) alpha(V))" execution={r.execution} />
                <div className="grid gap-4 xl:grid-cols-2">
                  <div><h4 className="mb-1 text-sm font-semibold text-slate-100">Components</h4>
                    <Table dense rows={r.components} rowKey="id" empty="No components" columns={[
                      { key: 'c', header: '', render: (c) => <span className="inline-block h-3 w-3 rounded-full" style={{ background: COLORS[Number(c.id) % COLORS.length] }} /> },
                      { key: 'id', header: 'ID' }, { key: 'size', header: 'Zones', align: 'right' },
                      { key: 'l', header: '', render: (c) => (c.id === largest ? <Badge tone="green">Largest</Badge> : c.size === 1 ? <Badge tone="red">Isolated</Badge> : null) },
                      { key: 'n', header: 'Members', render: (c) => <span className="text-xs text-slate-400">{(c.zoneNames || []).slice(0, 8).join(', ')}{(c.zoneNames || []).length > 8 ? ` +${c.zoneNames.length - 8}` : ''}</span> }]} /></div>
                  <div className="space-y-3">
                    <div><h4 className="mb-1 text-sm font-semibold text-slate-100">Isolated zones</h4>
                      <Table dense rows={r.isolatedZones} rowKey="id" empty="No isolated zones" columns={[{ key: 'name', header: 'Zone' }, { key: 'population', header: 'Population', align: 'right', render: (z) => fmt.num(z.population) }]} /></div>
                    <div><h4 className="mb-1 text-sm font-semibold text-slate-100">Critical roads (blocking would split a component)</h4>
                      <div className="max-h-48 overflow-y-auto"><Table dense rows={r.criticalRoads} rowKey="id" empty="No critical roads" columns={[{ key: 'a', header: 'Road', render: (x) => `${x.zoneAName} – ${x.zoneBName}` }, { key: 'd', header: 'Distance', align: 'right', render: (x) => fmt.km(x.distanceKm) }]} /></div></div>
                  </div>
                </div>
              </div>
            )}
          </Card>

          <Card title="Roads" subtitle="Tick to include in the what-if; use Block/Unblock to change the saved road status."
            actions={<>
              <Input aria-label="Filter roads" placeholder="Search zone" className="!w-40" value={q} onChange={(e) => setQ(e.target.value)} />
              <Select aria-label="Road status filter" className="!w-32" value={statusF} onChange={(e) => setStatusF(e.target.value)} options={[{ value: '', label: 'All' }, { value: 'OPEN', label: 'Open' }, { value: 'BLOCKED', label: 'Blocked' }]} /></>}>
            <ErrorBanner error={setStatus.error} />
            <div className="max-h-96 overflow-y-auto"><Table dense rows={rows} rowKey="id" empty="No roads match the filter" columns={[
              { key: 'sel', header: 'What-if', render: (x) => <input type="checkbox" aria-label={`Block ${x.zoneAName} to ${x.zoneBName} in what-if`} checked={sel.includes(x.id)} onChange={() => toggleSel(x.id)} /> },
              { key: 'a', header: 'Road', render: (x) => `${x.zoneAName} – ${x.zoneBName}` }, { key: 'd', header: 'Distance', align: 'right', render: (x) => fmt.km(x.distanceKm) },
              { key: 's', header: 'Status', render: (x) => <StatusBadge status={x.status} /> },
              { key: 'b', header: '', render: (x) => <Button loading={setStatus.loading} onClick={() => flip(x)}>{x.status === 'BLOCKED' ? <><Unlock size={13} /> Unblock</> : <><Ban size={13} /> Block</>}</Button> }]} /></div>
          </Card>
        </>
      )}
    </div>
  );
}
