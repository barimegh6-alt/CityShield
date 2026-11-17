import { Loader2, AlertTriangle, Inbox, RefreshCw } from 'lucide-react';

const cx = (...a) => a.filter(Boolean).join(' ');

export const fmt = {
  num: (n, d = 0) => (n === null || n === undefined || Number.isNaN(Number(n)) ? '—' : Number(n).toLocaleString(undefined, { maximumFractionDigits: d })),
  ms: (n) => (n === null || n === undefined || Number.isNaN(Number(n)) ? '—' : `${Number(n).toLocaleString(undefined, { maximumFractionDigits: 3 })} ms`),
  pct: (n, d = 1) => (n === null || n === undefined || Number.isNaN(Number(n)) ? '—' : `${Number(n).toFixed(d)}%`),
  dt: (iso) => {
    if (!iso) return '—';
    const d = new Date(iso);
    return Number.isNaN(d.getTime()) ? String(iso) : d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
  },
  km: (n) => (n === null || n === undefined || Number.isNaN(Number(n)) ? '—' : `${Number(n).toFixed(2)} km`),
};

export function Card({ title, subtitle, actions, children, className }) {
  return (
    <section className={cx('rounded-xl border border-line bg-navy-800 shadow-sm', className)}>
      {(title || actions) && (
        <header className="flex flex-wrap items-start justify-between gap-2 border-b border-line px-4 py-3">
          <div>
            {title && <h3 className="text-sm font-semibold text-slate-100">{title}</h3>}
            {subtitle && <p className="mt-0.5 text-xs text-slate-400">{subtitle}</p>}
          </div>
          {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  );
}

const TONES = {
  default: 'text-slate-100 border-line',
  green: 'text-emerald-400 border-emerald-500/30',
  orange: 'text-orange-400 border-orange-500/30',
  red: 'text-red-400 border-red-500/40',
  blue: 'text-sky-400 border-sky-500/30',
};
export function StatCard({ label, value, hint, icon: Icon, tone = 'default' }) {
  const t = TONES[tone] || TONES.default;
  return (
    <div className={cx('rounded-xl border bg-navy-800 p-4', t.split(' ')[1])}>
      <div className="flex items-center justify-between">
        <span className="label-caps">{label}</span>
        {Icon && <Icon size={16} className={t.split(' ')[0]} />}
      </div>
      <div className={cx('mt-2 text-2xl font-bold tnum', t.split(' ')[0])}>{value ?? '—'}</div>
      {hint && <div className="mt-1 text-xs text-slate-400">{hint}</div>}
    </div>
  );
}

const BADGE = {
  default: 'bg-slate-700/50 text-slate-300 border-slate-600',
  green: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/40',
  orange: 'bg-orange-500/15 text-orange-300 border-orange-500/40',
  red: 'bg-red-500/15 text-red-300 border-red-500/40',
  blue: 'bg-sky-500/15 text-sky-300 border-sky-500/40',
  amber: 'bg-amber-500/15 text-amber-300 border-amber-500/40',
  purple: 'bg-violet-500/15 text-violet-300 border-violet-500/40',
};
export function Badge({ tone = 'default', children }) {
  return <span className={cx('inline-flex items-center whitespace-nowrap rounded-md border px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wide', BADGE[tone] || BADGE.default)}>{children}</span>;
}

const BTN = {
  primary: 'bg-orange-500 text-white hover:bg-orange-400 border-orange-500',
  secondary: 'bg-navy-700 text-slate-200 hover:bg-navy-600 border-line',
  danger: 'bg-red-600 text-white hover:bg-red-500 border-red-600',
  ghost: 'bg-transparent text-slate-300 hover:bg-navy-700 border-transparent',
};
export function Button({ variant = 'secondary', loading, className, children, disabled, type = 'button', ...props }) {
  return (
    <button
      type={type}
      disabled={disabled || loading}
      className={cx('inline-flex items-center justify-center gap-1.5 rounded-lg border px-3 py-1.5 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50', BTN[variant] || BTN.secondary, className)}
      {...props}
    >
      {loading && <Loader2 size={14} className="animate-spin" />}
      {children}
    </button>
  );
}

export function Spinner() {
  return <Loader2 size={18} className="animate-spin text-orange-400" />;
}
export function LoadingBlock({ label = 'Loading…' }) {
  return (
    <div className="flex items-center justify-center gap-2 py-10 text-sm text-slate-400">
      <Spinner /> {label}
    </div>
  );
}
export function ErrorBanner({ error, onRetry }) {
  if (!error) return null;
  return (
    <div className="flex items-start gap-3 rounded-lg border border-red-500/40 bg-red-500/10 p-3 text-sm text-red-200">
      <AlertTriangle size={18} className="mt-0.5 shrink-0 text-red-400" />
      <div className="min-w-0 flex-1">
        <div className="font-semibold">{error.message || String(error)}</div>
        {error.code && error.status ? <div className="text-xs text-red-300/80">{error.status} {error.code}</div> : null}
        {error.details?.length > 0 && (
          <ul className="mt-1 list-disc pl-4 text-xs">{error.details.map((d, i) => <li key={i}>{d}</li>)}</ul>
        )}
      </div>
      {onRetry && <Button variant="secondary" onClick={onRetry}><RefreshCw size={13} /> Retry</Button>}
    </div>
  );
}
export function EmptyState({ title = 'Nothing here', hint }) {
  return (
    <div className="flex flex-col items-center justify-center gap-1 py-10 text-center">
      <Inbox size={28} className="text-slate-500" />
      <div className="text-sm font-medium text-slate-300">{title}</div>
      {hint && <div className="text-xs text-slate-500">{hint}</div>}
    </div>
  );
}
export function PageHeader({ title, subtitle, actions, badge }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-3">
      <div>
        <div className="flex items-center gap-2">
          <h1 className="text-xl font-bold text-slate-50 sm:text-2xl">{title}</h1>
          {badge && <Badge tone="orange">{badge}</Badge>}
        </div>
        {subtitle && <p className="mt-1 max-w-3xl text-sm text-slate-400">{subtitle}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  );
}

export function Table({ columns = [], rows = [], rowKey, onRowClick, empty = 'No records', dense }) {
  const list = rows || [];
  const pad = dense ? 'px-3 py-1.5' : 'px-3 py-2.5';
  return (
    <div className="overflow-x-auto rounded-lg border border-line">
      <table className="min-w-full text-sm">
        <thead className="bg-navy-900/70">
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={cx('label-caps whitespace-nowrap px-3 py-2', c.align === 'right' ? 'text-right' : c.align === 'center' ? 'text-center' : 'text-left')}>{c.header}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-line">
          {list.length === 0 && (
            <tr><td colSpan={columns.length || 1}><EmptyState title={typeof empty === 'string' ? empty : 'No records'} /></td></tr>
          )}
          {list.map((row, i) => (
            <tr
              key={rowKey ? (typeof rowKey === 'function' ? rowKey(row, i) : row?.[rowKey]) : i}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              className={cx('hover:bg-navy-700/50', onRowClick && 'cursor-pointer')}
            >
              {columns.map((c) => (
                <td key={c.key} className={cx(pad, 'tnum text-slate-200', c.align === 'right' ? 'text-right' : c.align === 'center' ? 'text-center' : 'text-left', c.className)}>
                  {c.render ? c.render(row, i) : row?.[c.key] ?? '—'}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function Field({ label, hint, error, children }) {
  return (
    <label className="block">
      {label && <span className="label-caps mb-1 block">{label}</span>}
      {children}
      {hint && !error && <span className="mt-1 block text-xs text-slate-500">{hint}</span>}
      {error && <span className="mt-1 block text-xs text-red-400">{error}</span>}
    </label>
  );
}
const inputCls = 'w-full rounded-lg border border-line bg-navy-900 px-3 py-1.5 text-sm text-slate-100 placeholder-slate-500 focus:border-orange-500 focus:outline-none focus:ring-1 focus:ring-orange-500';
export function Input({ className, ...props }) {
  return <input className={cx(inputCls, className)} {...props} />;
}
export function Select({ options = [], className, ...props }) {
  return (
    <select className={cx(inputCls, className)} {...props}>
      {options.map((o) => <option key={String(o.value)} value={o.value}>{o.label}</option>)}
    </select>
  );
}
export function Textarea({ className, ...props }) {
  return <textarea className={cx(inputCls, className)} {...props} />;
}

export function Tabs({ tabs = [], value, onChange }) {
  return (
    <div className="flex flex-wrap gap-1 border-b border-line">
      {tabs.map((t) => (
        <button
          key={t.id}
          type="button"
          onClick={() => onChange?.(t.id)}
          className={cx('-mb-px border-b-2 px-3 py-2 text-sm font-medium', value === t.id ? 'border-orange-500 text-orange-400' : 'border-transparent text-slate-400 hover:text-slate-200')}
        >{t.label}</button>
      ))}
    </div>
  );
}

export function AlgorithmInfo({ name, complexity, execution, notes }) {
  return (
    <div className="rounded-lg border border-sky-500/30 bg-sky-500/5 p-3 text-xs">
      <div className="label-caps text-sky-400">Algorithm</div>
      <div className="mt-1 text-sm font-semibold text-slate-100">{name}</div>
      {complexity && <div className="mt-0.5 text-slate-300">{complexity}</div>}
      {notes && <div className="mt-1 text-slate-400">{notes}</div>}
      {execution && (
        <div className="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-slate-300">
          <span>Duration: <b className="tnum">{fmt.ms(execution.durationMs)}</b></span>
          <span>Input size: <b className="tnum">{fmt.num(execution.inputSize)}</b></span>
        </div>
      )}
    </div>
  );
}

const STATUS_TONE = {
  REPORTED: 'blue', ASSIGNED: 'purple', IN_PROGRESS: 'orange', RESOLVED: 'green', CANCELLED: 'default', ARCHIVED: 'default',
  AVAILABLE: 'green', BUSY: 'orange', OFF_DUTY: 'default',
  OPEN: 'green', BLOCKED: 'red',
  EXISTING: 'green', PROPOSED: 'blue', UNAVAILABLE: 'red',
};
export function StatusBadge({ status }) {
  if (!status) return <Badge>—</Badge>;
  return <Badge tone={STATUS_TONE[status] || 'default'}>{String(status).replace(/_/g, ' ')}</Badge>;
}
export function SeverityBadge({ value }) {
  const tone = value >= 5 ? 'red' : value >= 4 ? 'orange' : value >= 3 ? 'amber' : value >= 2 ? 'blue' : 'default';
  return <Badge tone={tone}>S{value ?? '?'}</Badge>;
}
export function PriorityPill({ score }) {
  const n = Number(score);
  const c = Number.isNaN(n) ? 'bg-slate-600' : n >= 75 ? 'bg-red-500' : n >= 50 ? 'bg-orange-500' : n >= 25 ? 'bg-amber-500 text-slate-900' : 'bg-emerald-500 text-slate-900';
  return <span className={cx('inline-block min-w-[3rem] rounded-full px-2 py-0.5 text-center text-xs font-bold tnum text-white', c)}>{Number.isNaN(n) ? '—' : n.toFixed(1)}</span>;
}
