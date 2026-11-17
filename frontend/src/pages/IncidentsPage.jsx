import { useEffect, useState } from 'react';
import { Plus, Search, ChevronLeft, ChevronRight } from 'lucide-react';
import { Button, Card, Input, Select, Table, PageHeader, StatusBadge, SeverityBadge, PriorityPill, LoadingBlock, ErrorBanner, fmt } from '../components/ui.jsx';
import { Modal } from '../components/Modal.jsx';
import ZoneSelect from '../components/ZoneSelect.jsx';
import IncidentForm from '../components/IncidentForm.jsx';
import IncidentDetail from '../components/IncidentDetail.jsx';
import { useApi, useAction } from '../hooks/useApi.js';
import { get, post, put } from '../api/client.js';
import { CATEGORIES, INCIDENT_STATUSES, opts, pretty, useZones, useResourceTypes } from '../hooks/useRef.js';

const PAGE_SIZE = 15;

export default function IncidentsPage() {
  const [filters, setFilters] = useState({ status: 'ACTIVE', category: '', zoneId: '', q: '', sort: 'priority', dir: 'desc' });
  const [qInput, setQInput] = useState('');
  const [page, setPage] = useState(0);
  const [detailId, setDetailId] = useState(null);
  const [form, setForm] = useState(null); // {initial?}
  const zones = useZones();
  const types = useResourceTypes();

  useEffect(() => { const t = setTimeout(() => { setFilters((f) => ({ ...f, q: qInput.trim() })); setPage(0); }, 300); return () => clearTimeout(t); }, [qInput]);
  const setF = (k, v) => { setFilters((f) => ({ ...f, [k]: v })); setPage(0); };

  const list = useApi(() => get('/incidents', { ...filters, page, size: PAGE_SIZE }), [JSON.stringify(filters), page]);
  const save = useAction((body, id) => (id ? put(`/incidents/${id}`, body) : post('/incidents', body)));

  const submit = async (body) => {
    const r = await save.run(body, form?.initial?.id);
    if (r) { setForm(null); save.reset(); list.reload(); }
  };
  const d = list.data;

  const cols = [
    { key: 'code', header: 'Code', render: (r) => <span className="font-semibold text-slate-100">{r.code}</span> },
    { key: 'priorityScore', header: 'Priority', render: (r) => <PriorityPill score={r.priorityScore} /> },
    { key: 'severity', header: 'Severity', render: (r) => <SeverityBadge value={r.severity} /> },
    { key: 'category', header: 'Category', render: (r) => pretty(r.category) },
    { key: 'zoneName', header: 'Zone' },
    { key: 'description', header: 'Description', render: (r) => <span className="line-clamp-1 max-w-xs text-slate-300">{r.description}</span> },
    { key: 'affectedPeople', header: 'Affected', align: 'right', render: (r) => fmt.num(r.affectedPeople) },
    { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
    { key: 'reportedAt', header: 'Reported', render: (r) => fmt.dt(r.reportedAt) },
  ];

  return (
    <div>
      <PageHeader title="Incident Management" subtitle="Search, filter, create and manage incidents. Priority scores are computed by the server's PriorityCalculator."
        actions={<Button variant="primary" onClick={() => { save.reset(); setForm({}); }}><Plus size={15} /> New incident</Button>} />
      <Card className="mb-4">
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-6">
          <div className="relative lg:col-span-2">
            <Search size={14} className="absolute left-3 top-2.5 text-slate-500" />
            <Input aria-label="Search code or description" placeholder="Search code or description…" className="!pl-8" value={qInput} onChange={(e) => setQInput(e.target.value)} />
          </div>
          <Select aria-label="Status filter" value={filters.status} onChange={(e) => setF('status', e.target.value)}
            options={[{ value: '', label: 'All statuses' }, { value: 'ACTIVE', label: 'Active (reported/assigned/in progress)' }, ...INCIDENT_STATUSES.map((s) => ({ value: s, label: pretty(s) }))]} />
          <Select aria-label="Category filter" value={filters.category} onChange={(e) => setF('category', e.target.value)} options={opts(CATEGORIES, 'All categories')} />
          <ZoneSelect aria-label="Zone filter" zones={zones.data || []} allLabel="All zones" value={filters.zoneId} onChange={(v) => setF('zoneId', v)} />
          <div className="flex gap-2">
            <Select aria-label="Sort by" value={filters.sort} onChange={(e) => setF('sort', e.target.value)} options={[{ value: 'priority', label: 'Priority' }, { value: 'time', label: 'Time' }]} />
            <Select aria-label="Sort direction" value={filters.dir} onChange={(e) => setF('dir', e.target.value)} options={[{ value: 'desc', label: 'Desc' }, { value: 'asc', label: 'Asc' }]} />
          </div>
        </div>
      </Card>
      <ErrorBanner error={list.error} onRetry={list.reload} />
      {list.loading && !d ? <LoadingBlock label="Loading incidents…" /> : d && (
        <Card title={`${fmt.num(d.totalElements)} incident${d.totalElements === 1 ? '' : 's'}`} subtitle="Click a row for the detail panel"
          actions={list.loading && <span className="text-xs text-slate-400">Refreshing…</span>}>
          <Table columns={cols} rows={d.content} rowKey="id" onRowClick={(r) => setDetailId(r.id)} empty="No incidents match these filters" />
          <div className="mt-3 flex items-center justify-between text-sm text-slate-400">
            <span>Page {(d.page ?? 0) + 1} of {Math.max(1, d.totalPages ?? 1)}</span>
            <div className="flex gap-2">
              <Button disabled={page <= 0} onClick={() => setPage((p) => p - 1)}><ChevronLeft size={14} /> Prev</Button>
              <Button disabled={page + 1 >= (d.totalPages ?? 1)} onClick={() => setPage((p) => p + 1)}>Next <ChevronRight size={14} /></Button>
            </div>
          </div>
        </Card>
      )}

      {detailId != null && !form && (
        <IncidentDetail id={detailId} onClose={() => setDetailId(null)} onChanged={list.reload}
          onEdit={(inc) => { save.reset(); setForm({ initial: inc }); }} />
      )}
      {form && (
        <Modal title={form.initial ? `Edit ${form.initial.code}` : 'New incident'} wide onClose={() => setForm(null)}>
          {(zones.loading || types.loading) ? <LoadingBlock /> : (zones.error || types.error) ? <ErrorBanner error={zones.error || types.error} onRetry={() => { zones.reload(); types.reload(); }} /> : (
            <IncidentForm initial={form.initial} zones={zones.data || []} resourceTypes={types.data || []} saving={save.loading} serverError={save.error}
              onSubmit={submit} onCancel={() => setForm(null)} />
          )}
        </Modal>
      )}
    </div>
  );
}
