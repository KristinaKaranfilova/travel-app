import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth.jsx'
import { getFeaturedDestinations } from '../api.js'
import { FadeImg } from '../components/ui.jsx'

/** Најава (mode="login") или регистрација (mode="register"). */
export default function AuthPage({ mode }) {
  const isRegister = mode === 'register'
  const { user, login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const [photos, setPhotos] = useState([])

  // Разгледници од популарните дестинации (јавно API, без најава)
  useEffect(() => {
    getFeaturedDestinations()
      .then((d) => setPhotos(d.filter((x) => x.imageUrl).slice(0, 3)))
      .catch(() => {})
  }, [])

  const goTo = location.state?.from || '/'
  if (user) return <Navigate to={goTo} replace />

  async function handleSubmit(e) {
    e.preventDefault()
    setError(null)
    if (isRegister && password.length < 8) {
      setError('Лозинката мора да има најмалку 8 знаци.')
      return
    }
    setLoading(true)
    try {
      if (isRegister) await register(fullName, email, password)
      else await login(email, password)
      navigate(goTo, { replace: true })
    } catch (err) {
      setError(err.message)
      setLoading(false)
    }
  }

  return (
    <div className="auth-layout">
      <section className="auth-visual">
        <div className="auth-postcards" aria-hidden="true">
          {photos.map((p, i) => (
            <figure key={p.name} className={`auth-postcard p${i}`}>
              <FadeImg src={p.imageUrl} alt="" />
              <figcaption>{p.name}</figcaption>
            </figure>
          ))}
        </div>
        <span className="script auth-script">здраво, патнику!</span>
        <h2>Секое патување почнува со добар план.</h2>
        <ul>
          <li>🗺️ План по денови, со најкратки рути</li>
          <li>🌦️ Прилагоден на временската прогноза</li>
          <li>💬 Асистент со проверени информации</li>
          <li>💾 Сите твои патувања на едно место</li>
        </ul>
      </section>

      <form className="card auth-card" onSubmit={handleSubmit}>
        <h1>{isRegister ? 'Креирај профил' : 'Добредојде назад'}</h1>
        <p className="muted">
          {isRegister ? 'Регистрирај се за да ги зачуваш своите патувања.' : 'Најави се за да продолжиш со планирањето.'}
        </p>

        {isRegister && (
          <label className="field">
            <span className="field-label">Име и презиме</span>
            <input type="text" value={fullName} onChange={(e) => setFullName(e.target.value)} required autoComplete="name" />
          </label>
        )}
        <label className="field">
          <span className="field-label">Е-пошта</span>
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" />
        </label>
        <label className="field">
          <span className="field-label">Лозинка {isRegister && <small>(најмалку 8 знаци)</small>}</span>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete={isRegister ? 'new-password' : 'current-password'}
          />
        </label>

        {error && <p className="error-text">{error}</p>}

        <button type="submit" className="cta big" disabled={loading}>
          {loading ? <><span className="spinner" /> Почекај...</> : isRegister ? 'Регистрирај се' : 'Најави се'}
        </button>

        <p className="center small">
          {isRegister ? (
            <>Веќе имаш профил? <Link to="/login" state={location.state}>Најави се</Link></>
          ) : (
            <>Немаш профил? <Link to="/register" state={location.state}>Регистрирај се</Link></>
          )}
        </p>
      </form>
    </div>
  )
}
