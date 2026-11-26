// Сите повици кон backend-от се на едно место.

const TOKEN_KEY = 'travel-token'

/** Токенот за најава се чува во localStorage, за да остане по освежување на страната. */
export function getToken() {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token) {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    // приватен режим: токенот важи само до затворање на страната
  }
}

/** Се повикува кога серверот ќе врати 401 (токенот истекол): AuthContext го одјавува корисникот. */
let onUnauthorized = () => {}
export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

async function request(path, options = {}) {
  const token = getToken()
  const res = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
  })
  if (res.status === 401 && token) {
    onUnauthorized()
  }
  if (!res.ok) {
    // Backend-от враќа {message: "..."} со објаснување на грешката
    let message = res.status === 401 ? 'Потребна е најава' : `Грешка ${res.status}`
    try {
      const body = await res.json()
      if (body.message) message = body.message
    } catch {
      // телото не е JSON, ја задржуваме општата порака
    }
    throw new Error(message)
  }
  return res.status === 204 ? null : res.json()
}

/**
 * Мал кеш во меморија за GET барања чии одговори ретко се менуваат (знаменитости, описи...).
 * Се чува самиот Promise, па две истовремени барања за исто нешто праќаат само едно.
 * Така „prefetch“ (на пр. кога глувчето ќе помине над дестинација) го прави кликот инстант.
 */
const memo = new Map()
function cached(key, load, ttlMs = 10 * 60 * 1000) {
  const hit = memo.get(key)
  if (hit && hit.expires > Date.now()) return hit.promise
  const promise = load().catch((err) => {
    memo.delete(key) // грешките не се кешираат
    throw err
  })
  memo.set(key, { promise, expires: Date.now() + ttlMs })
  return promise
}

// ---------- Најава ----------

export function register(fullName, email, password) {
  return request('/api/auth/register', { method: 'POST', body: JSON.stringify({ fullName, email, password }) })
}

export function login(email, password) {
  return request('/api/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })
}

export function getMe() {
  return request('/api/auth/me')
}

// ---------- Градови и знаменитости ----------

/** Пребарување градови (Nominatim). Враќа предлози, ништо не се зачувува. */
export function searchCities(query) {
  return request(`/api/cities/search?q=${encodeURIComponent(query)}`)
}

/** Го зачувува избраниот град и го враќа со id. */
export function selectCity(suggestion) {
  return request('/api/cities', { method: 'POST', body: JSON.stringify(suggestion) })
}

/** Популарни дестинации за почетната страна. */
export function getFeaturedDestinations() {
  return cached('featured', () => request('/api/destinations/featured'), 60 * 1000)
}

/** Најпознати знаменитости со слики околу дадена точка. */
export function getLandmarks(lat, lon) {
  const key = `landmarks|${Number(lat).toFixed(2)}|${Number(lon).toFixed(2)}`
  return cached(key, () => request(`/api/landmarks?lat=${lat}&lon=${lon}`))
}

/** Однапред (на пр. при hover), без да се чека и без грешки. */
export function prefetchLandmarks(lat, lon) {
  if (lat == null || lon == null) return
  getLandmarks(lat, lon).catch(() => {})
}

/** Дали местата за градот се преземени од OpenStreetMap (ако не, преземањето почнува). */
export function getPlacesStatus(cityId) {
  return request(`/api/cities/${cityId}/places/status`)
}

/** Краток опис на место (од Wikipedia/Wikidata). */
export function getPlaceDetails({ name, lat, lon, wikidataId }) {
  const params = new URLSearchParams({ name, lat, lon })
  if (wikidataId) params.set('wikidataId', wikidataId)
  return cached(`details|${params}`, () => request(`/api/places/details?${params}`))
}

export function prefetchPlaceDetails(place) {
  getPlaceDetails(place).catch(() => {})
}

// ---------- Патувања ----------

/** Генерира нов план. Враќа {id, plan}. */
export function createTrip(tripRequest) {
  return request('/api/trips', { method: 'POST', body: JSON.stringify(tripRequest) })
}

/** Патувањата на најавениот корисник. */
export function getMyTrips() {
  return request('/api/trips')
}

export function deleteTrip(id) {
  return request(`/api/trips/${id}`, { method: 'DELETE' })
}

/** Зачуван план по id. */
export function getTrip(id) {
  return request(`/api/trips/${id}`)
}

// ---------- Чет ----------

/** Прашање до чет-ботот. Враќа {answer, sources: [{section, url, score}]}. */
export function askChat(cityId, question) {
  return request('/api/chat', { method: 'POST', body: JSON.stringify({ cityId, question }) })
}

export function getHealth() {
  return request('/api/health')
}
