import { useEffect, useState } from 'react'
import { getPlaceDetails } from '../api.js'
import { TextSkeleton } from './Skeletons.jsx'
import { FadeImg } from './ui.jsx'

/** Слика, краток опис и извор за избраното место. */
export default function PlaceDetailsCard({ place, subtitle, onClose }) {
  const [result, setResult] = useState({ key: null, details: null, failed: false })
  const [attempt, setAttempt] = useState(0) // „Обиди се повторно“ го зголемува и повторува барањето
  const key = `${place.wikidataId || place.name}|${place.lat}|${place.lon}|${attempt}`

  useEffect(() => {
    let cancelled = false
    getPlaceDetails(place)
      .then((details) => !cancelled && setResult({ key, details, failed: false }))
      .catch(() => !cancelled && setResult({ key, details: null, failed: true }))
    return () => {
      cancelled = true // корисникот кликнал на друго место пред да стигне одговорот
    }
    // key ги опфаќа сите податоци за местото
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  const loading = result.key !== key
  const details = loading ? null : result.details
  const image = place.imageUrl || details?.imageUrl

  return (
    <article className="place-details" key={place.name}>
      {image ? (
        <FadeImg src={image} alt={place.name} className="place-photo" />
      ) : (
        loading && <div className="skeleton place-photo" />
      )}
      <button type="button" className="place-close" onClick={onClose} aria-label="Затвори">×</button>
      <div className="place-details-body">
        <h3>{place.name}</h3>
        {subtitle && <span className="muted small">{subtitle}</span>}

        {loading && <TextSkeleton lines={4} />}
        {!loading && details?.found && (
          <>
            <p>{details.description}</p>
            <a href={details.sourceUrl} target="_blank" rel="noreferrer" className="small source-link">
              Прочитај повеќе на {details.sourceName}{details.language === 'en' ? ' (англиски)' : ''} ↗
            </a>
          </>
        )}
        {!loading && result.failed && (
          <p className="muted small">
            Описот моментално не може да се вчита.{' '}
            <button type="button" className="link-button" onClick={() => setAttempt((a) => a + 1)}>
              Обиди се повторно
            </button>
          </p>
        )}
        {!loading && !result.failed && !details?.found && (
          <p className="muted small">Нема проверен опис за ова место.</p>
        )}
      </div>
    </article>
  )
}
