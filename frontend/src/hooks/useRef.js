import { get } from '../api/client.js';
import { useApi } from './useApi.js';

export const CATEGORIES = ['FIRE', 'MEDICAL', 'ROAD_ACCIDENT', 'FLOOD', 'BUILDING_COLLAPSE', 'UTILITY_FAILURE', 'PUBLIC_SAFETY', 'OTHER'];
export const INCIDENT_STATUSES = ['REPORTED', 'ASSIGNED', 'IN_PROGRESS', 'RESOLVED', 'CANCELLED', 'ARCHIVED'];
export const TEAM_TYPES = ['FIRE', 'MEDICAL', 'RESCUE', 'POLICE', 'HAZMAT', 'ENGINEERING'];
export const AVAILABILITIES = ['AVAILABLE', 'BUSY', 'OFF_DUTY'];
export const METRICS = ['PRIORITY', 'SEVERITY', 'AFFECTED', 'URGENCY'];

export const useZones = () => useApi(() => get('/zones'), []);
export const useResourceTypes = () => useApi(() => get('/resource-types'), []);
export const useCentres = () => useApi(() => get('/centres'), []);
export const useRoads = () => useApi(() => get('/roads'), []);
export const pretty = (s) => (s ? String(s).replace(/_/g, ' ') : '—');
export const opts = (arr, all) => [...(all ? [{ value: '', label: all }] : []), ...arr.map((v) => ({ value: v, label: pretty(v) }))];
