import { useState } from 'react';
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip } from 'recharts';
import { Button, Card, Field, Input, Select, Table, AlgorithmInfo, ErrorBanner, EmptyState, StatCard, PriorityPill, SeverityBadge, StatusBadge, fmt } from './ui.jsx';
import { tooltipStyle, axisProps, gridColor } from './charts.jsx';
import { useAction } from '../hooks/useApi.js';
import { post } from '../api/client.js';
import { METRICS, opts, pretty } from '../hooks/useRef.js';

export default function PercentileLab() {
  const [f, setF] = useState({ metric: 'PRIORITY', percentile: 90, seed: 42, scope: 'ACTIVE' });
  const [err, setErr] = useState(null);
  const q = useAction((body) => post('/analytics/percentile', body));
  const sweep = useAction((body) => post('/analytics/percentile-sweep', body));
  const set = (k, v) => setF((p) => ({ ...p, [k]: v }));

  const run = async () => {
    const p = Number(f.percentile);
    if (!(p > 0 && p <= 100)) { setErr('Percentile must be greater than 0 and at most 100.'); return; }
    if (f.seed === '' || !Number.isInteger(Number(f.seed))) { setErr('Seed must be a whole number.'); return; }
    setErr(null);
    await q.run({ metric: f.metric, percentile: p, seed: Number(f.seed), scope: f.scope });
  };
  const runSweep = () => sweep.run({ metric: f.metric, seed: Number(f.seed) || 0, scope: f.scope });
  const r = q.data; const s = sweep.data;

  return (
    <div className="space-y-4">
      <Card title="Randomized Quickselect" subtitle="Finds the k-th ranked value without sorting everything; the seed makes the random pivots reproducible.">
        <form className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5" onSubmit={(e) => { e.preventDefault(); run(); }}>
          <Field label="Metric"><Select aria-label="Metric" options={opts(METRICS)} value={f.metric} onChange={(e) => set('metric', e.target.value)} /></Field>
          <Field label="Percentile" error={err && err.startsWith('Percentile') ? err : null}><Input aria-label="Percentile" type="number" step="any" min={0} max={100} value={f.percentile} onChange={(e) => set('percentile', e.target.value)} /></Field>
          <Field label="Seed" error={err && err.startsWith('Seed') ? err : null}><Input aria-label="Seed" type="number" value={f.seed} onChange={(e) => set('seed', e.target.value)} /></Field>
          <Field label="Scope"><Select aria-label="Scope" options={[{ value: 'ACTIVE', label: 'Active incidents' }, { value: 'ALL', label: 'All incidents' }]} value={f.scope} onChange={(e) => set('scope', e.target.value)} /></Field>
          <div className="flex items-end gap-2">
            <Button variant="primary" type="submit" loading={q.loading}>Run Quickselect</Button>
            <Button onClick={runSweep} loading={sweep.loading}>Sweep</Button>
          </div>
        </form>
      </Card>
      <ErrorBanner error={q.error} />
      <ErrorBanner error={sweep.error} />
      {!r && !s && !q.loading && <EmptyState title="No analysis run yet" hint="Choose a metric and percentile, then press Run Quickselect." />}

      {r && (
        <>
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <StatCard label={`P${r.percentile} threshold`} value={fmt.num(r.threshold, 2)} tone="red" hint={`${pretty(r.metric)}, ${pretty(r.scope)}`} />
            <StatCard label="Rank k (1-based)" value={fmt.num(r.k)} hint={`of n = ${fmt.num(r.n)}`} />
            <StatCard label="High-risk count" value={fmt.num(r.highRiskCount)} tone="orange" />
            <StatCard label="Seed" value={fmt.num(r.seed)} />
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <Card title="Distribution">
              {(r.distribution || []).length === 0 ? <EmptyState /> : (
                <ResponsiveContainer width="100%" height={240}>
                  <BarChart data={r.distribution}><CartesianGrid stroke={gridColor} vertical={false} /><XAxis dataKey="label" {...axisProps} /><YAxis allowDecimals={false} {...axisProps} /><Tooltip {...tooltipStyle} /><Bar dataKey="count" fill="#f97316" radius={[4, 4, 0, 0]} /></BarChart>
                </ResponsiveContainer>
              )}
            </Card>
            <div className="space-y-3">
              <AlgorithmInfo name="Randomized Quickselect" complexity="Expected O(n) time, worst case O(n^2); O(1) extra space" execution={r.execution}
                notes="Random pivots (seeded) make the expected linear time independent of input order." />
              <Card title="Quickselect vs full sort baseline">
                <Table dense rows={[
                  { m: 'Quickselect', c: fmt.num(r.quickselect?.comparisons), d: fmt.ms(r.quickselect?.durationMs), x: `${fmt.num(r.quickselect?.pivotSelections)} pivots, ${fmt.num(r.quickselect?.partitionRounds)} rounds` },
                  { m: 'Sort baseline', c: '—', d: fmt.ms(r.sortBaseline?.durationMs), x: r.sortBaseline?.agrees ? 'Same answer as Quickselect' : 'DISAGREES' },
                ]} rowKey="m" columns={[{ key: 'm', header: 'Method' }, { key: 'c', header: 'Comparisons', align: 'right' }, { key: 'd', header: 'Time', align: 'right' }, { key: 'x', header: 'Notes' }]} />
              </Card>
            </div>
          </div>
          <Card title={`High-risk incidents (top ${(r.highRiskIncidents || []).length} of ${fmt.num(r.highRiskCount)})`}>
            <Table dense rows={r.highRiskIncidents} rowKey="id" empty="No incidents at or above the threshold" columns={[
              { key: 'code', header: 'Code' }, { key: 'priorityScore', header: 'Priority', render: (x) => <PriorityPill score={x.priorityScore} /> },
              { key: 'severity', header: 'Sev', render: (x) => <SeverityBadge value={x.severity} /> }, { key: 'category', header: 'Category', render: (x) => pretty(x.category) },
              { key: 'zoneName', header: 'Zone' }, { key: 'affectedPeople', header: 'Affected', align: 'right', render: (x) => fmt.num(x.affectedPeople) },
              { key: 'status', header: 'Status', render: (x) => <StatusBadge status={x.status} /> }]} />
          </Card>
        </>
      )}

      {s && (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card title="Percentile sweep" subtitle="Same data, different ranks">
            <Table dense rows={s.rows} rowKey="percentile" empty="No rows" columns={[{ key: 'percentile', header: 'Percentile', render: (x) => `P${x.percentile}` }, { key: 'threshold', header: 'Threshold', align: 'right', render: (x) => fmt.num(x.threshold, 2) }, { key: 'k', header: 'k', align: 'right' }, { key: 'comparisons', header: 'Comparisons', align: 'right', render: (x) => fmt.num(x.comparisons) }]} />
          </Card>
          <Card title="Seed runs (P90)" subtitle="Randomisation changes the work done, never the answer.">
            <Table dense rows={s.seedRuns} rowKey="seed" empty="No runs" columns={[{ key: 'seed', header: 'Seed' }, { key: 'threshold', header: 'P90 threshold', align: 'right', render: (x) => fmt.num(x.threshold, 2) }, { key: 'comparisons', header: 'Comparisons', align: 'right', render: (x) => fmt.num(x.comparisons) }, { key: 'pivotSelections', header: 'Pivots', align: 'right', render: (x) => fmt.num(x.pivotSelections) }]} />
            <p className="mt-2 text-xs text-slate-400">Every seed returns the same threshold, but the number of comparisons and pivots varies because different random pivots are chosen. That is randomised Quickselect: same answer, different work, expected linear time.</p>
          </Card>
        </div>
      )}
    </div>
  );
}
