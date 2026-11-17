import { Select } from './ui.jsx';

export default function ZoneSelect({ zones, value, onChange, allLabel, ...props }) {
  const options = [
    ...(allLabel !== undefined ? [{ value: '', label: allLabel }] : [{ value: '', label: 'Select zone…' }]),
    ...(zones || []).map((z) => ({ value: String(z.id), label: `${z.name}${z.zoneIndex != null ? ` (#${z.zoneIndex})` : ''}` })),
  ];
  return <Select options={options} value={value ?? ''} onChange={(e) => onChange(e.target.value)} {...props} />;
}
