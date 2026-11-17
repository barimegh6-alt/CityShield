import { useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { Button, Field, Input, Select, Textarea, ErrorBanner } from './ui.jsx';
import ZoneSelect from './ZoneSelect.jsx';
import { CATEGORIES, opts } from '../hooks/useRef.js';

export function validateIncident(f) {
  const e = {};
  const d = (f.description || '').trim();
  if (d.length < 5 || d.length > 500) e.description = 'Description must be 5–500 characters.';
  if (!f.category) e.category = 'Choose a category.';
  if (!f.zoneId) e.zoneId = 'Choose a zone.';
  const intIn = (v, lo, hi) => v !== '' && Number.isInteger(Number(v)) && Number(v) >= lo && Number(v) <= hi;
  if (!intIn(f.severity, 1, 5)) e.severity = 'Severity must be a whole number 1–5.';
  if (!intIn(f.urgency, 1, 5)) e.urgency = 'Urgency must be a whole number 1–5.';
  if (!intIn(f.affectedPeople, 0, 1000000)) e.affectedPeople = 'Affected people must be 0–1,000,000.';
  const seen = new Set();
  const reqErr = [];
  f.requirements.forEach((r, i) => {
    if (!r.resourceTypeId) reqErr[i] = 'Pick a resource type.';
    else if (seen.has(String(r.resourceTypeId))) reqErr[i] = 'Duplicate resource type.';
    else if (!intIn(r.quantity, 1, Number.MAX_SAFE_INTEGER)) reqErr[i] = 'Quantity must be at least 1.';
    seen.add(String(r.resourceTypeId));
  });
  if (reqErr.some(Boolean)) e.requirements = reqErr;
  return e;
}

export function toForm(inc) {
  return {
    category: inc?.category || 'OTHER',
    zoneId: inc?.zoneId != null ? String(inc.zoneId) : '',
    description: inc?.description || '',
    severity: inc?.severity ?? 3,
    urgency: inc?.urgency ?? 3,
    affectedPeople: inc?.affectedPeople ?? 0,
    requirements: (inc?.requirements || []).map((r) => ({ resourceTypeId: String(r.resourceTypeId), quantity: r.quantity })),
  };
}

export default function IncidentForm({ initial, zones, resourceTypes, saving, serverError, onSubmit, onCancel, formId = 'incident-form' }) {
  const [f, setF] = useState(() => toForm(initial));
  const [errors, setErrors] = useState({});
  const set = (k, v) => setF((p) => ({ ...p, [k]: v }));
  const setReq = (i, k, v) => set('requirements', f.requirements.map((r, j) => (j === i ? { ...r, [k]: v } : r)));

  const submit = (ev) => {
    ev.preventDefault();
    const e = validateIncident(f);
    setErrors(e);
    if (Object.keys(e).length) return;
    onSubmit({
      category: f.category,
      zoneId: Number(f.zoneId),
      description: f.description.trim(),
      severity: Number(f.severity),
      urgency: Number(f.urgency),
      affectedPeople: Number(f.affectedPeople),
      requirements: f.requirements.map((r) => ({ resourceTypeId: Number(r.resourceTypeId), quantity: Number(r.quantity) })),
    });
  };
  const usedIds = new Set(f.requirements.map((r) => String(r.resourceTypeId)));

  return (
    <form id={formId} onSubmit={submit} className="space-y-3" noValidate>
      <ErrorBanner error={serverError} />
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Category" error={errors.category}>
          <Select aria-label="Category" options={opts(CATEGORIES)} value={f.category} onChange={(e) => set('category', e.target.value)} />
        </Field>
        <Field label="Zone" error={errors.zoneId}>
          <ZoneSelect aria-label="Zone" zones={zones} value={f.zoneId} onChange={(v) => set('zoneId', v)} />
        </Field>
      </div>
      <Field label={`Description (${f.description.trim().length}/500)`} error={errors.description}>
        <Textarea aria-label="Description" rows={3} maxLength={600} value={f.description} onChange={(e) => set('description', e.target.value)} />
      </Field>
      <div className="grid gap-3 sm:grid-cols-3">
        <Field label="Severity (1-5)" error={errors.severity}><Input aria-label="Severity" type="number" min={1} max={5} value={f.severity} onChange={(e) => set('severity', e.target.value)} /></Field>
        <Field label="Urgency (1-5)" error={errors.urgency}><Input aria-label="Urgency" type="number" min={1} max={5} value={f.urgency} onChange={(e) => set('urgency', e.target.value)} /></Field>
        <Field label="Affected people" error={errors.affectedPeople}><Input aria-label="Affected people" type="number" min={0} max={1000000} value={f.affectedPeople} onChange={(e) => set('affectedPeople', e.target.value)} /></Field>
      </div>
      <div>
        <div className="mb-1 flex items-center justify-between">
          <span className="label-caps">Resource requirements</span>
          <Button onClick={() => set('requirements', [...f.requirements, { resourceTypeId: '', quantity: 1 }])} disabled={f.requirements.length >= resourceTypes.length}><Plus size={14} /> Add</Button>
        </div>
        {f.requirements.length === 0 && <p className="text-xs text-slate-500">No resource requirements.</p>}
        <div className="space-y-2">
          {f.requirements.map((r, i) => (
            <div key={i}>
              <div className="flex gap-2">
                <Select aria-label={`Resource type ${i + 1}`} className="flex-1"
                  options={[{ value: '', label: 'Resource type…' }, ...resourceTypes.filter((t) => String(t.id) === String(r.resourceTypeId) || !usedIds.has(String(t.id))).map((t) => ({ value: String(t.id), label: `${t.name} (${t.unit})` }))]}
                  value={r.resourceTypeId} onChange={(e) => setReq(i, 'resourceTypeId', e.target.value)} />
                <Input aria-label={`Quantity ${i + 1}`} type="number" min={1} className="!w-24" value={r.quantity} onChange={(e) => setReq(i, 'quantity', e.target.value)} />
                <Button variant="ghost" aria-label="Remove requirement" onClick={() => set('requirements', f.requirements.filter((_, j) => j !== i))}><Trash2 size={15} /></Button>
              </div>
              {errors.requirements?.[i] && <div className="mt-1 text-xs text-red-400">{errors.requirements[i]}</div>}
            </div>
          ))}
        </div>
      </div>
      <div className="flex justify-end gap-2 pt-1">
        <Button onClick={onCancel}>Cancel</Button>
        <Button variant="primary" type="submit" loading={saving}>{initial?.id ? 'Save changes' : 'Create incident'}</Button>
      </div>
    </form>
  );
}
