import { useState } from 'react';
import { Eye, Zap, RotateCcw, RefreshCw } from 'lucide-react';
import { Button, Card, StatCard, Table, PageHeader, AlgorithmInfo, LoadingBlock, ErrorBanner, EmptyState, PriorityPill, SeverityBadge, fmt } from '../components/ui.jsx';
import { ConfirmDialog, Modal } from '../components/Modal.jsx';
import FactorBars from '../components/FactorBars.jsx';
import PercentileLab from '../components/PercentileLab.jsx';
import { useApi, useAction } from '../hooks/useApi.js';
import { get, post } from '../api/client.js';
import { pretty } from '../hooks/useRef.js';

export default function PriorityPage() {
  const queue = useApi(() => get('/priority/queue', { limit: 25 }), []);
  const formula = useApi(() => get('/priority/formula'), []);
  const peek = useAction(() => get('/priority/peek'));
  const extract = useAction(() => post('/priority/extract-next'));
  const rebuild = useAction(() => post('/priority/rebuild'));
  const [confirm, setConfirm] = useState(false);
  const [notice, setNotice] = useState(null);

  const doExtract = async () => {
    const r = await extract.run();
    if (r) { setConfirm(false); setNotice(`Extracted ${r.incident?.code} (priority ${fmt.num(r.incident?.priorityScore, 2)}) and set IN_PROGRESS. ${fmt.num(r.remaining)} remaining.`); queue.reload(); }
  };
  const doRebuild = async () => {
    const r = await rebuild.run();
    if (r) { setNotice(`Heap rebuilt from the database: ${fmt.num(r.size)} incidents (${fmt.ms(r.execution?.durationMs)}).`); queue.reload(); }
  };
  const q = queue.data; const hs = q?.heapStats || {}; const fo = formula.data;

  return (
    <div className="space-y-4">
      <PageHeader title="Emergency Prioritisation" subtitle="A custom binary max-heap holds REPORTED incidents awaiting dispatch."
        actions={<>
          <Button onClick={() => peek.run()} loading={peek.loading}><Eye size={14} /> Peek</Button>
          <Button variant="primary" onClick={() => { extract.reset(); setConfirm(true); }}><Zap size={14} /> Extract next</Button>
          <Button onClick={doRebuild} loading={rebuild.loading}><RotateCcw size={14} /> Rebuild heap</Button>
          <Button onClick={queue.reload} loading={queue.loading}><RefreshCw size={14} /></Button>
        </>} />
      {notice && <div className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-3 text-sm text-emerald-200">{notice}</div>}
      <ErrorBanner error={rebuild.error} />
      <ErrorBanner error={peek.error} />

      {queue.loading && !q ? <LoadingBlock label="Loading priority queue…" /> : queue.error && !q ? <ErrorBanner error={queue.error} onRetry={queue.reload} /> : q && (
        <>
          <ErrorBanner error={queue.error} onRetry={queue.reload} />
          <div className="grid grid-cols-2 gap-3 md:grid-cols-6">
            <StatCard label="Heap size" value={fmt.num(q.size)} tone="orange" />
            <StatCard label="Insertions" value={fmt.num(hs.insertions)} />
            <StatCard label="Extractions" value={fmt.num(hs.extractions)} />
            <StatCard label="Comparisons" value={fmt.num(hs.comparisons)} />
            <StatCard label="Swaps" value={fmt.num(hs.swaps)} />
            <StatCard label="P90 threshold" value={fmt.num(q.highRiskThreshold, 2)} tone="red" />
          </div>
          <div className="grid gap-4 lg:grid-cols-3">
            <div className="space-y-3">
              <AlgorithmInfo name="Binary Max-Heap" complexity="Insert / extract O(log n), peek O(1), build O(n)" execution={q.execution} notes="Queue shown is non-destructive: ties break by earlier report, then lower id." />
              <Card title="Priority formula">
                {formula.loading ? <LoadingBlock /> : formula.error ? <ErrorBanner error={formula.error} onRetry={formula.reload} /> : fo && (
                  <div className="space-y-2 text-sm">
                    <code className="block rounded bg-navy-900 p-2 text-orange-300">{fo.formula}</code>
                    <ul className="space-y-1 text-xs text-slate-300">
                      {Object.entries(fo.definitions || {}).map(([k, v]) => <li key={k}><b className="uppercase text-slate-100">{k}</b> (weight {fo.weights?.[k]}): {v}</li>)}
                    </ul>
                  </div>
                )}
              </Card>
            </div>
            <Card className="lg:col-span-2" title={`Priority queue (top ${(q.items || []).length})`} subtitle="Click nothing needed: bars show each factor's contribution input (S, U, A, R).">
              <Table dense rows={q.items} rowKey={(r) => r.incident?.id ?? r.rank} empty="Heap is empty: no REPORTED incidents awaiting dispatch" columns={[
                { key: 'rank', header: '#', render: (r) => <b>{r.rank}</b> },
                { key: 'code', header: 'Incident', render: (r) => <div><div className="font-semibold text-slate-100">{r.incident?.code}</div><div className="text-[11px] text-slate-400">{pretty(r.incident?.category)} · {r.incident?.zoneName}</div></div> },
                { key: 'p', header: 'Priority', render: (r) => <PriorityPill score={r.incident?.priorityScore} /> },
                { key: 'sev', header: 'Sev', render: (r) => <SeverityBadge value={r.incident?.severity} /> },
                { key: 'f', header: 'Factors', render: (r) => <FactorBars compact factors={r.incident?.factors} /> },
              ]} />
              {(q.items || []).length === 0 && <EmptyState hint="Create incidents with status REPORTED or rebuild the heap." />}
            </Card>
          </div>
        </>
      )}

      <h2 className="pt-2 text-lg font-semibold text-slate-100">High-risk analysis (Quickselect)</h2>
      <PercentileLab />

      {peek.data && (
        <Modal title="Peek: highest priority incident" onClose={() => peek.reset()}>
          {peek.data.incident ? (
            <div className="space-y-2 text-sm"><div className="flex items-center gap-2"><b>{peek.data.incident.code}</b><PriorityPill score={peek.data.incident.priorityScore} /></div>
              <p>{peek.data.incident.description}</p><FactorBars factors={peek.data.incident.factors} /></div>
          ) : <EmptyState title="Heap is empty" />}
        </Modal>
      )}
      {confirm && (
        <ConfirmDialog title="Extract next incident" confirmLabel="Extract" loading={extract.loading} error={extract.error} onCancel={() => setConfirm(false)} onConfirm={doExtract}
          message={<p>This removes the top incident from the heap and sets it to <b>IN_PROGRESS</b> in the database. Continue?</p>} />
      )}
    </div>
  );
}
