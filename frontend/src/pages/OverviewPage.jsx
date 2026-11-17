import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { ResponsiveContainer, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, LineChart, Line, PieChart, Pie, Cell, Legend } from 'recharts';
import { Activity, AlertOctagon, Flame, Users, Hospital, Radar, Network, Siren, RefreshCw, TriangleAlert, Building2 } from 'lucide-react';
import { Card, StatCard, Table, PageHeader, Button, LoadingBlock, ErrorBanner, EmptyState, StatusBadge, SeverityBadge, PriorityPill, fmt } from '../components/ui.jsx';
import { COLORS, tooltipStyle, axisProps, gridColor } from '../components/charts.jsx';
import { useApi } from '../hooks/useApi.js';
import { get } from '../api/client.js';
import { pretty } from '../hooks/useRef.js';

export default function OverviewPage() {
  const { data: d, loading, error, reload } = useApi(() => get('/dashboard/summary'), []);
  const [auto, setAuto] = useState(false);
  useEffect(() => { if (!auto) return undefined; const id = setInterval(reload, 15000); return () => clearInterval(id); }, [auto, reload]);

  const actions = (
    <>
      <label className="flex items-center gap-1.5 text-xs text-slate-300"><input type="checkbox" checked={auto} onChange={(e) => setAuto(e.target.checked)} /> Auto-refresh (15s)</label>
      <Button onClick={reload} loading={loading}><RefreshCw size={14} /> Refresh</Button>
    </>
  );

  const header = <PageHeader title="City Command Overview" subtitle="Live state of the simulated city, aggregated by the backend." actions={actions} />;
  if (loading && !d) return <div>{header}<LoadingBlock label="Loading dashboard…" /></div>;
  if (error && !d) return <div>{header}<ErrorBanner error={error} onRetry={reload} /></div>;
  if (!d) return <div>{header}<EmptyState title="No dashboard data" /></div>;

  const t = d.teams || {}; const h = d.hospitals || {}; const c = d.centres || {}; const cn = d.connectivity || {}; const cov = d.coverage || {};
  const pri = d.priorityDistribution || [];
  const hasPri = pri.some((p) => p.count > 0);

  return (
    <div className="space-y-4">
      {header}
      <ErrorBanner error={error} onRetry={reload} />
      <div className="flex items-start gap-3 rounded-xl border-2 border-red-500/60 bg-red-500/10 p-4">
        <TriangleAlert className="mt-0.5 shrink-0 text-red-400" />
        <div className="text-sm text-red-100">
          <div className="font-bold uppercase tracking-wide">Academic simulation</div>
          CityShield is a B.Tech Advanced Algorithms project. All incidents, teams, hospitals and resources are synthetic. It must not be used for real emergency dispatch, and routing distances are estimates, not navigation.
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3 md:grid-cols-4">
        <StatCard label="Total incidents" value={fmt.num(d.totalIncidents)} icon={Siren} />
        <StatCard label="Active emergencies" value={fmt.num(d.activeIncidents)} icon={Activity} tone="orange" />
        <StatCard label="Critical (S5)" value={fmt.num(d.criticalIncidents)} icon={Flame} tone="red" hint="Active, severity 5" />
        <StatCard label="High-risk (P90)" value={fmt.num(d.highRiskIncidents)} icon={AlertOctagon} tone="red" hint={`Priority >= ${fmt.num(d.highRiskThreshold, 2)}`} />
        <StatCard label="Teams available" value={`${fmt.num(t.available)} / ${fmt.num(t.total)}`} icon={Users} tone="green" hint={`${fmt.num(t.busy)} busy, ${fmt.num(t.offDuty)} off duty`} />
        <StatCard label="Hospital beds" value={fmt.pct(h.occupancyPct)} icon={Hospital} tone={h.occupancyPct >= 85 ? 'red' : h.occupancyPct >= 70 ? 'orange' : 'green'} hint={`${fmt.num(h.availableBeds)} free of ${fmt.num(h.totalBeds)} beds, ${fmt.num(h.count)} hospitals`} />
        <StatCard label="Coverage" value={fmt.pct(cov.coveragePct)} icon={Radar} tone="blue" hint={`${fmt.num(cov.coveredZones)} of ${fmt.num(cov.totalZones)} zones within ${fmt.num(cov.radiusKm, 1)} km`} />
        <StatCard label="Emergency centres" value={fmt.num(c.existing)} icon={Building2} tone="green" hint={`${fmt.num(c.proposed)} proposed, ${fmt.num(c.unavailable)} unavailable`} />
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatCard label="Connected components" value={fmt.num(cn.componentCount)} icon={Network} tone={cn.componentCount > 1 ? 'orange' : 'green'} hint={`${fmt.num(d.zones)} zones`} />
        <StatCard label="Isolated zones" value={fmt.num(cn.isolatedZones)} tone={cn.isolatedZones > 0 ? 'red' : 'green'} />
        <StatCard label="Blocked roads" value={fmt.num(cn.blockedRoads)} tone={cn.blockedRoads > 0 ? 'orange' : 'green'} hint={<Link to="/network" className="text-sky-400 hover:underline">Open city network</Link>} />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Resource inventory utilisation" subtitle="Available stock as % of capacity (Segment Tree totals)">
          {(d.inventory || []).length === 0 ? <EmptyState title="No inventory" /> : (
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={d.inventory} layout="vertical" margin={{ left: 20 }}>
                <CartesianGrid stroke={gridColor} horizontal={false} />
                <XAxis type="number" domain={[0, 100]} unit="%" {...axisProps} />
                <YAxis type="category" dataKey="name" width={110} {...axisProps} />
                <Tooltip {...tooltipStyle} formatter={(v, n, p) => [`${fmt.num(v, 1)}% (${fmt.num(p.payload.available)}/${fmt.num(p.payload.capacity)} ${p.payload.unit || ''})`, 'Utilisation']} />
                <Bar dataKey="utilizationPct" fill="#f97316" radius={[0, 4, 4, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Card>
        <Card title="Priority distribution" subtitle="Active incidents by priority score band">
          {!hasPri ? <EmptyState title="No active incidents to score" /> : (
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={pri}>
                <CartesianGrid stroke={gridColor} vertical={false} />
                <XAxis dataKey="label" {...axisProps} /><YAxis allowDecimals={false} {...axisProps} />
                <Tooltip {...tooltipStyle} />
                <Bar dataKey="count" name="Incidents" radius={[4, 4, 0, 0]}>
                  {pri.map((_, i) => <Cell key={i} fill={['#22c55e', '#84cc16', '#f59e0b', '#f97316', '#ef4444'][Math.min(i, 4)]} />)}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          )}
        </Card>
        <Card title="Incidents by category">
          {(d.categoryBreakdown || []).length === 0 ? <EmptyState /> : (
            <ResponsiveContainer width="100%" height={260}>
              <PieChart>
                <Pie data={d.categoryBreakdown.map((x) => ({ name: pretty(x.category), value: x.count }))} dataKey="value" nameKey="name" innerRadius={50} outerRadius={90} paddingAngle={2}>
                  {d.categoryBreakdown.map((_, i) => <Cell key={i} fill={COLORS[i % COLORS.length]} stroke="none" />)}
                </Pie>
                <Tooltip {...tooltipStyle} /><Legend wrapperStyle={{ fontSize: 11 }} />
              </PieChart>
            </ResponsiveContainer>
          )}
        </Card>
        <Card title="Incidents by status">
          {(d.statusBreakdown || []).length === 0 ? <EmptyState /> : (
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={d.statusBreakdown.map((x) => ({ name: pretty(x.status), count: x.count }))}>
                <CartesianGrid stroke={gridColor} vertical={false} />
                <XAxis dataKey="name" {...axisProps} interval={0} fontSize={10} /><YAxis allowDecimals={false} {...axisProps} />
                <Tooltip {...tooltipStyle} /><Bar dataKey="count" name="Incidents" fill="#38bdf8" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </Card>
      </div>
      <Card title="Incidents per day" subtitle="Last 14 days">
        {(d.incidentsPerDay || []).length === 0 ? <EmptyState /> : (
          <ResponsiveContainer width="100%" height={220}>
            <LineChart data={d.incidentsPerDay}>
              <CartesianGrid stroke={gridColor} vertical={false} />
              <XAxis dataKey="date" {...axisProps} /><YAxis allowDecimals={false} {...axisProps} />
              <Tooltip {...tooltipStyle} />
              <Line type="monotone" dataKey="count" name="Incidents" stroke="#f97316" strokeWidth={2} dot={{ r: 3 }} />
            </LineChart>
          </ResponsiveContainer>
        )}
      </Card>
      <Card title="Recent incidents" actions={<Link to="/incidents" className="text-xs text-sky-400 hover:underline">All incidents</Link>}>
        <Table rows={d.recentIncidents} rowKey="id" empty="No incidents yet" columns={[
          { key: 'code', header: 'Code' },
          { key: 'priorityScore', header: 'Priority', render: (r) => <PriorityPill score={r.priorityScore} /> },
          { key: 'severity', header: 'Sev', render: (r) => <SeverityBadge value={r.severity} /> },
          { key: 'category', header: 'Category', render: (r) => pretty(r.category) },
          { key: 'zoneName', header: 'Zone' },
          { key: 'status', header: 'Status', render: (r) => <StatusBadge status={r.status} /> },
          { key: 'reportedAt', header: 'Reported', render: (r) => fmt.dt(r.reportedAt) },
        ]} />
      </Card>
    </div>
  );
}
