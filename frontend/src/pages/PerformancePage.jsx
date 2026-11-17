import { useMemo, useState } from 'react';
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend } from 'recharts';
import { Gauge, Play } from 'lucide-react';
import { Button, Card, Table, Badge, PageHeader, LoadingBlock, ErrorBanner, EmptyState, Select, Field, Input, StatCard, Tabs, fmt } from '../components/ui.jsx';
import { COLORS, tooltipStyle, axisProps, gridColor } from '../components/charts.jsx';
import { useApi, useAction } from '../hooks/useApi.js';
import { get, post } from '../api/client.js';

const ALGOS = ['HEAP', 'DSU', 'SEGMENT_TREE', 'TRIE', 'KMP', 'MAX_FLOW', 'MATCHING', 'SET_COVER', 'TSP', 'QUICKSELECT'];
const pretty = (s) => String(s).replace(/_/g, ' ');

export default function PerformancePage() {
  const [tab, setTab] = useState('bench');
  const [algo, setAlgo] = useState('ALL');
  const [reps, setReps] = useState(5);
  const [shown, setShown] = useState('HEAP');
  const [localErr, setLocalErr] = useState(null);
  const results = useApi(() => get('/benchmarks/results'), []);
  const logs = useApi(() => get('/executions', { limit: 50 }), []);
  const run = useAction((b) => post('/benchmarks/run', b));

  const rows = useMemo(() => results.data || [], [results.data]);
  const forAlgo = rows.filter((r) => r.algorithm === shown);
  const variants = [...new Set(forAlgo.map((r) => r.variant))];
  const sizes = [...new Set(forAlgo.map((r) => r.inputSize))].sort((a, b) => a - b);
  const chart = sizes.map((n) => {
    const o = { size: fmt.num(n) };
    forAlgo.filter((r) => r.inputSize === n).forEach((r) => { o[r.variant] = r.medianMs; });
    return o;
  });
  const machine = run.data?.machine;

  const start = async () => {
    const n = Number(reps);
    if (!Number.isInteger(n) || n < 1 || n > 25) { setLocalErr('Repetitions must be a whole number from 1 to 25.'); return; }
    setLocalErr(null);
    const res = await run.run({ algorithm: algo, repetitions: n });
    if (res) { results.reload(); logs.reload(); }
  };

  return (
    <div className="space-y-4">
      <PageHeader title="Performance Evaluation" subtitle="Measured wall-clock time of each custom algorithm against a simple baseline, on generated inputs. Nothing on this page is hard-coded: every figure comes from a benchmark run on the server you are connected to." />
      <Tabs value={tab} onChange={setTab} tabs={[{ id: 'bench', label: 'Benchmarks' }, { id: 'logs', label: 'Execution log' }]} />
      {tab === 'bench' && (
        <>
          <Card title="Run benchmarks" subtitle="Runs on the backend machine; a full run takes several seconds. Results replace earlier stored rows for the chosen algorithm."
            actions={<Button variant="primary" loading={run.loading} onClick={start}><Play size={14} /> Run</Button>}>
            <div className="grid gap-3 sm:grid-cols-3">
              <Field label="Algorithm"><Select aria-label="Algorithm" value={algo} onChange={(e) => setAlgo(e.target.value)} options={[{ value: 'ALL', label: 'All algorithms' }, ...ALGOS.map((a) => ({ value: a, label: pretty(a) }))]} /></Field>
              <Field label="Repetitions (1–25)" error={localErr}><Input aria-label="Repetitions" type="number" min="1" max="25" value={reps} onChange={(e) => setReps(e.target.value)} /></Field>
              {machine && <div className="text-xs text-slate-400">Last run: {machine.os}, Java {machine.javaVersion}, {machine.cpus} CPUs</div>}
            </div>
            <ErrorBanner error={run.error} />
          </Card>

          {results.loading && !results.data ? <LoadingBlock /> : results.error && !results.data ? <ErrorBanner error={results.error} onRetry={results.reload} /> : rows.length === 0 ? (
            <EmptyState title="No benchmark results stored yet" hint="Press Run to measure the algorithms on this machine." />
          ) : (
            <>
              <div className="flex flex-wrap gap-2">
                {ALGOS.filter((a) => rows.some((r) => r.algorithm === a)).map((a) => (
                  <button key={a} type="button" onClick={() => setShown(a)} className={`rounded-md border px-2.5 py-1 text-xs ${shown === a ? 'border-orange-500 bg-orange-500/15 text-orange-300' : 'border-line text-slate-300 hover:bg-navy-700'}`}>{pretty(a)}</button>))}
              </div>
              <Card title={`${pretty(shown)} — median time by input size`} subtitle="Milliseconds (lower is better). Logarithmic axis because inputs grow by orders of magnitude.">
                {chart.length === 0 ? <EmptyState title="No rows for this algorithm" /> : (
                  <div style={{ height: 300 }}>
                    <ResponsiveContainer>
                      <BarChart data={chart} margin={{ left: 8, right: 8 }}>
                        <CartesianGrid stroke={gridColor} strokeDasharray="3 3" />
                        <XAxis dataKey="size" {...axisProps} /><YAxis scale="log" domain={['auto', 'auto']} allowDataOverflow {...axisProps} />
                        <Tooltip {...tooltipStyle} formatter={(v) => `${v} ms`} /><Legend />
                        {variants.map((v, i) => <Bar key={v} dataKey={v} name={pretty(v)} fill={COLORS[i % COLORS.length]} />)}
                      </BarChart>
                    </ResponsiveContainer>
                  </div>)}
              </Card>
              <Card title="All measurements" subtitle="Median, minimum and maximum of the repetitions (after one warm-up run). Notes show solution quality where it differs.">
                <div className="max-h-[28rem] overflow-y-auto">
                  <Table dense rows={forAlgo} rowKey={(r, i) => `${r.variant}-${r.inputSize}-${i}`} empty="No rows" columns={[
                    { key: 'variant', header: 'Variant', render: (r) => <Badge tone={r.variant === 'CUSTOM' ? 'orange' : 'default'}>{pretty(r.variant)}</Badge> },
                    { key: 'inputSize', header: 'Input size', align: 'right', render: (r) => fmt.num(r.inputSize) },
                    { key: 'medianMs', header: 'Median', align: 'right', render: (r) => fmt.ms(r.medianMs) },
                    { key: 'minMs', header: 'Min', align: 'right', render: (r) => fmt.ms(r.minMs) },
                    { key: 'maxMs', header: 'Max', align: 'right', render: (r) => fmt.ms(r.maxMs) },
                    { key: 'repetitions', header: 'Reps', align: 'right' },
                    { key: 'note', header: 'Note', render: (r) => <span className="text-xs text-slate-400">{r.note}</span> }]} />
                </div>
              </Card>
              <p className="text-xs text-slate-400">Reading the results: a custom algorithm is not always faster at small sizes — constant factors and JIT warm-up dominate there. The comparison that matters is how the gap changes as the input grows.</p>
            </>
          )}
        </>
      )}
      {tab === 'logs' && (
        <Card title="Algorithm execution log" subtitle="Every API call that ran an algorithm is recorded with its input size and duration."
          actions={<Button onClick={logs.reload}>Refresh</Button>}>
          {logs.loading && !logs.data ? <LoadingBlock /> : <><ErrorBanner error={logs.error} onRetry={logs.reload} />
            <div className="max-h-[32rem] overflow-y-auto"><Table dense rows={logs.data || []} rowKey="id" empty="No executions recorded yet" columns={[
              { key: 'runAt', header: 'When', render: (r) => fmt.dt(r.runAt) }, { key: 'algorithm', header: 'Algorithm', render: (r) => <Badge>{pretty(r.algorithm)}</Badge> },
              { key: 'inputSize', header: 'Input', align: 'right', render: (r) => fmt.num(r.inputSize) }, { key: 'durationMs', header: 'Duration', align: 'right', render: (r) => fmt.ms(r.durationMs) },
              { key: 'summary', header: 'Summary', render: (r) => <span className="text-xs text-slate-400">{r.summary}</span> }]} /></div></>}
        </Card>
      )}
    </div>
  );
}
