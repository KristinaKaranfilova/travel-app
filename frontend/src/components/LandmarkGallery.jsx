import { Suspense, lazy, useCallback, useEffect, useState } from 'react'
import { getLandmarks } from '../api.js'
import { FadeImg } from './ui.jsx'

// Прозорецот има мапа (Leaflet), па се вчитува дури на прв клик
const LandmarkModal = lazy(() => import('./LandmarkModal.jsx'))

/**
 * Галерија со најпознатите знаменитости во градот (слики од Wikimedia Commons / Wikipedia).
 * Клик на знаменитост отвора краток опис и нејзината локација на мапа.
 * onFirstImage: го враќа URL-то на првата слика (за позадина на избраниот град).
 */
export default function LandmarkGallery({ lat, lon, cityName, onFirstImage }) {
  const [landmarks, setLandmarks] = useState(null)
  const [selected, setSelected] = useState(null)
  const closeModal = useCallback(() => setSelected(null), [])

  useEffect(() => {
    let cancelled = false
    setLandmarks(null)
    setSelected(null)
    getLandmarks(lat, lon)
      .then((result) => {
        if (cancelled) return
        setLandmarks(result)
        if (result.length > 0 && onFirstImage) onFirstImage(result[0].imageUrl)
      })
      .catch(() => !cancelled && setLandmarks([]))
    return () => {
      cancelled = true // корисникот избрал друг град пред да стигне одговорот
    }
    // onFirstImage не е во зависностите намерно: се повикува само при нов град
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [lat, lon])

  if (landmarks !== null && landmarks.length === 0) {
    return <p className="muted small">Нема пронајдени слики од знаменитости за {cityName}.</p>
  }

  return (
    <div className="landmarks">
      <div className="section-heading">
        <h3><span className="script accent">не пропуштај</span> Најпознати знаменитости</h3>
        <span className="muted small">
          {landmarks === null ? 'Ги бараме најпознатите места…' : 'Кликни на слика за опис и локација'}
        </span>
      </div>
      <div className="landmark-grid">
        {landmarks === null
          ? Array.from({ length: 8 }, (_, i) => <div key={i} className={`landmark-card skeleton ${i === 0 ? 'featured' : ''}`} />)
          : landmarks.map((l, i) => (
              <figure key={`${l.wikidataId || l.pageUrl}-${i}`} className={`landmark-card ${i === 0 ? 'featured' : ''}`} style={{ '--i': i }}>
                <button type="button" className="landmark-open" onClick={() => setSelected(l)}>
                  <FadeImg src={l.imageUrl} alt={l.name} loading="lazy" />
                  <figcaption>
                    <strong>{l.name}</strong>
                    {l.description && <span>{l.description}</span>}
                  </figcaption>
                </button>
                <a href={l.pageUrl} target="_blank" rel="noreferrer" className="landmark-source" title="Извор на сликата">
                  ⓘ
                </a>
              </figure>
            ))}
      </div>
      {selected && (
        <Suspense fallback={<div className="modal-backdrop"><span className="compass-loader" /></div>}>
          <LandmarkModal landmark={selected} onClose={closeModal} />
        </Suspense>
      )}
    </div>
  )
}
