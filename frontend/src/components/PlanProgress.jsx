import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { getLandmarks } from '../api.js'

/** Чекорите на алгоритмот (backend: ItineraryService), како што корисникот ги гледа. */
const STEPS = [
  { icon: '🗺️', text: 'Ги собираме атракциите, рестораните и кафулињата' },
  { icon: '🌦️', text: 'Ја проверуваме временската прогноза' },
  { icon: '🧩', text: 'Ги групираме местата по денови' },
  { icon: '🚶', text: 'Ја бараме најкратката пешачка рута' },
  { icon: '🕘', text: 'Го составуваме распоредот по часови' },
]

/**
 * Екран додека се прави планот. Наместо само „Се вчитува...“, покажува:
 *  - вистински напредок за најбавниот чекор (местата од OpenStreetMap, преку placesReady)
 *  - слики од знаменитостите на градот, за корисникот да има што да гледа
 * Кога ќе стигне планот (done), сите чекори се штиклираат и се повикува onFinished.
 */
export default function PlanProgress({ city, placesReady, done, onFinished }) {
  const [step, setStep] = useState(0)
  const [slides, setSlides] = useState([])
  const [slide, setSlide] = useState(0)
  const [seconds, setSeconds] = useState(0)
  const finished = useRef(onFinished)
  finished.current = onFinished

  // Првиот чекор чека на местата; другите се брзи (милисекунди на серверот), па одат еден по друг
  useEffect(() => {
    if (done) return
    if (step === 0 && !placesReady) return
    if (step >= STEPS.length - 1) return
    const t = setTimeout(() => setStep((s) => s + 1), step === 0 ? 250 : 550)
    return () => clearTimeout(t)
  }, [step, placesReady, done])

  useEffect(() => {
    if (!done) return
    setStep(STEPS.length)
    const t = setTimeout(() => finished.current(), 650)
    return () => clearTimeout(t)
  }, [done])

  // Слики од знаменитостите (обично веќе се во кешот од галеријата)
  useEffect(() => {
    getLandmarks(city.lat, city.lon)
      .then((l) => {
        // Две знаменитости во Wikidata можат да ја делат истата слика: секоја слика само еднаш
        const seen = new Set()
        setSlides(l.filter((x) => x.imageUrl && !seen.has(x.imageUrl) && seen.add(x.imageUrl)))
      })
      .catch(() => {})
  }, [city.lat, city.lon])

  useEffect(() => {
    if (slides.length < 2) return
    const id = setInterval(() => setSlide((s) => (s + 1) % slides.length), 3200)
    return () => clearInterval(id)
  }, [slides.length])

  useEffect(() => {
    const id = setInterval(() => setSeconds((s) => s + 1), 1000)
    document.body.classList.add('no-scroll')
    return () => {
      clearInterval(id)
      document.body.classList.remove('no-scroll')
    }
  }, [])

  const progress = done ? 100 : Math.round(((step + (placesReady ? 0.5 : 0.15)) / STEPS.length) * 100)

  return createPortal(
    <div className="plan-progress" role="dialog" aria-modal="true" aria-label="Се прави планот">
      <div className="pp-slides" aria-hidden="true">
        {slides.map((s, i) => (
          <div key={s.imageUrl} className={`pp-slide ${i === slide ? 'active' : ''}`} style={{ backgroundImage: `url("${s.imageUrl}")` }} />
        ))}
      </div>

      <div className={`pp-card ${done ? 'done' : ''}`}>
        <div className="pp-orbit" aria-hidden="true">
          <span className="pp-globe">{done ? '🎉' : '🌍'}</span>
          <span className="pp-ring"><span className="pp-plane">✈</span></span>
        </div>

        <h2>{done ? 'Планот е подготвен!' : <>Го составуваме твојот план за <span className="gradient-text">{city.name}</span></>}</h2>

        <ol className="pp-steps">
          {STEPS.map((s, i) => {
            const state = i < step ? 'done' : i === step ? 'active' : 'todo'
            return (
              <li key={s.text} className={state}>
                <span className="pp-step-icon">{state === 'done' ? '✓' : s.icon}</span>
                <span>{s.text}</span>
              </li>
            )
          })}
        </ol>

        <div className="pp-bar"><span style={{ width: `${progress}%` }} /></div>

        <p className="pp-note">
          {!placesReady && seconds > 5 && !done
            ? `Прв пат планираме за ${city.name}: преземањето на местата трае до една минута (${seconds} s).`
            : slides[slide]
              ? <>📸 Додека чекаш: <strong>{slides[slide].name}</strong></>
              : 'Само момент…'}
        </p>
      </div>
    </div>,
    document.body,
  )
}
