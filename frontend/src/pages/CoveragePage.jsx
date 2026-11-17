import { useState } from 'react';
import { Play, ShieldCheck } from 'lucide-react';
import { Button, Card, Table, StatCard, Badge, AlgorithmInfo, PageHeader, ErrorBanner, EmptyState, Field, Input, Select, LoadingBlock, fmt } from '../components/ui.jsx';
import CityMap, { ZoneDot, RadiusCircle, FitBounds } from '../components/CityMap.jsx';
import { useAction } from '../hooks/useApi.js';
import { post } from '../api/client.js';

export default function CoveragePage() {
  const [f, setF] = useState({ radiusKm: 4, universe: 'ALL', maxNewCentres: '' });
  const [err, setErr] = useState(null);
  const run = useAction((b) => post('/coverage/set-cover', b));
  const r = run.data;

  const submit = () => {
    const rad = Number(f.radiusKm);
    if (!(rad > 0 && rad <= 50)) { setErr('Radius must be greater than 0 and at most 50 km.'); return; }
    if (f.maxNewCentres !== '' && !(Number.isInteger(Number(f.maxNewCentres)) && Number(f.maxNewCentres) >= 0)) { setErr('Max new centres must be a whole number ≥ 0 (or blank).'); return; }
    setErr(null);
    run.run({ radiusKm: rad, universe: f.universe, maxNewCentres: f.maxNewCentres === '' ? null : Number(f.maxNewCentres) });
  };

  const zones = r?.zones || [];
  const covered = new Set(r?.final?.coveredZoneIds || []);
  const existCovered = new Set(r?.existing?.coveredZoneIds || []);
  const highRisk = new Set((r?.highRiskUncoveredZones || []).map((z) => z.zoneId));
  const chosen = (r?.steps || []).filter((s) => !s.forced);
  const zoneColor = (z) => (highRisk.has(z.id) ? '#ef4444' : covered.has(z.id) ? (existCovered.has(z.id) ? '#22c55e' : '#38bdf8') : '#94a3b8');
  const sites = new Map((r?.suggestedSites || []).map((s) => [s.zoneId, s]));

  return (
    <div className="space-y-4">
      <PageHeader title="Emergency Coverage Planning" subtitle="Which zones are within reach of an emergency centre, and the cheapest set of additional centres that closes the gaps. Greedy Set Cover is compared with an exact search on small cases." />
      <Card title="Parameters" actions={<Button variant="primary" loading={run.loading} onClick={submit}><Play size={14} /> Run set cover</Button>}>
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="Coverage radius (km)" error={err && err.startsWith('Radius') ? err : null}><Input aria-label="Coverage radius" type="number" step="0.5" value={f.radiusKm} onChange={(e) => setF({ ...f, radiusKm: e.target.value })} /></Field>
          <Field label="Zones to cover"><Select aria-label="Universe" value={f.universe} onChange={(e) => setF({ ...f, universe: e.target.value })} options={[{ value: 'ALL', label: 'All zones' }, { value: 'ACTIVE_INCIDENT_ZONES', label: 'Zones with active incidents' }]} /></Field>
          <Field label="Max new centres (blank = unlimited)" error={err && err.startsWith('Max') ? err : null}><Input aria-label="Max new centres" type="number" min="0" value={f.maxNewCentres} onChange={(e) => setF({ ...f, maxNewCentres: e.target.value })} /></Field>
        </div>
        <ErrorBanner error={run.error} />
      </Card>

      {run.loading && <LoadingBlock label="Running set cover…" />}
      {!r && !run.loading && <EmptyState title="No coverage analysis yet" hint="Set a radius and press Run set cover." />}
      {r && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <StatCard label="Existing coverage" value={fmt.pct(r.existing?.coveragePct)} icon={ShieldCheck} tone={r.existing?.coveragePct >= 100 ? 'green' : 'orange'} hint={`${(r.existing?.uncoveredZoneIds || []).length} zones uncovered`} />
            <StatCard label="After new centres" value={fmt.pct(r.final?.coveragePct)} tone="green" hint={`${(r.final?.uncoveredZoneIds || []).length} zones uncovered`} />
            <StatCard label="Greedy cost" value={fmt.num(r.greedy?.totalCost, 1)} hint={`${(r.greedy?.centreIds || []).length} new centres`} />
            <StatCard label="Exact cost" value={r.exact?.available ? fmt.num(r.exact.totalCost, 1) : 'n/a'} hint={r.exact?.available ? `ratio greedy/exact = ${r.approximationRatio != null ? Number(r.approximationRatio).toFixed(3) : '—'}` : 'not computed'} />
          </div>
          <AlgorithmInfo name="Greedy Set Cover (cost-effectiveness) vs exact subset search" complexity="Greedy O(k·m·n) with a ln(n) approximation guarantee; exact is O(2^m·n), limited to 24 candidates" notes={r.note} execution={r.execution} />
          <Card title="Coverage map" subtitle="Green: covered by an existing centre. Blue: covered after adding proposed centres. Red: high-risk zone still uncovered. Grey: uncovered.">
            <CityMap height={460}>
              <FitBounds points={zones.map((z) => [z.latitude, z.longitude])} />
              {(r.centres || []).filter((c) => c.status === 'EXISTING' || chosen.some((s) => s.centreId === c.id)).map((c) => (
                <RadiusCircle key={`c${c.id}`} center={[c.latitude, c.longitude]} radiusKm={c.coverageRadiusKm ?? r.radiusKm} color={c.status === 'EXISTING' ? '#22c55e' : '#38bdf8'} />))}
              {zones.map((z) => <ZoneDot key={z.id} position={[z.latitude, z.longitude]} color={zoneColor(z)} radius={sites.has(z.id) ? 9 : 6} label={`${z.name}${highRisk.has(z.id) ? ' — high risk, uncovered' : ''}`} />)}
            </CityMap>
          </Card>
          <div className="grid gap-4 xl:grid-cols-2">
            <Card title="Greedy selection steps" subtitle="Each step picks the centre with the lowest cost per newly covered zone.">
              <Table dense rows={r.steps} rowKey="step" empty="No steps — nothing to add" columns={[
                { key: 'step', header: '#', align: 'right' }, { key: 'centreName', header: 'Centre', render: (s) => <>{s.centreName} {s.centreStatus === 'EXISTING' && <Badge tone="green">existing</Badge>}</> },
                { key: 'zoneName', header: 'Zone' }, { key: 'n', header: 'Newly covered', align: 'right', render: (s) => (s.newlyCoveredZoneIds || []).length },
                { key: 'cost', header: 'Cost', align: 'right', render: (s) => fmt.num(s.cost, 1) }, { key: 'ratio', header: 'Cost / zone', align: 'right', render: (s) => fmt.num(s.ratio, 2) }]} />
            </Card>
            <div className="space-y-4">
              <Card title="Suggested new sites"><Table dense rows={r.suggestedSites} rowKey="zoneId" empty="No new sites needed" columns={[{ key: 'zoneName', header: 'Zone' }, { key: 'wouldCover', header: 'Would cover', align: 'right' }]} /></Card>
              <Card title="High-risk zones still uncovered" subtitle="Zones above the Quickselect 90th-percentile of zone risk.">
                <Table dense rows={r.highRiskUncoveredZones} rowKey="zoneId" empty="None — every high-risk zone is covered" columns={[{ key: 'zoneName', header: 'Zone' }, { key: 'riskScore', header: 'Risk score', align: 'right', render: (z) => fmt.num(z.riskScore, 1) }]} />
              </Card>
              {r.exact?.available && <Card title="Exact solution"><p className="text-sm text-slate-300">Optimal cost {fmt.num(r.exact.totalCost, 1)} using {(r.exact.centreIds || []).length} new centres; checked {fmt.num(r.exact.subsetsChecked)} subsets of {fmt.num(r.exact.candidateCount)} candidates.</p></Card>}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
