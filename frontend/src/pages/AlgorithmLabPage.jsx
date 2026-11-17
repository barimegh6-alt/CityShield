import { useEffect, useRef, useState } from 'react';
import { Search } from 'lucide-react';
import { Button, Card, Table, StatCard, Badge, AlgorithmInfo, PageHeader, ErrorBanner, EmptyState, Field, Input, Select, Tabs, fmt } from '../components/ui.jsx';
import PercentileLab from '../components/PercentileLab.jsx';
import { useAction } from '../hooks/useApi.js';
import { get, post } from '../api/client.js';

function Highlight({ text, positions, length }) {
  if (!positions?.length) return <>{text}</>;
  const parts = []; let last = 0;
  positions.forEach((p, i) => {
    if (p < last) return;
    parts.push(text.slice(last, p));
    parts.push(<mark key={i} className="rounded bg-orange-500/30 px-0.5 text-orange-100">{text.slice(p, p + length)}</mark>);
    last = p + length;
  });
  parts.push(text.slice(last));
  return <>{parts}</>;
}

function Autocomplete() {
  const [q, setQ] = useState('');
  const [types, setTypes] = useState('');
  const act = useAction((query, t) => get('/search/autocomplete', { q: query, types: t || undefined, limit: 10 }));
  const timer = useRef(null);
  useEffect(() => {
    clearTimeout(timer.current);
    if (!q.trim()) { act.reset(); return undefined; }
    timer.current = setTimeout(() => act.run(q.trim(), types), 200);
    return () => clearTimeout(timer.current);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [q, types]);
  const r = act.data;
  return (
    <Card title="Trie autocomplete" subtitle="Type a prefix of a zone, hospital, centre or emergency category. A trie walks one node per character instead of scanning every name.">
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="sm:col-span-2"><Field label="Prefix"><Input aria-label="Prefix" placeholder="e.g. Ko" value={q} onChange={(e) => setQ(e.target.value)} /></Field></div>
        <Field label="Type filter"><Select aria-label="Type filter" value={types} onChange={(e) => setTypes(e.target.value)} options={[{ value: '', label: 'All types' }, { value: 'ZONE', label: 'Zones' }, { value: 'HOSPITAL', label: 'Hospitals' }, { value: 'CENTRE', label: 'Centres' }, { value: 'CATEGORY', label: 'Categories' }]} /></Field>
      </div>
      <ErrorBanner error={act.error} />
      {!q.trim() ? <div className="mt-3"><EmptyState title="Start typing" hint="Suggestions appear as you type." /></div> : r && (
        <div className="mt-3 space-y-3">
          <div className="flex flex-wrap gap-2">
            {r.suggestions.length === 0 ? <span className="text-sm text-slate-400">No match for “{r.query}”.</span> : r.suggestions.map((s) => (
              <span key={`${s.type}-${s.refId}`} className="inline-flex items-center gap-1.5 rounded-md border border-line bg-navy-900 px-2 py-1 text-sm text-slate-100">{s.text}<Badge tone="blue">{s.type}</Badge></span>))}
          </div>
          <div className="text-xs text-slate-400">Trie nodes visited: <b className="tnum">{fmt.num(r.nodesVisited)}</b> · {fmt.ms(r.durationMs)}</div>
        </div>)}
    </Card>
  );
}

function IncidentSearch() {
  const [f, setF] = useState({ pattern: '', description: true, logs: false, caseSensitive: false });
  const [err, setErr] = useState(null);
  const run = useAction((b) => post('/search/incidents', b));
  const r = run.data;
  const submit = () => {
    if (!f.pattern.trim()) { setErr('Enter a search pattern.'); return; }
    const fields = [f.description && 'DESCRIPTION', f.logs && 'LOGS'].filter(Boolean);
    if (!fields.length) { setErr('Choose at least one field to search.'); return; }
    setErr(null);
    run.run({ pattern: f.pattern, fields, caseSensitive: f.caseSensitive, limit: 50 });
  };
  return (
    <Card title="KMP incident search" subtitle="Finds every occurrence of a pattern inside incident descriptions and logs. KMP never re-reads text characters; the naive matcher is run alongside for comparison.">
      <form className="grid gap-3 lg:grid-cols-4" onSubmit={(e) => { e.preventDefault(); submit(); }}>
        <div className="lg:col-span-2"><Field label="Pattern" error={err}><Input aria-label="Pattern" placeholder="e.g. smoke" value={f.pattern} onChange={(e) => setF({ ...f, pattern: e.target.value })} /></Field></div>
        <div className="flex flex-col justify-end gap-1 text-sm text-slate-300">
          <label className="flex items-center gap-2"><input type="checkbox" checked={f.description} onChange={(e) => setF({ ...f, description: e.target.checked })} /> Descriptions</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={f.logs} onChange={(e) => setF({ ...f, logs: e.target.checked })} /> Incident logs</label>
          <label className="flex items-center gap-2"><input type="checkbox" checked={f.caseSensitive} onChange={(e) => setF({ ...f, caseSensitive: e.target.checked })} /> Case sensitive</label>
        </div>
        <div className="flex items-end"><Button variant="primary" type="submit" loading={run.loading}><Search size={14} /> Search</Button></div>
      </form>
      <ErrorBanner error={run.error} />
      {r && (
        <div className="mt-4 space-y-3">
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <StatCard label="Records scanned" value={fmt.num(r.recordsScanned)} />
            <StatCard label="Total matches" value={fmt.num(r.totalMatches)} tone="orange" />
            <StatCard label="KMP comparisons" value={fmt.num(r.kmp?.comparisons)} tone="green" hint={fmt.ms(r.kmp?.durationMs)} />
            <StatCard label="Naive comparisons" value={fmt.num(r.naive?.comparisons)} hint={fmt.ms(r.naive?.durationMs)} />
          </div>
          <div className="text-xs text-slate-400">Failure table (LPS) of “{r.pattern}”: [{(r.lps || []).join(', ')}]</div>
          <AlgorithmInfo name="Knuth–Morris–Pratt string matching" complexity="O(n + m) time, O(m) extra space" execution={r.execution} />
          <Table dense rows={r.matches} rowKey={(m) => `${m.incidentId}-${m.field}-${m.positions?.[0]}`} empty="No incident matches this pattern" columns={[
            { key: 'incidentCode', header: 'Incident' }, { key: 'zoneName', header: 'Zone' }, { key: 'field', header: 'Field', render: (m) => <Badge>{m.field}</Badge> },
            { key: 'text', header: 'Text', render: (m) => <span className="text-xs"><Highlight text={m.text} positions={m.positions} length={r.pattern.length} /></span> },
            { key: 'n', header: 'Hits', align: 'right', render: (m) => (m.positions || []).length }]} />
        </div>)}
    </Card>
  );
}

export default function AlgorithmLabPage() {
  const [tab, setTab] = useState('trie');
  return (
    <div className="space-y-4">
      <PageHeader title="Algorithm Lab" subtitle="Interactive demonstrations of the search and analytics algorithms, with the work each one actually performed." />
      <Tabs value={tab} onChange={setTab} tabs={[{ id: 'trie', label: 'Trie autocomplete' }, { id: 'kmp', label: 'KMP search' }, { id: 'qs', label: 'Quickselect percentiles' }]} />
      {tab === 'trie' && <Autocomplete />}
      {tab === 'kmp' && <IncidentSearch />}
      {tab === 'qs' && <PercentileLab />}
    </div>
  );
}
