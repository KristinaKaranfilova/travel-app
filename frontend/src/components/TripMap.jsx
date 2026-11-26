import { Fragment, useEffect } from 'react'
import { CircleMarker, MapContainer, Polyline, Popup, TileLayer, Tooltip, useMap } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import { ITEM_LABELS, dayColor, formatTime } from '../labels.js'

/**
 * Мапа (Leaflet + OpenStreetMap) со местата и рутата за секој ден.
 * Секој ден има своја боја. Атракциите се нумерирани кругови,
 * а рестораните/кафулињата се мали бели кругови.
 * Клик на атракција го отвора описот; избраното место е означено со портокалов прстен.
 */
export default function TripMap({ plan, selectedDay, focusedPlace, onPlaceClick }) {
  const visibleDays = plan.days.filter((d) => selectedDay === null || d.dayNumber === selectedDay)
  const points = visibleDays.flatMap((d) => d.items.map((i) => [i.lat, i.lon]))

  return (
    <MapContainer center={[plan.cityLat, plan.cityLon]} zoom={14} className="trip-map">
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://tile.openstreetmap.org/{z}/{x}/{y}.png"
        className="map-tiles"
      />
      <FitToPoints points={points} />
      <FlyToPlace place={focusedPlace} />

      {visibleDays.map((day) => {
        const color = dayColor(day.dayNumber)
        let visitNumber = 0
        return (
          <Fragment key={day.dayNumber}>
            {/* Рутата: линија низ сите ставки по редослед */}
            {/* Сенка + испрекината линија што „патува“ (CSS анимација на класата route-line) */}
            <Polyline positions={day.items.map((i) => [i.lat, i.lon])} pathOptions={{ color, weight: 7, opacity: 0.18 }} />
            <Polyline
              positions={day.items.map((i) => [i.lat, i.lon])}
              pathOptions={{ color, weight: 3, opacity: 0.95, dashArray: '8 10', className: 'route-line' }}
            />

            {day.items.map((item, index) => {
              const isVisit = item.type === 'VISIT'
              if (isVisit) visitNumber++
              return (
                <CircleMarker
                  key={index}
                  center={[item.lat, item.lon]}
                  radius={isVisit ? 12 : 6}
                  pathOptions={{
                    color: isVisit ? '#ffffff' : color,
                    weight: isVisit ? 3 : 2,
                    fillColor: isVisit ? color : '#ffffff',
                    fillOpacity: 1,
                    className: isVisit ? 'map-marker' : 'map-food',
                  }}
                  eventHandlers={isVisit ? { click: () => onPlaceClick({ ...item, dayNumber: day.dayNumber }) } : undefined}
                >
                  {/* Leaflet дозволува само една ознака (tooltip) по точка: бројот */}
                  {isVisit ? (
                    <Tooltip permanent direction="center" className="map-number">
                      {visitNumber}
                    </Tooltip>
                  ) : (
                    <Popup>
                      <strong>{item.name}</strong>
                      <br />
                      Ден {day.dayNumber}, {formatTime(item.start)}–{formatTime(item.end)} · {ITEM_LABELS[item.type]}
                    </Popup>
                  )}
                </CircleMarker>
              )
            })}
          </Fragment>
        )
      })}

      {focusedPlace && (
        <CircleMarker
          key={`${focusedPlace.lat},${focusedPlace.lon}`}
          center={[focusedPlace.lat, focusedPlace.lon]}
          radius={19}
          interactive={false}
          pathOptions={{ color: '#ff7a59', weight: 4, fillOpacity: 0.12, className: 'focus-ring' }}
        >
          <Tooltip permanent direction="top" offset={[0, -18]} className="focus-label">
            {focusedPlace.name}
          </Tooltip>
        </CircleMarker>
      )}
    </MapContainer>
  )
}

/**
 * Ја зумира мапата така што сите дадени точки се видливи.
 * Ако големината на мапата се смени (на пр. страната се појавува со анимација, па Leaflet
 * првиот пат измерил погрешна ширина), мапата повторно се мери и се центрира.
 */
function FitToPoints({ points }) {
  const map = useMap()
  const key = JSON.stringify(points)

  useEffect(() => {
    const fit = () => {
      // Leaflet ја памети големината од моментот на создавање; ако распоредот потоа се сменил
      // (lazy страна, анимации), прво повторно ја мериме, па дури тогаш зумираме
      map.invalidateSize({ pan: false })
      if (points.length > 0) map.fitBounds(points, { padding: [30, 30], maxZoom: 16 })
    }
    fit()

    const container = map.getContainer()
    let last = `${container.clientWidth}x${container.clientHeight}`
    const observer = new ResizeObserver(() => {
      const size = `${container.clientWidth}x${container.clientHeight}`
      if (size === last) return
      last = size
      map.invalidateSize()
      fit()
    })
    observer.observe(container)
    return () => observer.disconnect()
    // key ги содржи сите точки, па ефектот се извршува само кога тие ќе се променат
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, key])

  return null
}

/** Ја поместува мапата до избраното место. */
function FlyToPlace({ place }) {
  const map = useMap()
  const lat = place?.lat
  const lon = place?.lon

  useEffect(() => {
    if (lat !== undefined && lon !== undefined) {
      const zoom = Math.max(map.getZoom(), 16)
      // На десктоп описот е преку десната страна од мапата, па местото го држиме лево од него
      const cardOverlapsMap = window.matchMedia('(min-width: 801px)').matches
      const offsetX = cardOverlapsMap ? Math.min(175, map.getSize().x / 4) : 0
      const center = map.unproject(map.project([lat, lon], zoom).add([offsetX, 0]), zoom)
      map.flyTo(center, zoom, { duration: 0.6 })
    }
  }, [map, lat, lon])

  return null
}
