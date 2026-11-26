import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import DestinationPicker from '../components/DestinationPicker.jsx'
import LandmarkGallery from '../components/LandmarkGallery.jsx'
import PlanProgress from '../components/PlanProgress.jsx'
import PlacesStatus from '../components/PlacesStatus.jsx'
import { Reveal, RotatingWord } from '../components/ui.jsx'
import { createTrip, getPlacesStatus, selectCity } from '../api.js'
import { useAuth } from '../auth.jsx'
import { INTEREST_OPTIONS, PACE_OPTIONS } from '../labels.js'

/** Датум како "YYYY-MM-DD" (формат за <input type="date">), daysFromToday дена од денес. */
function isoDate(daysFromToday) {
  const d = new Date()
  d.setDate(d.getDate() + daysFromToday)
  return d.toLocaleDateString('sv-SE') // sv-SE дава YYYY-MM-DD во локално време
}

const MAX_DAYS = 7
const HERO_CITIES = ['Рим', 'Охрид', 'Париз', 'Прага', 'Истанбул', 'Барселона', 'Дубровник']

/**
 * Дали местата за градот се подготвени. Додека се преземаат од OpenStreetMap,
 * прашува на секои 2 секунди. retryKey: промена го почнува повторно (копче „Обиди се пак“).
 */
function usePlacesStatus(cityId, retryKey) {
  const [status, setStatus] = useState(null)

  useEffect(() => {
    setStatus(null)
    if (!cityId) return
    let cancelled = false
    let timer
    const poll = async () => {
      try {
        const s = await getPlacesStatus(cityId)
        if (cancelled) return
        setStatus(s)
        if (!s.ready && s.loading) timer = setTimeout(poll, 2000)
      } catch (e) {
        if (!cancelled) setStatus({ ready: false, loading: false, error: e.message })
      }
    }
    poll()
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [cityId, retryKey])

  return status
}

export default function HomePage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [city, setCity] = useState(null)
  const [cover, setCover] = useState(null) // позадинска слика за избраниот град
  const [startDate, setStartDate] = useState(isoDate(1))
  const [endDate, setEndDate] = useState(isoDate(3))
  const [interests, setInterests] = useState(['CULTURE', 'HISTORY'])
  const [pace, setPace] = useState('NORMAL')
  const [creating, setCreating] = useState(false)
  const [createdTrip, setCreatedTrip] = useState(null)
  const [error, setError] = useState(null)
  const [retryKey, setRetryKey] = useState(0)

  const placesStatus = usePlacesStatus(city?.id, retryKey)

  function chooseCity(selected) {
    setCity(selected)
    setCover(selected?.imageUrl || null)
    setError(null)
    window.scrollTo({ top: 0, behavior: 'smooth' })
    // Пребараниот град веднаш се зачувува: backend-от тогаш почнува да ги презема местата,
    // додека корисникот ги избира датумите (наместо дури по клик на „Направи план“)
    if (selected && !selected.id) {
      selectCity(selected)
        .then((saved) =>
          setCity((current) => (current === selected ? { ...current, id: saved.id } : current)))
        .catch((e) => setError(e.message))
    }
  }

  function toggleInterest(interest) {
    setInterests((current) =>
      current.includes(interest) ? current.filter((i) => i !== interest) : [...current, interest],
    )
  }

  // Проверки пред праќање, за корисникот веднаш да ја види грешката
  const days = Math.round((new Date(endDate) - new Date(startDate)) / 86400000) + 1
  let validationError = null
  if (!city) validationError = 'Избери град.'
  else if (days < 1) validationError = 'Крајниот датум е пред почетниот.'
  else if (days > MAX_DAYS) validationError = `Најмногу ${MAX_DAYS} дена.`
  else if (interests.length === 0) validationError = 'Избери барем еден интерес.'

  async function handleSubmit(e) {
    e.preventDefault()
    if (validationError || creating) return
    setCreating(true)
    setCreatedTrip(null)
    setError(null)
    try {
      const cityId = city.id ?? (await selectCity(city)).id
      const trip = await createTrip({ cityId, startDate, endDate, interests, pace })
      setCreatedTrip(trip) // PlanProgress ја покажува завршната анимација, па навигира
    } catch (err) {
      setError(err.message)
      setCreating(false)
    }
  }

  function openTrip() {
    // Планот се праќа и во state, па страната со планот не мора повторно да го бара
    navigate(`/trip/${createdTrip.id}`, { state: { trip: createdTrip } })
  }

  const firstName = user?.fullName.split(' ')[0]

  // ---------- Чекор 1: избор на дестинација ----------
  if (!city) {
    return (
      <div className="home wide">
        <section className="home-hero">
          <div className="hero-copy">
            <span className="hero-kicker">Здраво{firstName ? `, ${firstName}` : ''}! 👋</span>
            <h1>
              Каде патуваш
              <br />
              <span className="gradient-text">следно?</span>
            </h1>
            <p className="hero-next">
              <span className="script">следна станица:</span> <RotatingWord words={HERO_CITIES} />
            </p>
            <p className="hero-sub">
              Пребарај кој било град или избери разгледница подолу. Ние ќе направиме план по денови, со
              најкратки пешачки рути, прилагоден на временската прогноза.
            </p>
          </div>
          <HeroIllustration />
        </section>
        <Reveal as="section" className="card glass-card">
          <DestinationPicker onSelect={chooseCity} />
        </Reveal>
      </div>
    )
  }

  // ---------- Чекор 2: знаменитости + детали за патувањето ----------
  return (
    <div className="home wide">
      <section className={`selected-destination ${cover ? 'with-image' : ''}`}>
        {cover && <div className="destination-cover" style={{ backgroundImage: `url("${cover}")` }} />}
        <div className="selected-destination-content">
          <span className="eyebrow">Твојата дестинација</span>
          <h1>{city.name}</h1>
          <p>{city.country}</p>
          {city.hasAssistant && <span className="pill light">✨ Достапен локален AI водич</span>}
        </div>
        <button type="button" className="ghost-button" onClick={() => chooseCity(null)}>
          ← Друга дестинација
        </button>
      </section>

      <Reveal as="section" className="card glass-card">
        <LandmarkGallery
          lat={city.lat}
          lon={city.lon}
          cityName={city.name}
          onFirstImage={(url) => setCover((current) => current || url)}
        />
      </Reveal>

      <Reveal as="form" className="boarding-pass" onSubmit={handleSubmit}>
        <div className="bp-header">
          <span className="bp-brand">🧭 Патоказ</span>
          <span className="bp-title">Карта за патување</span>
          <span className="bp-class">Класа: Истражувач ✦</span>
        </div>

        <div className="bp-route">
          <div className="bp-end">
            <small>Од</small>
            <strong>Дома</strong>
            <span>🏠</span>
          </div>
          <div className="bp-flight" aria-hidden="true">
            <span className="bp-dash" />
            <span className="bp-plane">✈</span>
          </div>
          <div className="bp-end to">
            <small>До</small>
            <strong>{city.name}</strong>
            <span>{city.country}</span>
          </div>
        </div>

        <PlacesStatus
          cityName={city.name}
          saving={!city.id && !error}
          status={placesStatus}
          onRetry={() => setRetryKey((k) => k + 1)}
        />

        <div className="bp-perforation" aria-hidden="true" />

        <div className="bp-body">
          <div className="form-step">
            <div className="step-label"><span className="step-number">1</span> Кога патуваш?</div>
            <div className="date-row">
              <label>
                <span className="field-label">Заминување</span>
                <input type="date" value={startDate} min={isoDate(0)} onChange={(e) => setStartDate(e.target.value)} />
              </label>
              <span className="date-arrow">→</span>
              <label>
                <span className="field-label">Враќање</span>
                <input type="date" value={endDate} min={startDate} onChange={(e) => setEndDate(e.target.value)} />
              </label>
              {days >= 1 && days <= MAX_DAYS && (
                <span key={days} className="pill days-pill">{days} {days === 1 ? 'ден' : 'дена'}</span>
              )}
            </div>
          </div>

          <div className="form-step">
            <div className="step-label"><span className="step-number">2</span> Што те интересира?</div>
            <div className="option-grid four">
              {INTEREST_OPTIONS.map((o) => (
                <button
                  key={o.value}
                  type="button"
                  className={`option-card ${interests.includes(o.value) ? 'active' : ''}`}
                  aria-pressed={interests.includes(o.value)}
                  onClick={() => toggleInterest(o.value)}
                >
                  <span className="option-check" aria-hidden="true">✓</span>
                  <span className="option-icon">{o.icon}</span>
                  <span className="option-title">{o.title}</span>
                  <span className="option-hint">{o.hint}</span>
                </button>
              ))}
            </div>
          </div>

          <div className="form-step">
            <div className="step-label"><span className="step-number">3</span> Со какво темпо?</div>
            <div className="option-grid three">
              {PACE_OPTIONS.map((o) => (
                <button
                  key={o.value}
                  type="button"
                  className={`option-card ${pace === o.value ? 'active' : ''}`}
                  aria-pressed={pace === o.value}
                  onClick={() => setPace(o.value)}
                >
                  <span className="option-check" aria-hidden="true">✓</span>
                  <span className="option-icon">{o.icon}</span>
                  <span className="option-title">{o.title}</span>
                  <span className="option-hint">{o.hint}</span>
                </button>
              ))}
            </div>
          </div>

          {validationError && <p className="error-text">{validationError}</p>}
          {error && <p className="error-text">{error}</p>}
        </div>

        <div className="bp-footer">
          <span className="barcode" aria-hidden="true" />
          <button type="submit" className="cta big" disabled={creating || validationError !== null}>
            Направи план за {city.name} <span className="cta-arrow">→</span>
          </button>
        </div>
      </Reveal>

      {creating && (
        <PlanProgress
          city={city}
          placesReady={placesStatus?.ready === true}
          done={createdTrip !== null}
          onFinished={openTrip}
        />
      )}
    </div>
  )
}

/** Декорација во насловот: пат на авион меѓу две игли и „налепници“ што лебдат. */
function HeroIllustration() {
  return (
    <div className="hero-illustration" aria-hidden="true">
      <svg viewBox="0 0 360 260" className="flight-svg">
        <path
          id="flight-path"
          d="M30 220 C 90 120, 160 230, 210 140 S 300 40, 330 50"
          fill="none"
          stroke="currentColor"
          strokeWidth="2.5"
          strokeDasharray="2 9"
          strokeLinecap="round"
        />
        <g className="pin pin-start"><circle cx="30" cy="220" r="9" /><circle cx="30" cy="220" r="3.5" fill="#fff" /></g>
        <g className="pin pin-end"><circle cx="330" cy="50" r="9" /><circle cx="330" cy="50" r="3.5" fill="#fff" /></g>
        <g className="plane">
          <text fontSize="26" textAnchor="middle" dominantBaseline="central">✈</text>
          <animateMotion dur="7s" repeatCount="indefinite" rotate="auto" keyPoints="0;1" keyTimes="0;1" calcMode="spline" keySplines="0.45 0 0.55 1">
            <mpath href="#flight-path" />
          </animateMotion>
        </g>
      </svg>
      <span className="sticker s1">🏛️</span>
      <span className="sticker s2">🍝</span>
      <span className="sticker s3">☀️</span>
      <span className="sticker s4">📸</span>
      <span className="postcard-mini">
        <span className="script">Поздрав од</span>
        <strong>секаде!</strong>
      </span>
    </div>
  )
}
