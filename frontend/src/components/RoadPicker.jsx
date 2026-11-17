import { useState } from 'react';
import { Plus, X } from 'lucide-react';
import { Button, Select } from './ui.jsx';

export const roadLabel = (r) => `${r.zoneAName || r.zoneA} – ${r.zoneBName || r.zoneB} (${Number(r.distanceKm ?? 0).toFixed(1)} km)${r.status === 'BLOCKED' ? ' [BLOCKED]' : ''}`;

/** Chooses a set of road ids (used for what-if blocked roads). Fully controlled. */
export default function RoadPicker({ roads, value, onChange, label = 'Blocked roads (what-if, not saved)' }) {
  const [pick, setPick] = useState('');
  const list = roads || [];
  const byId = new Map(list.map((r) => [r.id, r]));
  const available = list.filter((r) => !value.includes(r.id));
  const add = () => { if (pick) { onChange([...value, Number(pick)]); setPick(''); } };
  return (
    <div>
      <span className="label-caps mb-1 block">{label}</span>
      <div className="flex gap-2">
        <Select aria-label="Road to add" className="flex-1" value={pick} onChange={(e) => setPick(e.target.value)}
          options={[{ value: '', label: available.length ? 'Select a road…' : 'No roads available' }, ...available.map((r) => ({ value: String(r.id), label: roadLabel(r) }))]} />
        <Button onClick={add} disabled={!pick} aria-label="Add road"><Plus size={14} /> Add</Button>
      </div>
      {value.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-1.5">
          {value.map((id) => (
            <span key={id} className="inline-flex items-center gap-1 rounded-md border border-red-500/40 bg-red-500/10 px-2 py-0.5 text-xs text-red-200">
              {byId.get(id) ? roadLabel(byId.get(id)) : `Road #${id}`}
              <button type="button" aria-label={`Remove road ${id}`} onClick={() => onChange(value.filter((v) => v !== id))}><X size={12} /></button>
            </span>
          ))}
        </div>
      )}
    </div>
  );
}
