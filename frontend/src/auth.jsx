import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import * as api from './api.js'

/**
 * Состојба на најавата за целата апликација (React Context).
 * user = null ако корисникот не е најавен.
 *
 * Брзина: корисникот се чува и во localStorage, па апликацијата веднаш се прикажува
 * (без „Се вчитува...“), а токенот се проверува во позадина. Ако истекол, корисникот се одјавува.
 */
const AuthContext = createContext(null)
const USER_KEY = 'travel-user'

function readCachedUser() {
  if (!api.getToken()) return null
  try {
    return JSON.parse(localStorage.getItem(USER_KEY))
  } catch {
    return null
  }
}

function cacheUser(user) {
  try {
    if (user) localStorage.setItem(USER_KEY, JSON.stringify(user))
    else localStorage.removeItem(USER_KEY)
  } catch {
    // приватен режим
  }
}

export function AuthProvider({ children }) {
  const [user, setUserState] = useState(readCachedUser)
  // Чекаме на серверот само ако има токен, а нема зачуван корисник
  const [checking, setChecking] = useState(() => api.getToken() !== null && readCachedUser() === null)

  const setUser = useCallback((u) => {
    cacheUser(u)
    setUserState(u)
  }, [])

  const logout = useCallback(() => {
    api.setToken(null)
    setUser(null)
  }, [setUser])

  // Ако има зачуван токен, провери кој е корисникот (и дали токенот уште важи)
  useEffect(() => {
    api.setUnauthorizedHandler(logout)
    if (!api.getToken()) return
    api.getMe()
      .then(setUser)
      .catch(logout)
      .finally(() => setChecking(false))
  }, [logout, setUser])

  async function login(email, password) {
    const result = await api.login(email, password)
    api.setToken(result.token)
    setUser(result.user)
  }

  async function register(fullName, email, password) {
    const result = await api.register(fullName, email, password)
    api.setToken(result.token)
    setUser(result.user)
  }

  return (
    <AuthContext.Provider value={{ user, checking, login, register, logout }}>{children}</AuthContext.Provider>
  )
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth() {
  return useContext(AuthContext)
}

/** Страна само за најавени корисници; другите ги праќа на /login (и потоа назад). */
export function RequireAuth({ children }) {
  const { user, checking } = useAuth()
  const location = useLocation()
  if (checking) return <div className="page-loader"><span className="compass-loader" aria-label="Се вчитува" /></div>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}
