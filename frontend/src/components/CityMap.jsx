import { useEffect, useState } from 'react';
import { MapContainer, TileLayer, CircleMarker, Circle, Polyline, Tooltip, Popup, useMap } from 'react-leaflet';

export { Tooltip, Popup, Polyline, CircleMarker };

function TileWatcher({ onError }) {
  return (
    <TileLayer
      attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
      url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      eventHandlers={{ tileerror: onError }}
    />
  );
}

export default function CityMap({ height = 460, center = [19.076, 72.8777], zoom = 11, children, className = '' }) {
  const [tilesFailed, setTilesFailed] = useState(false);
  return (
    <div className={`relative overflow-hidden rounded-lg border border-line ${className}`} style={{ height }}>
      <MapContainer center={center} zoom={zoom} style={{ height: '100%', width: '100%' }} scrollWheelZoom>
        <TileWatcher onError={() => setTilesFailed(true)} />
        {children}
      </MapContainer>
      {tilesFailed && (
        <div className="pointer-events-none absolute bottom-2 left-2 z-[1000] rounded bg-navy-900/90 px-2 py-1 text-xs text-amber-300">
          Map tiles unavailable — showing data only
        </div>
      )}
    </div>
  );
}

export function ZoneDot({ position, radius = 7, color = '#38bdf8', label, children }) {
  if (!position || position[0] == null || position[1] == null) return null;
  return (
    <CircleMarker center={position} radius={radius} pathOptions={{ color, fillColor: color, fillOpacity: 0.7, weight: 1.5 }}>
      {label && <Tooltip>{label}</Tooltip>}
      {children && <Popup>{children}</Popup>}
    </CircleMarker>
  );
}

export function LinkLine({ positions, color = '#64748b', weight = 2, dashArray, label }) {
  if (!positions || positions.length < 2) return null;
  return (
    <Polyline positions={positions} pathOptions={{ color, weight, dashArray }}>
      {label && <Tooltip sticky>{label}</Tooltip>}
    </Polyline>
  );
}

export function RadiusCircle({ center, radiusKm = 1, color = '#22c55e', fillOpacity = 0.1 }) {
  if (!center || center[0] == null || center[1] == null) return null;
  return <Circle center={center} radius={radiusKm * 1000} pathOptions={{ color, fillColor: color, fillOpacity, weight: 1 }} />;
}

export function FitBounds({ points }) {
  const map = useMap();
  useEffect(() => {
    const pts = (points || []).filter((p) => p && p[0] != null && p[1] != null);
    if (pts.length > 0) map.fitBounds(pts, { padding: [30, 30], maxZoom: 14 });
  }, [map, JSON.stringify(points || [])]); // eslint-disable-line react-hooks/exhaustive-deps
  return null;
}
