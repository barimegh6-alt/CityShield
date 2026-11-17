import { useMemo, useState } from 'react';
import { Plus, Pencil, Search, Play, CheckCheck, RefreshCw, Unlock } from 'lucide-react';
import { Button, Card, Field, Input, Select, Table, StatCard, StatusBadge, Badge, PriorityPill, AlgorithmInfo, PageHeader, LoadingBlock, ErrorBanner, EmptyState, fmt } from '../components/ui.jsx';
import { Modal, ConfirmDialog } from '../components/Modal.jsx';
import ZoneSelect from '../components/ZoneSelect.jsx';
import RoadPicker from '../components/RoadPicker.jsx';
import { useApi, useAction } from '../hooks/useApi.js';
import { get, post, put, patch } from '../api/client.js';
import { useZones, useCentres, useRoads, TEAM_TYPES, AVAILABILITIES, opts, pretty } from '../hooks/useRef.js';

function TeamForm({ team, zones, centres, onClose, onSaved }) {
  const [f, setF] = useState({ name: team?.name || '', teamType: team?.teamType || 'FIRE', baseZoneId: team?.baseZoneId != null ? String(team.baseZoneId) : '', centreId: team?.centreId != null ? String(team.centreId) : '', availability: team?.availability || 'AVAILABLE' });
  const [errs, setErrs] = useState({});
  const save = useAction((body) => (team?.id ? put(`/teams/${team.id}`, body) : post('/teams', body)));
  const set = (k, v) => setF((p) => ({ ...p, [k]: v }));
  const submit = async (e) => {
    e.preventDefault();
    const er = {};
    if (!f.name.trim()) er.name = 'Name is required.';
    if (!f.baseZoneId) er.baseZoneId = 'Choose a base zone.';
    setErrs(er);
    if (Object.keys(er).length) return;
    const r = await save.run({ name: f.name.trim(), teamType: f.teamType, baseZoneId: Number(f.baseZoneId), ...(f.centreId ? { centreId: Number(f.centreId) } : {}), availability: f.availability });
    if (r) onSaved();
  };
  return (
    <Modal title={team?.id ? `Edit team ${team.name}` : 'Add response team'} onClose={onClose}>
      <form onSubmit={submit} className="space-y-3" noValidate>
        <ErrorBanner error={save.error} />
        <Field label="Name" error={errs.name}><Input aria-label="Team name" value={f.name} onChange={(e) => set('name', e.target.value)} /></Field>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Team type"><Select aria-label="Team type" options={opts(TEAM_TYPES)} value={f.teamType} onChange={(e) => set('teamType', e.target.value)} /></Field>
          <Field label="Availability"><Select aria-label="Availability" options={opts(AVAILABILITIES)} value={f.availability} onChange={(e) => set('availability', e.target.value)} /></Field>
          <Field label="Base zone" error={errs.baseZoneId}><ZoneSelect aria-label="Base zone" zones={zones} value={f.baseZoneId} onChange={(v) => set('baseZoneId', v)} /></Field>
          <Field label="Centre (optional)"><Select aria-label="Centre" value={f.centreId} onChange={(e) => set('centreId', e.target.value)} options={[{ value: '', label: 'None' }, ...(centres || []).map((c) => ({ value: String(c.id), label: c.name }))]} /></Field>
        </div>
        <div className="flex justify-end gap-2"><Button onClick={onClose}>Cancel</Button><Button variant="primary" type="submit" loading={save.loading}>{team?.id ? 'Save changes' : 'Add team'}</Button></div>
      </form>
    </Modal>
  );
}

function EligibilityInspector({ incidents }) {
  const [id, setId] = useState('');
  const el = useAction((i) => get(`/allocation/eligibility/${i}`));
  const [onlyEligible, setOnlyEligible] = useState(false);
  const rows = (el.data || []).filter((x) => !onlyEligible || x.eligible);
  return (
    <Card title="Eligibility inspector" subtitle="Why each team can or cannot respond to an incident (GET /allocation/eligibility).">
      <div className="flex flex-wrap items-end gap-3">
        <Field label="Incident">
          <Select aria-label="Eligibility incident" className="min-w-[16rem]" value={id} onChange={(e) => setId(e.target.value)}
            options={[{ value: '', label: 'Select an incident…' }, ...(incidents || []).map((i) => ({ value: String(i.id), label: `${i.code} · ${pretty(i.category)} · P${Number(i.priorityScore).toFixed(1)}` }))]} />
        </Field>
        <Button variant="primary" disabled={!id} loading={el.loading} onClick={() => el.run(id)}><Search size={14} /> Inspect</Button>
        <label className="flex items-center gap-1.5 pb-2 text-xs text-slate-300"><input type="checkbox" checked={onlyEligible} onChange={(e) => setOnlyEligible(e.target.checked)} /> Eligible only</label>
      </div>
      <div className="mt-3 space-y-2">
        <ErrorBanner error={el.error} />
        {el.data && (
          <Table dense rows={rows} rowKey={(r) => r.team?.id} empty="No teams to show" columns={[
            { key: 'n', header: 'Team', render: (r) => r.team?.name },
            { key: 't', header: 'Type', render: (r) => pretty(r.team?.teamType) },
            { key: 'a', header: 'Availability', render: (r) => <StatusBadge status={r.team?.availability} /> },
            { key: 'e', header: 'Eligible', render: (r) => <Badge tone={r.eligible ? 'green' : 'red'}>{r.eligible ? 'Eligible' : 'Not eligible'}</Badge> },
            { key: 'r', header: 'Reasons', render: (r) => <ul className="list-disc pl-4 text-xs text-slate-300">{(r.reasons || []).map((x, i) => <li key={i}>{x}</li>)}</ul> },
          ]} />
        )}
      </div>
    </Card>
  );
}

function MatchingPanel({ incidents, roads, onCommitted }) {
  const [sel, setSel] = useState([]);
  const [reach, setReach] = useState(true);
  const [blocked, setBlocked] = useState([]);
  const [confirm, setConfirm] = useState(false);
  const [msg, setMsg] = useState(null);
  const preview = useAction((body) => post('/allocation/preview', body));
  const commit = useAction((body) => post('/allocation/commit', body));
  const p = preview.data;
  const toggle = (id) => setSel((s) => (s.includes(id) ? s.filter((x) => x !== id) : [...s, id]));
  const runPreview = () => { setMsg(null); commit.reset(); return preview.run({ ...(sel.length ? { incidentIds: sel } : {}), requireReachability: reach, blockedRoadIds: blocked }); };
  const doCommit = async () => {
    const r = await commit.run({ assignments: (p.assignments || []).map((a) => ({ incidentId: a.incidentId, teamId: a.teamId })) });
    if (r) { setConfirm(false); setMsg(`Committed ${r.committed ?? (r.assignments || []).length} assignment(s). Teams are now BUSY.`); preview.reset(); onCommitted(); }
  };
  const m = p?.metrics || {};
  return (
    <Card title="Matching preview (Kuhn bipartite matching)" subtitle="Preview never changes data. Apply re-validates in one transaction on the server.">
      <div className="grid gap-4 lg:grid-cols-2">
        <div>
          <span className="label-caps mb-1 block">Incidents to match ({sel.length ? `${sel.length} selected` : 'default: all REPORTED'})</span>
          <div className="max-h-44 overflow-y-auto rounded-lg border border-line p-2">
            {(incidents || []).length === 0 && <p className="text-xs text-slate-500">No REPORTED incidents.</p>}
            {(incidents || []).map((i) => (
              <label key={i.id} className="flex items-center gap-2 py-0.5 text-xs text-slate-300">
                <input type="checkbox" checked={sel.includes(i.id)} onChange={() => toggle(i.id)} />
                <span className="font-semibold">{i.code}</span> {pretty(i.category)} · S{i.severity} · P{Number(i.priorityScore).toFixed(1)}
              </label>
            ))}
          </div>
        </div>
        <div className="space-y-3">
          <label className="flex items-center gap-2 text-sm text-slate-300"><input type="checkbox" checked={reach} onChange={(e) => setReach(e.target.checked)} /> Require road reachability (DSU)</label>
          <RoadPicker roads={roads} value={blocked} onChange={setBlocked} />
          <Button variant="primary" onClick={runPreview} loading={preview.loading}><Play size={14} /> Preview matching</Button>
        </div>
      </div>
      <div className="mt-4 space-y-3">
        {msg && <div className="rounded-lg border border-emerald-500/40 bg-emerald-500/10 p-3 text-sm text-emerald-200">{msg}</div>}
        <ErrorBanner error={preview.error} />
        {!p && !preview.loading && <EmptyState title="No preview yet" hint="Press Preview matching to see proposed assignments." />}
        {preview.loading && <LoadingBlock label="Computing matching…" />}
        {p && (
          <>
            <div className="grid grid-cols-2 gap-3 md:grid-cols-4 lg:grid-cols-6">
              <StatCard label="Incidents" value={fmt.num(m.incidentsConsidered)} />
              <StatCard label="Slots matched" value={`${fmt.num(m.slotsMatched)} / ${fmt.num(m.slotsTotal)}`} tone="green" />
              <StatCard label="Fully staffed" value={fmt.num(m.incidentsFullyStaffed)} tone="green" hint={`${fmt.num(m.incidentsPartial)} partial, ${fmt.num(m.incidentsUnstaffed)} unstaffed`} />
              <StatCard label="Teams used" value={`${fmt.num(m.teamsUsed)} / ${fmt.num(m.teamsAvailable)}`} tone="orange" />
              <StatCard label="Matching size" value={fmt.num(m.matchingSize)} />
              <StatCard label="Efficiency" value={fmt.pct(m.efficiencyPct)} tone="blue" hint={`${fmt.num(p.componentCount)} road components`} />
            </div>
            <AlgorithmInfo name="Kuhn's bipartite matching (augmenting-path DFS)" complexity="O(V x E) time; incidents expand into slots (2 teams if severity >= 4)" execution={p.execution} />
            <div className="grid gap-4 xl:grid-cols-2">
              <div>
                <h4 className="mb-1 text-sm font-semibold text-slate-100">Proposed assignments ({(p.assignments || []).length})</h4>
                <Table dense rows={p.assignments} rowKey={(r, i) => `${r.incidentId}-${r.teamId}-${i}`} empty="No assignments possible" columns={[
                  { key: 'c', header: 'Incident', render: (r) => <b>{r.incidentCode}</b> }, { key: 'p', header: 'Priority', render: (r) => <PriorityPill score={r.priorityScore} /> },
                  { key: 's', header: 'Slot', render: (r) => r.slot }, { key: 't', header: 'Team', render: (r) => `${r.teamName} (${pretty(r.teamType)})` },
                  { key: 'd', header: 'Distance', align: 'right', render: (r) => fmt.km(r.distanceKm) }]} />
              </div>
              <div>
                <h4 className="mb-1 text-sm font-semibold text-slate-100">Unassigned / partial ({(p.unassigned || []).length})</h4>
                <Table dense rows={p.unassigned} rowKey={(r) => r.incidentId} empty="Every slot was filled" columns={[
                  { key: 'c', header: 'Incident', render: (r) => <b>{r.incidentCode}</b> }, { key: 'p', header: 'Priority', render: (r) => <PriorityPill score={r.priorityScore} /> },
                  { key: 'f', header: 'Filled', render: (r) => `${r.slotsFilled ?? 0}/${r.slotsNeeded ?? '?'}` }, { key: 'r', header: 'Reason', render: (r) => <span className="text-xs text-orange-300">{r.reason || '—'}</span> }]} />
              </div>
            </div>
            <div className="flex justify-end">
              <Button variant="primary" disabled={(p.assignments || []).length === 0} onClick={() => { commit.reset(); setConfirm(true); }}><CheckCheck size={14} /> Apply assignments</Button>
            </div>
          </>
        )}
      </div>
      {confirm && p && (
        <ConfirmDialog title="Apply assignments" confirmLabel="Apply" loading={commit.loading} error={commit.error} onCancel={() => setConfirm(false)} onConfirm={doCommit}
          message={<p>This will assign <b>{(p.assignments || []).length}</b> team slot(s), mark those teams BUSY and set the incidents to ASSIGNED. The server re-validates everything first.</p>} />
      )}
    </Card>
  );
}

export default function TeamsPage() {
  const teams = useApi(() => get('/teams'), []);
  const zones = useZones(); const centres = useCentres(); const roads = useRoads();
  const reported = useApi(() => get('/incidents', { status: 'REPORTED', sort: 'priority', dir: 'desc', size: 100 }), []);
  const active = useApi(() => get('/allocation/assignments', { activeOnly: true }), []);
  const [fl, setFl] = useState({ type: '', availability: '', q: '' });
  const [edit, setEdit] = useState(null);
  const toggle = useAction((id, availability) => patch(`/teams/${id}/availability`, { availability }));
  const release = useAction((id) => patch(`/allocation/assignments/${id}/release`));

  const all = teams.data || [];
  const rows = useMemo(() => all.filter((t) => (!fl.type || t.teamType === fl.type) && (!fl.availability || t.availability === fl.availability) && (!fl.q || `${t.name} ${t.baseZoneName}`.toLowerCase().includes(fl.q.toLowerCase()))), [all, fl]);
  const cnt = (a) => all.filter((t) => t.availability === a).length;
  const refreshAll = () => { teams.reload(); active.reload(); reported.reload(); };
  const doToggle = async (t, a) => { if (a === t.availability) return; const r = await toggle.run(t.id, a); if (r) teams.reload(); };
  const doRelease = async (id) => { const r = await release.run(id); if (r) refreshAll(); };

  return (
    <div className="space-y-4">
      <PageHeader title="Response Team Allocation" subtitle="Teams, eligibility, Kuhn matching preview and committed assignments."
        actions={<><Button onClick={refreshAll}><RefreshCw size={14} /> Refresh</Button><Button variant="primary" onClick={() => setEdit({})}><Plus size={14} /> Add team</Button></>} />
      {teams.loading && !teams.data ? <LoadingBlock label="Loading teams…" /> : teams.error && !teams.data ? <ErrorBanner error={teams.error} onRetry={teams.reload} /> : (
        <>
          <ErrorBanner error={teams.error} onRetry={teams.reload} />
          <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
            <StatCard label="Teams" value={fmt.num(all.length)} />
            <StatCard label="Available" value={fmt.num(cnt('AVAILABLE'))} tone="green" />
            <StatCard label="Busy" value={fmt.num(cnt('BUSY'))} tone="orange" />
            <StatCard label="Off duty" value={fmt.num(cnt('OFF_DUTY'))} />
          </div>
          <Card title="Teams" actions={<>
            <Input aria-label="Search teams" placeholder="Search name or zone" className="!w-44" value={fl.q} onChange={(e) => setFl({ ...fl, q: e.target.value })} />
            <Select aria-label="Filter type" className="!w-36" options={opts(TEAM_TYPES, 'All types')} value={fl.type} onChange={(e) => setFl({ ...fl, type: e.target.value })} />
            <Select aria-label="Filter availability" className="!w-36" options={opts(AVAILABILITIES, 'Any availability')} value={fl.availability} onChange={(e) => setFl({ ...fl, availability: e.target.value })} />
          </>}>
            <ErrorBanner error={toggle.error} />
            <Table dense rows={rows} rowKey="id" empty="No teams match the filters" columns={[
              { key: 'name', header: 'Team' }, { key: 'teamType', header: 'Type', render: (t) => pretty(t.teamType) },
              { key: 'baseZoneName', header: 'Base zone' }, { key: 'centreName', header: 'Centre' },
              { key: 'availability', header: 'Availability', render: (t) => (
                <Select aria-label={`Availability of ${t.name}`} className="!w-32 !py-0.5 text-xs" value={t.availability} onChange={(e) => doToggle(t, e.target.value)} options={opts(AVAILABILITIES)} />) },
              { key: 'cur', header: 'Incident', render: (t) => (t.currentIncidentId != null ? `#${t.currentIncidentId}` : '—') },
              { key: 'act', header: '', render: (t) => <Button variant="ghost" aria-label={`Edit ${t.name}`} onClick={() => setEdit(t)}><Pencil size={14} /></Button> },
            ]} />
          </Card>
        </>
      )}
      {reported.error ? <ErrorBanner error={reported.error} onRetry={reported.reload} /> : (
        <>
          <EligibilityInspector incidents={reported.data?.content} />
          <MatchingPanel incidents={reported.data?.content} roads={roads.data} onCommitted={refreshAll} />
        </>
      )}
      <Card title="Active assignments" actions={<Button onClick={active.reload}><RefreshCw size={13} /></Button>}>
        <ErrorBanner error={release.error} />
        {active.loading && !active.data ? <LoadingBlock /> : active.error ? <ErrorBanner error={active.error} onRetry={active.reload} /> : (
          <Table dense rows={active.data} rowKey="id" empty="No active assignments" columns={[
            { key: 'incidentCode', header: 'Incident' }, { key: 'teamName', header: 'Team' }, { key: 'teamType', header: 'Type', render: (r) => pretty(r.teamType) },
            { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> }, { key: 'assignedAt', header: 'Assigned', render: (r) => fmt.dt(r.assignedAt) },
            { key: 'rel', header: '', render: (r) => <Button loading={release.loading} onClick={() => doRelease(r.id)}><Unlock size={13} /> Release</Button> }]} />
        )}
      </Card>
      {edit && <TeamForm team={edit.id ? edit : null} zones={zones.data || []} centres={centres.data || []} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); teams.reload(); }} />}
    </div>
  );
}
