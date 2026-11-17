import { useEffect, useState } from 'react';
import { RefreshCw, Play, CheckCheck, Save, AlertTriangle, CheckCircle2, SlidersHorizontal } from 'lucide-react';
import { Button, Card, Field, Input, Select, Table, StatCard, Badge, AlgorithmInfo, PageHeader, LoadingBlock, ErrorBanner, EmptyState, fmt } from '../components/ui.jsx';
import { ConfirmDialog } from '../components/Modal.jsx';
import CityMap, { ZoneDot, LinkLine, FitBounds, Tooltip } from '../components/CityMap.jsx';
import RoadPicker from '../components/RoadPicker.jsx';
import RangeQueryPanel from '../components/RangeQueryPanel.jsx';
import { useApi, useAction } from '../hooks/useApi.js';
import { get, post, put } from '../api/client.js';
import { useResourceTypes, useRoads, pretty } from '../hooks/useRef.js';

const ROLE_COLOR = { WAREHOUSE: '#22c55e', HUB: '#38bdf8', DEMAND: '#f97316' };

function AdjustPanel({ typeId, inventory, onDone }) {
  const [f, setF] = useState({ zoneId: '', delta: '', reason: '' });
  const [err, setErr] = useState({});
  const adj = useAction((body) => post('/inventory/adjust', body));
  const set = (k, v) => setF((p) => ({ ...p, [k]: v }));
  const submit = async (e) => {
    e.preventDefault();
    const er = {};
    if (!f.zoneId) er.zoneId = 'Choose a zone.';
    if (f.delta === '' || !Number.isInteger(Number(f.delta)) || Number(f.delta) === 0) er.delta = 'Delta must be a non-zero whole number.';
    if (!f.reason.trim()) er.reason = 'Reason is required.';
    setErr(er);
    if (Object.keys(er).length) return;
    const r = await adj.run({ zoneId: Number(f.zoneId), resourceTypeId: Number(typeId), delta: Number(f.delta), reason: f.reason.trim() });
    if (r) onDone();
  };
  const r = adj.data;
  return (
    <Card title="Adjust stock (point update)" subtitle="Updates MySQL and the segment tree. Results below 0 or above capacity are rejected by the server.">
      <form className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4" onSubmit={submit} noValidate>
        <Field label="Zone" error={err.zoneId}><Select aria-label="Adjust zone" value={f.zoneId} onChange={(e) => set('zoneId', e.target.value)}
          options={[{ value: '', label: 'Select zone…' }, ...(inventory || []).map((i) => ({ value: String(i.zoneId), label: `${i.zoneName} (${i.quantity}/${i.capacity})` }))]} /></Field>
        <Field label="Delta (+/-)" error={err.delta}><Input aria-label="Delta" type="number" value={f.delta} onChange={(e) => set('delta', e.target.value)} /></Field>
        <Field label="Reason" error={err.reason}><Input aria-label="Reason" value={f.reason} onChange={(e) => set('reason', e.target.value)} /></Field>
        <div className="flex items-end"><Button variant="primary" type="submit" loading={adj.loading} disabled={!typeId}><SlidersHorizontal size={14} /> Apply adjustment</Button></div>
      </form>
      <div className="mt-3 space-y-2">
        <ErrorBanner error={adj.error} />
        {r && (
          <div className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-3 text-sm text-emerald-100">
            Stock changed from <b className="tnum">{fmt.num(r.before)}</b> to <b className="tnum">{fmt.num(r.after)}</b> (capacity {fmt.num(r.capacity)}).
          </div>
        )}
      </div>
    </Card>
  );
}

function EdgeEditor({ edges, nameOf, onSaved }) {
  const [vals, setVals] = useState({});
  const [q, setQ] = useState('');
  const [msg, setMsg] = useState(null);
  const save = useAction((id, capacity) => put(`/distribution/edges/${id}`, { capacity }));
  const [bad, setBad] = useState({});
  const rows = (edges || []).filter((e) => !q || `${nameOf(e.fromZoneId)} ${nameOf(e.toZoneId)}`.toLowerCase().includes(q.toLowerCase()));
  const doSave = async (e) => {
    const v = vals[e.id] ?? String(e.capacity);
    if (v === '' || !Number.isFinite(Number(v)) || Number(v) < 0) { setBad((b) => ({ ...b, [e.id]: 'Capacity must be 0 or more' })); return; }
    setBad((b) => ({ ...b, [e.id]: null }));
    const r = await save.run(e.id, Number(v));
    if (r) { setMsg(`Edge ${nameOf(e.fromZoneId)} to ${nameOf(e.toZoneId)} capacity is now ${v}.`); setVals((x) => { const n = { ...x }; delete n[e.id]; return n; }); onSaved(); }
  };
  return (
    <Card title="Distribution edge capacities" subtitle="Edit a capacity (0 closes the edge), then preview again."
      actions={<Input aria-label="Filter edges" placeholder="Filter by zone" className="!w-40" value={q} onChange={(e) => setQ(e.target.value)} />}>
      <ErrorBanner error={save.error} />
      {msg && <div className="mb-2 text-xs text-emerald-300">{msg}</div>}
      <div className="max-h-72 overflow-y-auto">
        <Table dense rows={rows} rowKey="id" empty="No edges" columns={[
          { key: 'f', header: 'From', render: (e) => nameOf(e.fromZoneId) }, { key: 't', header: 'To', render: (e) => nameOf(e.toZoneId) },
          { key: 'c', header: 'Capacity', render: (e) => (
            <div><Input aria-label={`Capacity ${nameOf(e.fromZoneId)} to ${nameOf(e.toZoneId)}`} type="number" min={0} className="!w-24 !py-0.5" value={vals[e.id] ?? e.capacity} onChange={(ev) => setVals({ ...vals, [e.id]: ev.target.value })} />
              {bad[e.id] && <div className="text-[11px] text-red-400">{bad[e.id]}</div>}</div>) },
          { key: 's', header: '', render: (e) => <Button loading={save.loading} disabled={vals[e.id] === undefined} onClick={() => doSave(e)}><Save size={13} /> Save</Button> }]} />
      </div>
    </Card>
  );
}

function PlanView({ plan }) {
  const warn = [];
  if ((plan.zeroCapacityEdges ?? 0) > 0 || (Array.isArray(plan.zeroCapacityEdges) && plan.zeroCapacityEdges.length)) warn.push(`Zero-capacity edges: ${Array.isArray(plan.zeroCapacityEdges) ? plan.zeroCapacityEdges.length : plan.zeroCapacityEdges}`);
  if ((plan.unusableEdges ?? 0) > 0 || (Array.isArray(plan.unusableEdges) && plan.unusableEdges.length)) warn.push(`Unusable edges (zones disconnected): ${Array.isArray(plan.unusableEdges) ? plan.unusableEdges.length : plan.unusableEdges}`);
  const unit = plan.unit || '';
  return (
    <div className="space-y-3">
      <div className={`flex items-start gap-3 rounded-lg border p-3 text-sm ${plan.feasible ? 'border-emerald-500/40 bg-emerald-500/10 text-emerald-100' : 'border-orange-500/50 bg-orange-500/10 text-orange-100'}`}>
        {plan.feasible ? <CheckCircle2 className="mt-0.5 text-emerald-400" size={18} /> : <AlertTriangle className="mt-0.5 text-orange-400" size={18} />}
        <div><b>{plan.feasible ? 'Feasible: all demand can be met.' : 'Not fully feasible: some demand stays unmet.'}</b> Max flow {fmt.num(plan.maxFlow)} {unit} of {fmt.num(plan.totalDemand)} demanded (supply {fmt.num(plan.totalSupply)}); unmet {fmt.num(plan.unmetDemand)}.</div>
      </div>
      {warn.length > 0 && <div className="rounded-lg border border-amber-500/40 bg-amber-500/10 p-2 text-xs text-amber-200">{warn.join(' · ')}</div>}
      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <StatCard label="Max flow" value={fmt.num(plan.maxFlow)} tone="green" hint={unit} />
        <StatCard label="Unmet demand" value={fmt.num(plan.unmetDemand)} tone={plan.unmetDemand > 0 ? 'red' : 'green'} />
        <StatCard label="Total supply" value={fmt.num(plan.totalSupply)} />
        <StatCard label="Total demand" value={fmt.num(plan.totalDemand)} />
      </div>
      <AlgorithmInfo name="Edmonds-Karp maximum flow" complexity="O(V x E^2) time; BFS finds the shortest augmenting path in the residual graph" execution={plan.execution} />
      <div className="grid gap-4 xl:grid-cols-2">
        <div><h4 className="mb-1 text-sm font-semibold text-slate-100">Proposed deliveries</h4>
          <Table dense rows={plan.deliveries} rowKey={(r, i) => i} empty="No deliveries proposed" columns={[
            { key: 'f', header: 'From', render: (r) => r.fromZoneName }, { key: 't', header: 'To', render: (r) => r.toZoneName },
            { key: 'q', header: 'Qty', align: 'right', render: (r) => fmt.num(r.quantity) }, { key: 'p', header: 'Path', render: (r) => <span className="text-xs text-slate-400">{(r.path || []).join(' → ')}</span> }]} /></div>
        <div><h4 className="mb-1 text-sm font-semibold text-slate-100">Demand zones</h4>
          <Table dense rows={plan.demandZones} rowKey="zoneId" empty="No demand zones" columns={[
            { key: 'z', header: 'Zone', render: (r) => r.zoneName }, { key: 'd', header: 'Demand', align: 'right', render: (r) => fmt.num(r.demand) },
            { key: 'r', header: 'Received', align: 'right', render: (r) => fmt.num(r.received) }, { key: 'u', header: 'Unmet', align: 'right', render: (r) => <span className={r.unmet > 0 ? 'font-semibold text-red-400' : ''}>{fmt.num(r.unmet)}</span> }]} /></div>
      </div>
      <div className="grid gap-4 xl:grid-cols-2">
        <div><h4 className="mb-1 text-sm font-semibold text-slate-100">Edge flows</h4>
          <div className="max-h-72 overflow-y-auto"><Table dense rows={plan.edgeFlows} rowKey={(r, i) => i} empty="No edge flows" columns={[
            { key: 'e', header: 'Edge', render: (r) => `${r.fromName} → ${r.toName}` }, { key: 'f', header: 'Flow / cap', align: 'right', render: (r) => `${fmt.num(r.flow)} / ${fmt.num(r.capacity)}` },
            { key: 's', header: '', render: (r) => (r.saturated ? <Badge tone="red">Saturated</Badge> : r.flow > 0 ? <Badge tone="green">Used</Badge> : <Badge>Idle</Badge>) }]} /></div></div>
        <div><h4 className="mb-1 text-sm font-semibold text-slate-100">Minimum-cut bottlenecks</h4>
          <Table dense rows={plan.bottlenecks} rowKey={(r, i) => i} empty="No bottlenecks: demand fully satisfied" columns={[
            { key: 'k', header: 'Kind', render: (r) => <Badge tone={r.kind === 'EDGE' ? 'red' : r.kind === 'SUPPLY' ? 'orange' : 'blue'}>{r.kind}</Badge> },
            { key: 'e', header: 'Where', render: (r) => `${r.fromName ?? '—'} → ${r.toName ?? '—'}` }, { key: 'c', header: 'Capacity', align: 'right', render: (r) => fmt.num(r.capacity) }]} />
          <p className="mt-1 text-xs text-slate-400">By max-flow min-cut, the total capacity of these saturated cut edges equals the max flow.</p></div>
      </div>
      <div><h4 className="mb-1 text-sm font-semibold text-slate-100">Augmenting paths (Edmonds-Karp steps)</h4>
        <p className="mb-1 text-xs text-slate-400">Each step finds the shortest path with spare residual capacity (BFS), pushes the bottleneck amount, then reduces forward and creates reverse residual capacity so later steps can undo flow.</p>
        <div className="max-h-64 overflow-y-auto"><Table dense rows={plan.augmentingPaths} rowKey={(r, i) => i} empty="No augmenting paths found" columns={[
          { key: 'step', header: 'Step' }, { key: 'p', header: 'Path', render: (r) => (r.path || []).join(' → ') }, { key: 'b', header: 'Bottleneck', align: 'right', render: (r) => fmt.num(r.bottleneck) }]} /></div></div>
    </div>
  );
}

export default function ResourcesPage() {
  const types = useResourceTypes(); const roads = useRoads();
  const [typeId, setTypeId] = useState('');
  useEffect(() => { if (!typeId && types.data?.length) setTypeId(String(types.data[0].id)); }, [types.data, typeId]);
  const inv = useApi(() => (typeId ? get('/inventory', { resourceTypeId: typeId }) : Promise.resolve([])), [typeId]);
  const summary = useApi(() => get('/inventory/summary'), []);
  const net = useApi(() => (typeId ? get('/distribution/network', { resourceTypeId: typeId }) : Promise.resolve(null)), [typeId]);
  const hist = useApi(() => get('/distribution/allocations', { limit: 50 }), []);
  const [blocked, setBlocked] = useState([]);
  const [confirm, setConfirm] = useState(false);
  const [done, setDone] = useState(null);
  const [previewed, setPreviewed] = useState(null);
  const preview = useAction((body) => post('/distribution/preview', body));
  const commit = useAction((body) => post('/distribution/commit', body));

  const plan = preview.data;
  const stale = plan && previewed && (previewed.typeId !== typeId || JSON.stringify(previewed.blocked) !== JSON.stringify(blocked));
  const resetPlan = () => { preview.reset(); setPreviewed(null); };
  useEffect(() => { resetPlan(); setDone(null); }, [typeId]); // eslint-disable-line react-hooks/exhaustive-deps
  const runPreview = async () => { setDone(null); commit.reset(); const r = await preview.run({ resourceTypeId: Number(typeId), blockedRoadIds: blocked }); if (r) setPreviewed({ typeId, blocked: [...blocked] }); };
  const doCommit = async () => {
    const r = await commit.run({ resourceTypeId: Number(previewed.typeId), blockedRoadIds: previewed.blocked, expectedMaxFlow: plan.maxFlow, confirm: true });
    if (r) { setConfirm(false); setDone(r); resetPlan(); inv.reload(); summary.reload(); net.reload(); hist.reload(); }
  };
  const refreshData = () => { inv.reload(); summary.reload(); net.reload(); };

  const nodes = net.data?.nodes || []; const edges = net.data?.edges || [];
  const nodeById = new Map(nodes.map((n) => [n.zoneId, n]));
  const nameOf = (id) => nodeById.get(id)?.name ?? `Zone ${id}`;
  const flowKey = (a, b) => `${a}-${b}`;
  const flowMap = new Map((plan?.edgeFlows || []).map((e) => [flowKey(e.fromZoneId, e.toZoneId), e]));
  const typeObj = (types.data || []).find((t) => String(t.id) === String(typeId));

  return (
    <div className="space-y-4">
      <PageHeader title="Resource Distribution" subtitle="Segment Tree inventory queries and Edmonds-Karp max-flow distribution with preview-then-commit."
        actions={<><Select aria-label="Resource type" className="!w-56" value={typeId} onChange={(e) => setTypeId(e.target.value)}
          options={(types.data || []).map((t) => ({ value: String(t.id), label: `${t.name} (${t.unit})` }))} />
          <Button onClick={refreshData}><RefreshCw size={14} /> Refresh</Button></>} />
      {types.loading && !types.data ? <LoadingBlock label="Loading resource types…" /> : types.error ? <ErrorBanner error={types.error} onRetry={types.reload} /> : (types.data || []).length === 0 ? <EmptyState title="No resource types defined" /> : (
        <>
          <Card title="Inventory summary (segment tree totals)">
            {summary.loading && !summary.data ? <LoadingBlock /> : summary.error ? <ErrorBanner error={summary.error} onRetry={summary.reload} /> : (summary.data || []).length === 0 ? <EmptyState title="No inventory summary" /> : (
              <div className="grid grid-cols-2 gap-3 md:grid-cols-3 xl:grid-cols-5">
                {summary.data.map((s) => (
                  <button key={s.resourceTypeId} type="button" onClick={() => setTypeId(String(s.resourceTypeId))} className="text-left" aria-label={`Select ${s.name}`}>
                    <StatCard label={s.name} value={`${fmt.num(s.available)} ${s.unit || ''}`} tone={String(s.resourceTypeId) === typeId ? 'orange' : s.utilizationPct < 25 ? 'red' : 'default'} hint={`${fmt.pct(s.utilizationPct)} of ${fmt.num(s.capacity)} capacity`} />
                  </button>))}
              </div>)}
          </Card>

          <Card title={`Inventory by zone: ${typeObj?.name ?? ''}`}>
            {inv.loading && !inv.data ? <LoadingBlock /> : inv.error ? <ErrorBanner error={inv.error} onRetry={inv.reload} /> : (
              <div className="max-h-80 overflow-y-auto"><Table dense rows={inv.data} rowKey="zoneId" empty="No inventory rows for this resource" columns={[
                { key: 'zoneIndex', header: 'Index' }, { key: 'zoneName', header: 'Zone' }, { key: 'zoneRole', header: 'Role', render: (r) => <Badge tone={r.zoneRole === 'WAREHOUSE' ? 'green' : r.zoneRole === 'DEMAND' ? 'orange' : 'blue'}>{r.zoneRole}</Badge> },
                { key: 'quantity', header: 'Quantity', align: 'right', render: (r) => fmt.num(r.quantity) }, { key: 'capacity', header: 'Capacity', align: 'right', render: (r) => fmt.num(r.capacity) },
                { key: 'u', header: 'Fill', render: (r) => <div className="h-1.5 w-24 overflow-hidden rounded bg-navy-900"><div className="h-full bg-sky-500" style={{ width: `${r.capacity > 0 ? Math.min(100, (r.quantity / r.capacity) * 100) : 0}%` }} /></div> }]} /></div>)}
          </Card>

          <div className="grid gap-4 xl:grid-cols-2">
            <RangeQueryPanel resourceTypes={types.data} resourceTypeId={typeId} onTypeChange={setTypeId} />
            <AdjustPanel typeId={typeId} inventory={inv.data} onDone={refreshData} />
          </div>

          <Card title="Distribution network" subtitle="Green = warehouse, blue = hub, orange = demand. After a preview, edges show flow (green) and saturation (red).">
            {net.loading && !net.data ? <LoadingBlock /> : net.error ? <ErrorBanner error={net.error} onRetry={net.reload} /> : nodes.length === 0 ? <EmptyState title="Network is empty" /> : (
              <CityMap height={420}>
                <FitBounds points={nodes.map((n) => [n.latitude, n.longitude])} />
                {edges.map((e) => {
                  const a = nodeById.get(e.fromZoneId); const b = nodeById.get(e.toZoneId);
                  if (!a || !b) return null;
                  const fl = flowMap.get(flowKey(e.fromZoneId, e.toZoneId));
                  const color = fl?.saturated ? '#ef4444' : fl?.flow > 0 ? '#22c55e' : e.capacity === 0 ? '#475569' : '#64748b';
                  return <LinkLine key={e.id} positions={[[a.latitude, a.longitude], [b.latitude, b.longitude]]} color={color} weight={fl?.flow > 0 ? 4 : 2} dashArray={e.capacity === 0 ? '4 6' : undefined} label={`${a.name} → ${b.name}: cap ${e.capacity}${fl ? `, flow ${fl.flow}` : ''}`} />;
                })}
                {nodes.map((n) => <ZoneDot key={n.zoneId} position={[n.latitude, n.longitude]} color={ROLE_COLOR[n.role] || '#94a3b8'} radius={n.role === 'HUB' ? 6 : 8} label={`${n.name} (${n.role}) supply ${n.supply ?? 0}, demand ${n.demand ?? 0}`} />)}
              </CityMap>)}
          </Card>
          {net.data && <EdgeEditor edges={edges} nameOf={nameOf} onSaved={() => { net.reload(); resetPlan(); }} />}

          <Card title="Distribution plan (Edmonds-Karp)" subtitle="Preview never changes inventory. Commit is only possible after a fresh preview.">
            <div className="grid gap-3 lg:grid-cols-2">
              <RoadPicker roads={roads.data} value={blocked} onChange={setBlocked} />
              <div className="flex flex-wrap items-end gap-2">
                <Button variant="primary" onClick={runPreview} loading={preview.loading} disabled={!typeId}><Play size={14} /> Preview distribution</Button>
                <Button variant="danger" disabled={!plan || stale || plan.maxFlow <= 0} onClick={() => { commit.reset(); setConfirm(true); }}><CheckCheck size={14} /> Commit plan</Button>
              </div>
            </div>
            <div className="mt-3 space-y-3">
              <ErrorBanner error={preview.error} />
              {stale && <div className="rounded border border-amber-500/40 bg-amber-500/10 p-2 text-xs text-amber-200">Selection changed since the preview. Preview again before committing.</div>}
              {done && <div className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-3 text-sm text-emerald-100">Committed. Moved {fmt.num(done.maxFlow)} units in {(done.allocations || []).length} allocation(s); inventory has been updated.</div>}
              {preview.loading && <LoadingBlock label="Running Edmonds-Karp…" />}
              {!plan && !preview.loading && !done && <EmptyState title="No preview yet" hint="Choose blocked roads if you wish, then press Preview distribution." />}
              {plan && <PlanView plan={plan} />}
            </div>
          </Card>

          <Card title="Allocation history" actions={<Button onClick={hist.reload}><RefreshCw size={13} /></Button>}>
            {hist.loading && !hist.data ? <LoadingBlock /> : hist.error ? <ErrorBanner error={hist.error} onRetry={hist.reload} /> : (
              <div className="max-h-72 overflow-y-auto"><Table dense rows={hist.data} rowKey="id" empty="No committed allocations yet" columns={[
                { key: 'createdAt', header: 'When', render: (r) => fmt.dt(r.createdAt) }, { key: 'resourceName', header: 'Resource' }, { key: 'fromZoneName', header: 'From' }, { key: 'toZoneName', header: 'To' },
                { key: 'quantity', header: 'Qty', align: 'right', render: (r) => fmt.num(r.quantity) }]} /></div>)}
          </Card>
        </>
      )}
      {confirm && plan && (
        <ConfirmDialog title="Commit distribution" confirmLabel="Commit" loading={commit.loading} error={commit.error} onCancel={() => setConfirm(false)} onConfirm={doCommit}
          message={<><p>Move stock according to the previewed plan: <b>{fmt.num(plan.maxFlow)}</b> {plan.unit} of {plan.resourceName}.</p><p className="text-xs text-slate-400">The server recomputes the plan and rejects the commit (409) if the max flow differs from {fmt.num(plan.maxFlow)}.</p></>} />
      )}
    </div>
  );
}
