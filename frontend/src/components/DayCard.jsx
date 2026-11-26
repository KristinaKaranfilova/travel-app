import WeatherBadge from './WeatherBadge.jsx'
import { Reveal } from './ui.jsx'
import { prefetchPlaceDetails } from '../api.js'
import { CATEGORY_ICONS, CATEGORY_LABELS, ITEM_LABELS, dayColor, formatDate, formatTime } from '../labels.js'

const FOOD_ICONS = { LUNCH: '🍽️', COFFEE: '☕', DINNER: '🍷' }

/** Картичка за еден ден: време, временска линија по часови и места „ако остане време“. */
export default function DayCard({ day, index = 0, selected, onSelect, focusedPlace, onPlaceClick }) {
  const color = dayColor(day.dayNumber)
  let visitNumber = 0

  return (
    <Reveal
      as="article"
      delay={Math.min(index, 3) * 90}
      className={`card day-card ${selected ? 'selected' : ''}`}
      style={{ '--day-color': color }}
    >
      <button type="button" className="day-header" onClick={onSelect}>
        <span className="day-number" aria-hidden="true">{String(day.dayNumber).padStart(2, '0')}</span>
        <div className="day-title">
          <span className="day-badge">Ден {day.dayNumber}</span>
          <span className="day-date">{formatDate(day.date)}</span>
        </div>
        <WeatherBadge weather={day.weather} />
      </button>

      <ol className="timeline">
        {day.items.map((item, i) => {
          const isVisit = item.type === 'VISIT'
          if (isVisit) visitNumber++
          // Атракциите се копчиња: клик го отвора описот и ја означува точката на мапата
          const Row = isVisit ? 'button' : 'div'
          const rowProps = isVisit
            ? {
                type: 'button',
                onClick: () => onPlaceClick({ ...item, dayNumber: day.dayNumber }),
                // Описот се бара однапред, па кликот е (речиси) инстант
                onMouseEnter: () => prefetchPlaceDetails({ name: item.name, lat: item.lat, lon: item.lon }),
              }
            : {}
          const focused = isVisit && focusedPlace?.lat === item.lat && focusedPlace?.lon === item.lon
          return (
            <li key={i} className={isVisit ? 'visit' : 'food'} style={{ '--i': i }}>
              {item.walkMinutes > 0 && <div className="walk"><span>🚶</span> {item.walkMinutes} мин пешачење</div>}
              <Row className={`item-row ${isVisit ? 'clickable' : ''} ${focused ? 'focused' : ''}`} {...rowProps}>
                <span className="time">
                  {formatTime(item.start)}
                  <small>{formatTime(item.end)}</small>
                </span>
                <span className="node">
                  {isVisit ? <span className="marker-number">{visitNumber}</span> : <span className="food-icon">{FOOD_ICONS[item.type]}</span>}
                </span>
                <span className="item-text">
                  <strong>{item.name}</strong>
                  <span className="item-meta">
                    {isVisit ? (
                      <>
                        {CATEGORY_ICONS[item.category]} {CATEGORY_LABELS[item.category]}
                        {item.indoor && <span className="tag">затворено</span>}
                      </>
                    ) : (
                      ITEM_LABELS[item.type]
                    )}
                  </span>
                  {item.openingHours && <span className="hours">🕘 {item.openingHours}</span>}
                </span>
                {isVisit && <span className="item-go" aria-hidden="true">→</span>}
              </Row>
            </li>
          )
        })}
      </ol>

      {day.items.length === 0 && <p className="muted day-empty">Нема доволно места за овој ден.</p>}

      {day.extras.length > 0 && (
        <div className="extras">
          <span className="extras-title"><span className="script">ако ти остане време</span></span>
          <div className="chips">
            {day.extras.map((e, i) => (
              <button
                key={i}
                type="button"
                className="chip chip-button"
                onClick={() => onPlaceClick({ ...e, dayNumber: day.dayNumber })}
                onMouseEnter={() => prefetchPlaceDetails({ name: e.name, lat: e.lat, lon: e.lon })}
              >
                {CATEGORY_ICONS[e.category]} {e.name}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="day-footer">
        <span>🚶 Вкупно пешачење: околу <strong>{day.walkingKm} km</strong></span>
        <span className="footer-dots" aria-hidden="true" />
      </div>
    </Reveal>
  )
}
