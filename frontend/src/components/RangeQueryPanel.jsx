import { useEffect, useState } from 'react';
import { Button, Card, Field, Input, Select, StatCard, Table, AlgorithmInfo, ErrorBanner, EmptyState, fmt } from './ui.jsx';
import { useAction } from '../hooks/useApi.js';
import { post } from '../api/client.js';

/** Segment Tree range-sum query vs linear scan. */
export default function RangeQueryPanel({ resourceTypes, resourceTypeId, onTypeChange }) {
  const [f, setF] = useState({ resourceTypeId: resourceTypeId ?? '', fromIndex: 0, toIndex: 9, compareLinear: true });
  const [err, setErr] = useState(null);
  const q = useAction((body) => post('/inventory/range-query', body));
  useEffect(() => { if (resourceTypeId !== undefined && resourceTypeId !== null) setF((p) => ({ ...p, resourceTypeId })); }, [resourceTypeId]);
  const set = (k, v) => setF((p) => ({ ...p, [k]: v }));
  const types = resourceTypes || [];

  const run = () => {
    const a = Number(f.fromIndex); const b = Number(f.toIndex);
    if (!f.resourceTypeId) return setErr('Choose a resource type.');
    if (f.fromIndex === '' || f.toIndex === '' || !Number.isInteger(a) || !Number.isInteger(b) || a < 0 || b < 0) return setErr('Zone indexes must be non-negative whole numbers.');
    if (a > b) return setErr('From index must not be greater than To index.');
    setErr(null);
    return q.run({ resourceTypeId: Number(f.resourceTypeId), fromIndex: a, toIndex: b, compareLinear: f.compareLinear });
  };
  const r = q.data;

  return (
    <Card title="Segment Tree range query" subtitle="Sum of stock over a contiguous range of zone indexes, answered by the segment tree and (optionally) a linear scan.">
      <form className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5" onSubmit={(e) => { e.preventDefault(); run(); }}>
        <Field label="Resource type">
          <Select aria-label="Range resource type" value={f.resourceTypeId} onChange={(e) => { set('resourceTypeId', e.target.value); onTypeChange?.(e.target.value); }}
            options={[{ value: '', label: 'Select…' }, ...types.map((t) => ({ value: String(t.id), label: `${t.name} (${t.unit})` }))]} />
        </Field>
        <Field label="From zone index"><Input aria-label="From index" type="number" min={0} value={f.fromIndex} onChange={(e) => set('fromIndex', e.target.value)} /></Field>
        <Field label="To zone index"><Input aria-label="To index" type="number" min={0} value={f.toIndex} onChange={(e) => set('toIndex', e.target.value)} /></Field>
        <label className="flex items-end gap-2 pb-2 text-sm text-slate-300"><input type="checkbox" checked={f.compareLinear} onChange={(e) => set('compareLinear', e.target.checked)} /> Compare with linear scan</label>
        <div className="flex items-end"><Button variant="primary" type="submit" loading={q.loading}>Run query</Button></div>
      </form>
      {err && <p className="mt-2 text-xs text-red-400">{err}</p>}
      <div className="mt-3 space-y-3">
        <ErrorBanner error={q.error} />
        {!r && !q.loading && !q.error && <EmptyState title="No query run yet" hint="Choose a range and press Run query." />}
        {r && (
          <>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
              <StatCard label="Range sum" value={fmt.num(r.sum)} tone="orange" hint={`indexes ${r.fromIndex}..${r.toIndex}`} />
              <StatCard label="Tree nodes visited" value={fmt.num(r.segmentTree?.nodesVisited)} tone="green" hint={fmt.ms(r.segmentTree?.durationMs)} />
              <StatCard label="Linear elements scanned" value={r.linearScan ? fmt.num(r.linearScan.elementsScanned) : '—'} hint={r.linearScan ? fmt.ms(r.linearScan.durationMs) : 'not requested'} />
              <StatCard label="Results agree" value={r.linearScan ? (Number(r.linearScan.sum) === Number(r.sum) ? 'Yes' : 'NO') : '—'} tone={r.linearScan && Number(r.linearScan.sum) !== Number(r.sum) ? 'red' : 'green'} />
            </div>
            <AlgorithmInfo name="Segment Tree range-sum" complexity="Query / point update O(log n), build O(n), space O(n); linear scan is O(k) for a range of k zones" execution={r.execution} />
            <p className="text-xs text-slate-400">Zones in range ({(r.zones || []).length}): {(r.zones || []).join(', ') || '—'}</p>
            <Table dense rowKey="m" rows={[
              { m: 'Segment tree', s: r.sum, w: r.segmentTree?.nodesVisited, t: r.segmentTree?.durationMs },
              ...(r.linearScan ? [{ m: 'Linear scan', s: r.linearScan.sum, w: r.linearScan.elementsScanned, t: r.linearScan.durationMs }] : []),
            ]} columns={[{ key: 'm', header: 'Method' }, { key: 's', header: 'Sum', align: 'right', render: (x) => fmt.num(x.s) }, { key: 'w', header: 'Work (nodes / elements)', align: 'right', render: (x) => fmt.num(x.w) }, { key: 't', header: 'Time', align: 'right', render: (x) => fmt.ms(x.t) }]} />
          </>
        )}
      </div>
    </Card>
  );
}
