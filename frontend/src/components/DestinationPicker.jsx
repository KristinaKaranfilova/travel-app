import { useEffect, useState } from 'react'
import { getFeaturedDestinations, prefetchLandmarks, searchCities } from '../api.js'
import CitySearch from './CitySearch.jsx'
import { FadeImg } from './ui.jsx'
import { useTilt } from '../motion.js'

/**
 * Избор на дестинација: пребарување + мрежа од популарни дестинации како разгледници.
 * onSelect добива објект со барем {name, country, lat, lon}; ако градот е во базата, има и id.
 */
export default function DestinationPicker({ onSelect }) {
  const [featured, setFeatured] = useState(null)
  const [resolving, setResolving] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    getFeaturedDestinations().then(setFeatured).catch(() => setFeatured([]))
  }, [])

  async function pick(d) {
    setError(null)
    if (d.cityId) {
      onSelect({ id: d.cityId, name: d.name, country: d.country, lat: d.lat, lon: d.lon,
        imageUrl: d.imageUrl, hasAssistant: d.hasAssistant })
      return
    }
    // Градот уште не е во базата: прво го наоѓаме преку пребарувањето
    setResolving(d.name)
    try {
      const found = await searchCities(d.name)
      if (found.length === 0) throw new Error('Градот не е пронајден')
      onSelect({ ...found[0], imageUrl: d.imageUrl })
    } catch (e) {
      setError(e.message)
    } finally {
      setResolving(null)
    }
  }

  return (
    <div className="destination-picker">
      <CitySearch onSelect={onSelect} />

      <div className="section-heading">
        <h3><span className="script accent">разгледници</span> Популарни дестинации</h3>
        <span className="muted small">✨ = достапен локален AI водич</span>
      </div>
      {error && <p className="error-text small">{error}</p>}

      <div className="destination-grid">
        {featured === null
          ? Array.from({ length: 8 }, (_, i) => <div key={i} className="destination-card skeleton" />)
          : featured.map((d, i) => (
              <Postcard
                key={d.name}
                destination={d}
                index={i}
                resolving={resolving}
                onPick={() => pick(d)}
              />
            ))}
      </div>
    </div>
  )
}

function Postcard({ destination: d, index, resolving, onPick }) {
  const tiltRef = useTilt(8)
  return (
    <button
      ref={tiltRef}
      type="button"
      className="destination-card"
      style={{ '--i': index, '--rot': `${index % 2 === 0 ? -1.4 : 1.2}deg` }}
      onClick={onPick}
      // Знаменитостите се бараат однапред, па следниот екран се отвора веднаш
      onMouseEnter={() => prefetchLandmarks(d.lat, d.lon)}
      onFocus={() => prefetchLandmarks(d.lat, d.lon)}
      disabled={resolving !== null}
    >
      <span className="postcard-photo">
        {d.imageUrl ? <FadeImg src={d.imageUrl} alt="" loading="lazy" /> : <span className="destination-fallback">🏙️</span>}
        <span className="postcard-glare" />
      </span>
      <span className="destination-overlay">
        <strong>{d.name}</strong>
        <span>{d.country}</span>
      </span>
      {d.hasAssistant && <span className="stamp" title="Достапен локален AI водич">✨<small>водич</small></span>}
      {resolving === d.name && <span className="destination-loading"><span className="spinner" /></span>}
    </button>
  )
}
