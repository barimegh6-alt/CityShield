import { useState } from 'react';
import { Pencil, Archive } from 'lucide-react';
import { Button, Card, Table, StatusBadge, SeverityBadge, PriorityPill, LoadingBlock, ErrorBanner, fmt } from './ui.jsx';
import { Modal, ConfirmDialog } from './Modal.jsx';
import FactorBars from './FactorBars.jsx';
import { useApi, useAction } from '../hooks/useApi.js';
import { get, patch, del } from '../api/client.js';
import { pretty } from '../hooks/useRef.js';

export default function IncidentDetail({ id, onClose, onChanged, onEdit }) {
  const { data: d, loading, error, reload } = useApi(() => get(`/incidents/${id}`), [id]);
  const [confirmArchive, setConfirmArchive] = useState(false);
  const [note, setNote] = useState('');
  const changeStatus = useAction((status) => patch(`/incidents/${id}/status`, { status, ...(note.trim() ? { note: note.trim() } : {}) }));
  const archive = useAction(() => del(`/incidents/${id}`));

  const doStatus = async (s) => { const r = await changeStatus.run(s); if (r) { setNote(''); reload(); onChanged?.(); } };
  const doArchive = async () => { const r = await archive.run(); if (r) { setConfirmArchive(false); reload(); onChanged?.(); } };
  const next = d?.allowedNextStatuses || [];

  return (
    <Modal side title={d ? `${d.code} — ${pretty(d.category)}` : 'Incident detail'} onClose={onClose}>
      {loading && !d && <LoadingBlock />}
      <ErrorBanner error={error} onRetry={reload} />
      {d && (
        <div className="space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={d.status} /> <SeverityBadge value={d.severity} /> <PriorityPill score={d.priorityScore} />
            <span className="text-xs text-slate-400">Reported {fmt.dt(d.reportedAt)}</span>
          </div>
          <p className="text-sm text-slate-200">{d.description}</p>
          <div className="grid grid-cols-2 gap-2 text-sm text-slate-300 sm:grid-cols-4">
            <div><div className="label-caps">Zone</div>{d.zoneName}</div>
            <div><div className="label-caps">Urgency</div>{d.urgency}</div>
            <div><div className="label-caps">Affected</div>{fmt.num(d.affectedPeople)}</div>
            <div><div className="label-caps">Location</div>{d.latitude != null ? `${Number(d.latitude).toFixed(3)}, ${Number(d.longitude).toFixed(3)}` : '—'}</div>
          </div>
          <Card title="Priority factors (S, U, A, R)" subtitle="Each factor normalised to 0..1; P = 100 x (0.40S + 0.25U + 0.20A + 0.15R)"><FactorBars factors={d.factors} /></Card>
          <Card title="Resource requirements">
            <Table dense rows={d.requirements} rowKey="resourceTypeId" empty="No requirements"
              columns={[{ key: 'resourceName', header: 'Resource' }, { key: 'quantity', header: 'Quantity', align: 'right' }, { key: 'unit', header: 'Unit' }]} />
          </Card>
          <Card title="Change status" subtitle="Only transitions allowed by the server are offered.">
            <ErrorBanner error={changeStatus.error} />
            {next.length === 0 ? <p className="text-sm text-slate-500">No further transitions available.</p> : (
              <div className="space-y-2">
                <input aria-label="Status note" value={note} onChange={(e) => setNote(e.target.value)} placeholder="Optional note" className="w-full rounded-lg border border-line bg-navy-900 px-3 py-1.5 text-sm" />
                <div className="flex flex-wrap gap-2">
                  {next.map((s) => <Button key={s} loading={changeStatus.loading} onClick={() => doStatus(s)}>{pretty(s)}</Button>)}
                </div>
              </div>
            )}
          </Card>
          <Card title="Assignments"><Table dense rows={d.assignments} rowKey="id" empty="No assignments"
            columns={[{ key: 'teamName', header: 'Team' }, { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> }, { key: 'assignedAt', header: 'Assigned', render: (r) => fmt.dt(r.assignedAt) }]} /></Card>
          <Card title="Status history"><Table dense rows={d.history} empty="No history"
            columns={[{ key: 'changedAt', header: 'When', render: (r) => fmt.dt(r.changedAt) }, { key: 'oldStatus', header: 'From', render: (r) => (r.oldStatus ? <StatusBadge status={r.oldStatus} /> : '—') }, { key: 'newStatus', header: 'To', render: (r) => <StatusBadge status={r.newStatus} /> }, { key: 'note', header: 'Note' }]} /></Card>
          <Card title="Logs"><Table dense rows={d.logs} empty="No logs" columns={[{ key: 'loggedAt', header: 'When', render: (r) => fmt.dt(r.loggedAt) }, { key: 'text', header: 'Entry' }]} /></Card>
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => onEdit?.(d)}><Pencil size={14} /> Edit</Button>
            <Button variant="danger" disabled={d.status === 'ARCHIVED'} onClick={() => setConfirmArchive(true)}><Archive size={14} /> Archive</Button>
          </div>
        </div>
      )}
      {confirmArchive && (
        <ConfirmDialog danger title="Archive incident" confirmLabel="Archive" loading={archive.loading} error={archive.error}
          message={<p>Archive <b>{d?.code}</b>? This is a soft delete: the record is kept with status ARCHIVED.</p>}
          onConfirm={doArchive} onCancel={() => setConfirmArchive(false)} />
      )}
    </Modal>
  );
}
