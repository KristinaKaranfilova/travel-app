import { useEffect, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { getLandmarks, getTrip } from '../api.js'
import { useAuth } from '../auth.jsx'
import ChatPanel, { CHAT_EXAMPLES } from '../components/ChatPanel.jsx'
import DayCard from '../components/DayCard.jsx'
import PlaceDetailsCard from '../components/PlaceDetailsCard.jsx'
import { TripPageSkeleton } from '../components/Skeletons.jsx'
import TripMap from '../components/TripMap.jsx'
import { CountUp, Reveal } from '../components/ui.jsx'
import { CATEGORY_LABELS, dayColor, formatDate } from '../labels.js'

/** Приказ на генериран план: банер горе, водич, денови лево, мапа десно. */
export default function TripPage() {
  const { id } = useParams()
  const location = useLocation()
  const { user } = useAuth()
  // Штом е направен план, HomePage го праќа во state: нема потреба повторно да се бара
  const passed = location.state?.trip?.id === id ? location.state.trip : null
  const [trip, setTrip] = useState(passed)
  const [error, setError] = useState(null)
  const [cover, setCover] = useState(null)
  const [selectedDay, setSelectedDay] = useState(null) // null = сите денови на мапата
  const [focusedPlace, setFocusedPlace] = useState(null) // атракцијата на која е кликнато
  const [copied, setCopied] = useState(false)
  const [chatOpen, setChatOpen] = useState(false)
  const [chatQuestion, setChatQuestion] = useState(null)

  useEffect(() => {
    if (trip?.id === id) return
    getTrip(id).then(setTrip).catch((e) => setError(e.message))
  }, [id, trip?.id])

  // Насловна слика: првата знаменитост (обично веќе во кешот)
  const cityLat = trip?.plan.cityLat
  const cityLon = trip?.plan.cityLon
  useEffect(() => {
    if (cityLat == null) return
    getLandmarks(cityLat, cityLon).then((l) => setCover(l[0]?.imageUrl || null)).catch(() => {})
  }, [cityLat, cityLon])

  if (error) {
    return (
      <section className="card empty">
        <span className="empty-icon">🧭</span>
        <h2>Овој план не може да се најде</h2>
        <p className="error-text">{error}</p>
        <Link to="/" className="button-link primary">← Ново патување</Link>
      </section>
    )
  }
  if (!trip) return <TripPageSkeleton />

  const { plan } = trip
  const visits = plan.days.reduce((n, d) => n + d.items.filter((i) => i.type === 'VISIT').length, 0)
  const walkingKm = plan.days.reduce((km, d) => km + d.walkingKm, 0)
  const rainyDays = plan.days.filter((d) => d.weather && d.weather.precipitationProbability >= 50).length

  function toggleDay(dayNumber) {
    setFocusedPlace(null)
    setSelectedDay((current) => (current === dayNumber ? null : dayNumber))
  }

  function showAllDays() {
    setFocusedPlace(null)
    setSelectedDay(null)
  }

  async function copyLink() {
    await navigator.clipboard.writeText(window.location.href)
    setCopied(true)
    setTimeout(() => setCopied(false), 2500)
  }

  function ask(question) {
    setChatQuestion(question ? { text: question, at: Date.now() } : null)
    setChatOpen(true)
  }

  return (
    <div className="trip-page">
      <section className={`trip-hero ${cover ? 'with-image' : ''}`}>
        {cover && <div className="trip-hero-cover" style={{ backgroundImage: `url("${cover}")` }} />}
        <div className="trip-hero-main">
          <span className="eyebrow">Твојот план за</span>
          <h1>{plan.cityName}</h1>
          <p className="trip-dates">
            📅 {formatDate(plan.startDate)} – {formatDate(plan.endDate)}
          </p>
        </div>
        <div className="trip-hero-side">
          <div className="stats">
            <div className="stat"><strong><CountUp value={plan.days.length} /></strong><span>{plan.days.length === 1 ? 'ден' : 'дена'}</span></div>
            <div className="stat"><strong><CountUp value={visits} /></strong><span>места</span></div>
            <div className="stat"><strong><CountUp value={walkingKm} decimals={1} /></strong><span>km пешки</span></div>
            {rainyDays > 0 && <div className="stat"><strong><CountUp value={rainyDays} /></strong><span>дожд. ден</span></div>}
          </div>
          <button type="button" className="ghost-button" onClick={copyLink}>
            {copied ? '✓ Линкот е копиран' : '🔗 Сподели го планот'}
          </button>
        </div>
        <span className="hero-stamp" aria-hidden="true">
          <span>{plan.days.length}</span>
          <small>{plan.days.length === 1 ? 'ден' : 'дена'}</small>
        </span>
      </section>

      <Reveal as="section" className="guide-banner">
        <div className="guide-avatar" aria-hidden="true">
          <span>🧭</span>
          <i /><i />
        </div>
        <div className="guide-copy">
          <span className="script">твојот локален водич</span>
          <h2>Имаш прашање за {plan.cityName}?</h2>
          <p>Прашај за храна, превоз, безбедност или скриени места. Одговорите се од Wikivoyage, со извори.</p>
        </div>
        <div className="guide-actions">
          {user ? (
            <>
              <div className="guide-chips">
                {CHAT_EXAMPLES.map((q) => (
                  <button key={q} type="button" className="suggestion" onClick={() => ask(q)}>{q}</button>
                ))}
              </div>
              <button type="button" className="cta" onClick={() => ask(null)}>✨ Разговарај со водичот</button>
            </>
          ) : (
            <Link to="/login" state={{ from: `/trip/${id}` }} className="cta button-link">
              Најави се за водичот →
            </Link>
          )}
        </div>
      </Reveal>

      <div className="day-tabs" role="tablist">
        <button
          type="button"
          className={`day-tab ${selectedDay === null ? 'active' : ''}`}
          onClick={showAllDays}
        >
          🗺️ Сите денови
        </button>
        {plan.days.map((d) => (
          <button
            key={d.dayNumber}
            type="button"
            className={`day-tab ${selectedDay === d.dayNumber ? 'active' : ''}`}
            style={{ '--day-color': dayColor(d.dayNumber) }}
            onClick={() => toggleDay(d.dayNumber)}
          >
            <span className="dot" /> Ден {d.dayNumber}
          </button>
        ))}
      </div>

      <div className="trip-layout">
        <div className="days">
          {plan.days
            .filter((d) => selectedDay === null || d.dayNumber === selectedDay)
            .map((day, i) => (
              <DayCard
                key={day.dayNumber}
                index={i}
                day={day}
                selected={selectedDay === day.dayNumber}
                onSelect={() => toggleDay(day.dayNumber)}
                focusedPlace={focusedPlace}
                onPlaceClick={setFocusedPlace}
              />
            ))}
        </div>
        <div className="map-column">
          <div className="map-frame">
            <TripMap plan={plan} selectedDay={selectedDay} focusedPlace={focusedPlace} onPlaceClick={setFocusedPlace} />
            {focusedPlace && (
              <div className="map-details">
                <PlaceDetailsCard
                  place={{ name: focusedPlace.name, lat: focusedPlace.lat, lon: focusedPlace.lon }}
                  subtitle={`Ден ${focusedPlace.dayNumber} · ${CATEGORY_LABELS[focusedPlace.category] || ''}`}
                  onClose={() => setFocusedPlace(null)}
                />
              </div>
            )}
          </div>
          <p className="muted small map-hint">👆 Кликни на атракција за опис и локација на мапата.</p>
        </div>
      </div>

      {/* Чет-ботот бара најава (го троши Gemini лимитот); споделениот линк е јавен */}
      {user && (
        <ChatPanel
          cityId={plan.cityId}
          cityName={plan.cityName}
          open={chatOpen}
          onOpen={() => ask(null)}
          onClose={() => setChatOpen(false)}
          question={chatQuestion}
        />
      )}
    </div>
  )
}
