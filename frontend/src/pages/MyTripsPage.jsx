import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { deleteTrip, getMyTrips } from '../api.js'
import { TripsSkeleton } from '../components/Skeletons.jsx'
import { FadeImg, Reveal } from '../components/ui.jsx'
import { PACE_OPTIONS, formatDate } from '../labels.js'

/** Листа на зачуваните патувања на корисникот, како разгледници. */
export default function MyTripsPage() {
  const [trips, setTrips] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    getMyTrips().then(setTrips).catch((e) => setError(e.message))
  }, [])

  async function handleDelete(id) {
    if (!window.confirm('Да се избрише ова патување? Ова не може да се врати.')) return
    try {
      await deleteTrip(id)
      setTrips((current) => current.filter((t) => t.id !== id))
    } catch (e) {
      setError(e.message)
    }
  }

  if (error) return <p className="error-text">{error}</p>
  if (!trips) return <TripsSkeleton />

  const today = new Date().toLocaleDateString('sv-SE')

  return (
    <div>
      <div className="page-title">
        <div>
          <span className="script accent">твојот дневник</span>
          <h1>Мои патувања</h1>
        </div>
        <Link to="/" className="cta button-link">+ Ново патување</Link>
      </div>

      {trips.length === 0 ? (
        <div className="card empty glass-card">
          <span className="empty-icon bob">🧳</span>
          <h2>Куферот е спакуван, само дестинација фали</h2>
          <p className="muted">Избери дестинација и направи го својот прв план.</p>
          <Link to="/" className="cta button-link">Започни →</Link>
        </div>
      ) : (
        <div className="trip-grid">
          {trips.map((t, i) => {
            const days = Math.round((new Date(t.endDate) - new Date(t.startDate)) / 86400000) + 1
            const pace = PACE_OPTIONS.find((p) => p.value === t.pace)
            const upcoming = t.endDate >= today
            return (
              <Reveal as="article" key={t.id} delay={Math.min(i, 6) * 70} className="trip-card">
                <Link to={`/trip/${t.id}`} className="trip-card-link">
                  <span className="trip-card-photo">
                    {t.imageUrl ? <FadeImg src={t.imageUrl} alt="" loading="lazy" /> : <span className="trip-card-initial">{t.cityName.charAt(0)}</span>}
                    <span className={`trip-card-badge ${upcoming ? 'upcoming' : ''}`}>{upcoming ? '✈ Претстои' : '✓ Поминато'}</span>
                  </span>
                  <span className="trip-card-body">
                    <span className="trip-card-city">{t.cityName}</span>
                    <span className="muted small">{t.country}</span>
                    <span className="trip-card-dates">📅 {formatDate(t.startDate)} – {formatDate(t.endDate)}</span>
                    <span className="chips">
                      <span className="chip">{days} {days === 1 ? 'ден' : 'дена'}</span>
                      {pace && <span className="chip">{pace.icon} {pace.title}</span>}
                    </span>
                  </span>
                </Link>
                <button type="button" className="link-button danger small trip-delete" onClick={() => handleDelete(t.id)}>
                  Избриши
                </button>
              </Reveal>
            )
          })}
        </div>
      )}
    </div>
  )
}
