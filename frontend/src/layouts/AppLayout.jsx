import { useEffect, useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { LayoutDashboard, Siren, ListOrdered, Users, Boxes, Network, Radar, Route, FlaskConical, Gauge, Menu, X, ShieldAlert } from 'lucide-react';
import { get } from '../api/client.js';

export const NAV = [
  { to: '/', label: 'Overview', icon: LayoutDashboard },
  { to: '/incidents', label: 'Incident Management', icon: Siren },
  { to: '/priority', label: 'Priority Engine', icon: ListOrdered },
  { to: '/teams', label: 'Team Allocation', icon: Users },
  { to: '/resources', label: 'Resource Distribution', icon: Boxes },
  { to: '/network', label: 'City Network', icon: Network },
  { to: '/coverage', label: 'Coverage Planner', icon: Radar },
  { to: '/routes', label: 'Route Optimization', icon: Route },
  { to: '/lab', label: 'Algorithm Lab', icon: FlaskConical },
  { to: '/performance', label: 'Performance Analytics', icon: Gauge },
];

function useHealth() {
  const [up, setUp] = useState(null);
  useEffect(() => {
    let alive = true;
    const check = () => get('/health').then((h) => alive && setUp(h?.status === 'UP')).catch(() => alive && setUp(false));
    check();
    const id = setInterval(check, 20000);
    return () => { alive = false; clearInterval(id); };
  }, []);
  return up;
}

export default function AppLayout({ children }) {
  const [open, setOpen] = useState(false);
  const up = useHealth();
  const loc = useLocation();
  useEffect(() => setOpen(false), [loc.pathname]);
  const current = NAV.find((n) => (n.to === '/' ? loc.pathname === '/' : loc.pathname.startsWith(n.to)));

  return (
    <div className="flex h-full bg-navy-900">
      {open && <div className="fixed inset-0 z-30 bg-black/60 lg:hidden" onClick={() => setOpen(false)} />}
      <aside className={`fixed inset-y-0 left-0 z-40 flex w-64 flex-col border-r border-line bg-navy-950 transition-transform lg:static lg:translate-x-0 ${open ? 'translate-x-0' : '-translate-x-full'}`}>
        <div className="flex items-center gap-2 px-4 py-4">
          <div className="rounded-lg bg-orange-500 p-1.5 text-white"><ShieldAlert size={20} /></div>
          <div>
            <div className="text-base font-bold tracking-wide text-slate-50">CityShield</div>
            <div className="label-caps !text-[10px]">Command centre</div>
          </div>
          <button className="ml-auto text-slate-400 lg:hidden" onClick={() => setOpen(false)} aria-label="Close menu"><X size={18} /></button>
        </div>
        <nav className="flex-1 space-y-0.5 overflow-y-auto px-2 pb-4">
          {NAV.map(({ to, label, icon: Icon }) => (
            <NavLink key={to} to={to} end={to === '/'}
              className={({ isActive }) => `flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition ${isActive ? 'bg-orange-500/15 text-orange-300' : 'text-slate-400 hover:bg-navy-700 hover:text-slate-100'}`}>
              <Icon size={17} /> {label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-line px-4 py-3 text-[11px] text-slate-500">B.Tech Advanced Algorithms project</div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <div className="bg-red-600/90 px-3 py-1.5 text-center text-xs font-bold uppercase tracking-wide text-white">
          ACADEMIC SIMULATION — synthetic data, not a live emergency dispatch service.
        </div>
        <header className="flex items-center gap-3 border-b border-line bg-navy-800 px-4 py-2.5">
          <button className="text-slate-300 lg:hidden" onClick={() => setOpen(true)} aria-label="Open menu"><Menu size={20} /></button>
          <h2 className="text-sm font-semibold text-slate-100">{current?.label || 'CityShield'}</h2>
          <div className="ml-auto flex items-center gap-2 text-xs">
            <span className={`h-2.5 w-2.5 rounded-full ${up === null ? 'bg-slate-500' : up ? 'bg-emerald-400' : 'bg-red-500'}`} />
            <span className="text-slate-300">Backend {up === null ? 'checking…' : up ? 'UP' : 'DOWN'}</span>
          </div>
        </header>
        <main className="flex-1 overflow-y-auto p-4 lg:p-6">{children ?? <Outlet />}</main>
      </div>
    </div>
  );
}
