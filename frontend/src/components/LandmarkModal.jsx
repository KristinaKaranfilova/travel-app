import { useEffect } from 'react'
import { createPortal } from 'react-dom'
import { CircleMarker, MapContainer, TileLayer, Tooltip, useMap } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import PlaceDetailsCard from './PlaceDetailsCard.jsx'

/** Прозорец со опис на знаменитоста и нејзината локација на мапа. */
export default function LandmarkModal({ landmark, onClose }) {
  useEffect(() => {
    const onKey = (e) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [onClose])

  const position = [landmark.lat, landmark.lon]

  // Portal во <body>: така position: fixed е секогаш во однос на прозорецот
  return createPortal(
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className="landmark-modal" role="dialog" aria-modal="true" aria-label={landmark.name}>
        <PlaceDetailsCard place={landmark} onClose={onClose} />
        {/* key: нова мапа за секое место, центрирана на него */}
        <MapContainer key={position.join(',')} center={position} zoom={16} className="landmark-map">
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
            url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
          />
          <CircleMarker center={position} radius={11} pathOptions={{ color: '#ffffff', weight: 3, fillColor: '#ff7a59', fillOpacity: 1 }}>
            <Tooltip permanent direction="top" offset={[0, -10]}>{landmark.name}</Tooltip>
          </CircleMarker>
          <KeepCentered position={position} />
        </MapContainer>
      </div>
    </div>,
    document.body,
  )
}

/**
 * Мапата ја памети големината од моментот на создавање (прозорецот се вчитува „lazy“ и се анимира).
 * По прикажувањето ја мериме повторно и ја центрираме на местото.
 */
function KeepCentered({ position }) {
  const map = useMap()
  const [lat, lon] = position
  useEffect(() => {
    const center = () => {
      map.invalidateSize({ pan: false })
      map.setView([lat, lon], map.getZoom(), { animate: false })
    }
    center()
    const observer = new ResizeObserver(center)
    observer.observe(map.getContainer())
    return () => observer.disconnect()
  }, [map, lat, lon])
  return null
}
