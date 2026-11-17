import { Routes, Route } from 'react-router-dom';
import AppLayout from './layouts/AppLayout.jsx';
import OverviewPage from './pages/OverviewPage.jsx';
import IncidentsPage from './pages/IncidentsPage.jsx';
import PriorityPage from './pages/PriorityPage.jsx';
import TeamsPage from './pages/TeamsPage.jsx';
import ResourcesPage from './pages/ResourcesPage.jsx';
import NetworkPage from './pages/NetworkPage.jsx';
import CoveragePage from './pages/CoveragePage.jsx';
import RoutesPage from './pages/RoutesPage.jsx';
import AlgorithmLabPage from './pages/AlgorithmLabPage.jsx';
import PerformancePage from './pages/PerformancePage.jsx';

export default function App() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route path="/" element={<OverviewPage />} />
        <Route path="/incidents" element={<IncidentsPage />} />
        <Route path="/priority" element={<PriorityPage />} />
        <Route path="/teams" element={<TeamsPage />} />
        <Route path="/resources" element={<ResourcesPage />} />
        <Route path="/network" element={<NetworkPage />} />
        <Route path="/coverage" element={<CoveragePage />} />
        <Route path="/routes" element={<RoutesPage />} />
        <Route path="/lab" element={<AlgorithmLabPage />} />
        <Route path="/performance" element={<PerformancePage />} />
        <Route path="*" element={<OverviewPage />} />
      </Route>
    </Routes>
  );
}
