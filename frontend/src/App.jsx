import { Suspense, lazy, useEffect, useState } from 'react'
import { BrowserRouter, Link, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import { AuthProvider, RequireAuth, useAuth } from './auth.jsx'
import AuthPage from './pages/AuthPage.jsx'
import HomePage from './pages/HomePage.jsx'
import { TripPageSkeleton, TripsSkeleton } from './components/Skeletons.jsx'

// Страните со мапа (Leaflet) се вчитуваат дури кога се потребни: почетната страна се отвора побрзо
const TripPage = lazy(() => import('./pages/TripPage.jsx'))
const MyTripsPage = lazy(() => import('./pages/MyTripsPage.jsx'))

function Header() {
  const { user, logout } = useAuth()
  const [scrolled, setScrolled] = useState(false)

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return (
    <header className={`app-header ${scrolled ? 'scrolled' : ''}`}>
      <div className="app-header-inner">
        <Link to="/" className="brand">
          <span className="brand-icon" aria-hidden="true">
            <svg viewBox="0 0 40 40" className="brand-compass">
              <circle cx="20" cy="20" r="17" fill="none" stroke="currentColor" strokeWidth="2.5" opacity="0.35" />
              <path d="M20 6 L24 20 L20 34 L16 20 Z" fill="currentColor" className="needle" />
              <path d="M20 6 L24 20 L16 20 Z" fill="#ff7a59" className="needle" />
              <circle cx="20" cy="20" r="2.6" fill="#fff" />
            </svg>
          </span>
          <span className="brand-name">Патоказ</span>
        </Link>
        <nav className="nav">
          {user ? (
            <>
              <NavLink to="/" end className="nav-link" title="Планирај">
                <span aria-hidden="true">✈</span><span className="nav-label">Планирај</span>
              </NavLink>
              <NavLink to="/my-trips" className="nav-link" title="Мои патувања">
                <span aria-hidden="true">🧳</span><span className="nav-label">Мои патувања</span>
              </NavLink>
              <span className="user-chip" title={user.email}>
                <span className="avatar">{user.fullName.charAt(0).toUpperCase()}</span>
                <span className="user-name">{user.fullName}</span>
              </span>
              <button type="button" className="link-button logout" onClick={logout}>Одјава</button>
            </>
          ) : (
            <>
              <NavLink to="/login" className="nav-link">Најава</NavLink>
              <Link to="/register" className="header-cta">Регистрација</Link>
            </>
          )}
        </nav>
      </div>
    </header>
  )
}

/** Декоративна позадина: меки бои што полека се движат + топографски линии. */
function Backdrop() {
  return (
    <div className="backdrop" aria-hidden="true">
      <span className="blob blob-a" />
      <span className="blob blob-b" />
      <span className="blob blob-c" />
      <span className="topo" />
    </div>
  )
}

/** Секоја страна влегува со кратка анимација; key = патеката ја рестартира. */
function AnimatedRoutes() {
  const location = useLocation()

  useEffect(() => {
    window.scrollTo({ top: 0 })
  }, [location.pathname])

  return (
    <div key={location.pathname} className="page">
      <Routes location={location}>
        <Route path="/login" element={<AuthPage mode="login" />} />
        <Route path="/register" element={<AuthPage mode="register" />} />
        <Route path="/" element={<RequireAuth><HomePage /></RequireAuth>} />
        <Route
          path="/my-trips"
          element={
            <RequireAuth>
              <Suspense fallback={<TripsSkeleton />}><MyTripsPage /></Suspense>
            </RequireAuth>
          }
        />
        {/* Планот е јавен, за линкот за споделување да работи и без најава */}
        <Route path="/trip/:id" element={<Suspense fallback={<TripPageSkeleton />}><TripPage /></Suspense>} />
      </Routes>
    </div>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Backdrop />
        <Header />
        <main className="app-main">
          <AnimatedRoutes />
        </main>
        <footer className="app-footer">
          <span className="footer-script">Среќен пат! ✈</span>
          <span>Податоци: © OpenStreetMap, Open-Meteo, Wikivoyage, Wikidata и Wikimedia Commons (CC BY-SA)</span>
        </footer>
      </AuthProvider>
    </BrowserRouter>
  )
}
