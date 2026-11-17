const DEFS = [
  { k: 's', label: 'Severity', color: 'bg-red-500' },
  { k: 'u', label: 'Urgency', color: 'bg-orange-500' },
  { k: 'a', label: 'Affected', color: 'bg-amber-400' },
  { k: 'r', label: 'Resource', color: 'bg-sky-500' },
];

export default function FactorBars({ factors, compact }) {
  if (!factors) return <span className="text-slate-500">—</span>;
  return (
    <div className={compact ? 'w-40 space-y-0.5' : 'space-y-1.5'}>
      {DEFS.map((d) => {
        const v = Math.max(0, Math.min(1, Number(factors[d.k]) || 0));
        return (
          <div key={d.k} className="flex items-center gap-2" title={`${d.label}: ${v.toFixed(2)}`}>
            <span className={`w-4 text-[10px] font-bold uppercase text-slate-400 ${compact ? '' : 'w-16 text-xs normal-case'}`}>{compact ? d.k : d.label}</span>
            <div className="h-1.5 flex-1 overflow-hidden rounded bg-navy-900">
              <div className={`h-full ${d.color}`} style={{ width: `${v * 100}%` }} />
            </div>
            <span className="w-8 text-right text-[10px] tnum text-slate-400">{v.toFixed(2)}</span>
          </div>
        );
      })}
    </div>
  );
}
