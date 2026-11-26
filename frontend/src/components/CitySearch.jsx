import { useState } from 'react'
import { searchCities } from '../api.js'

/**
 * Пребарување град. Се пребарува на копче „Барај“ или Enter (не при секоја буква),
 * бидејќи Nominatim дозволува најмногу 1 барање во секунда.
 * Ги прикажува сите пронајдени места (до 5), за корисникот да го избере вистинското.
 */
export default function CitySearch({ onSelect }) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  async function handleSearch() {
    if (!query.trim()) return
    setLoading(true)
    setError(null)
    try {
      const found = await searchCities(query.trim())
      setResults(found)
      if (found.length === 0) setError('Не е пронајден град со тоа име. Пробај на англиски или со друг правопис.')
    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="city-search">
      {/* div, а не form: ова е дел од поголемата форма, а HTML не дозволува форма во форма */}
      <div className={`search-row big ${loading ? 'searching' : ''}`}>
        <span className="search-icon" aria-hidden="true">🔍</span>
        <input
          type="text"
          placeholder="Пребарај град, на пр. Лисабон, Амстердам, Струга..."
          value={query}
          aria-label="Пребарај град"
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault() // да не се испрати главната форма
              handleSearch()
            }
          }}
        />
        <button type="button" className="cta" onClick={handleSearch} disabled={loading}>
          {loading ? <><span className="spinner" /> Барам</> : 'Барај'}
        </button>
      </div>
      {error && <p className="error-text small">{error}</p>}
      {loading && results.length === 0 && (
        <ul className="city-results" aria-hidden="true">
          {[0, 1, 2].map((i) => <li key={i} className="skeleton sk-result" />)}
        </ul>
      )}
      {results.length > 0 && (
        <ul className="city-results">
          {results.map((city, i) => (
            <li key={`${city.osmType}-${city.osmId}`} style={{ '--i': i }}>
              <button type="button" onClick={() => onSelect(city)}>
                <span className="result-pin">📍</span>
                <span className="result-text">
                  <strong>{city.name}</strong>
                  <span className="muted">{city.displayName}</span>
                </span>
                <span className="result-arrow">→</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
