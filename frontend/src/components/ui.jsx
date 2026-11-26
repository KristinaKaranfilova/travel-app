import { useEffect, useRef, useState } from 'react'
import { prefersReducedMotion } from '../motion.js'

/**
 * Се појавува (лизга нагоре) кога ќе влезе во видното поле.
 * delay (ms) овозможува „каскада“ кога има повеќе елементи еден по друг.
 */
export function Reveal({ as = 'div', delay = 0, className = '', style, children, ...rest }) {
  const Tag = as
  const ref = useRef(null)
  const [shown, setShown] = useState(false)

  useEffect(() => {
    const el = ref.current
    if (!el || shown) return
    if (!('IntersectionObserver' in window)) {
      setShown(true)
      return
    }
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setShown(true)
          observer.disconnect()
        }
      },
      { rootMargin: '0px 0px -40px 0px' },
    )
    observer.observe(el)
    return () => observer.disconnect()
  }, [shown])

  return (
    <Tag
      ref={ref}
      className={`reveal ${shown ? 'in' : ''} ${className}`}
      {...rest}
      style={{ '--reveal-delay': `${delay}ms`, ...style }}
    >
      {children}
    </Tag>
  )
}

/** Слика што меко се појавува кога ќе се вчита (наместо да „скокне“). */
export function FadeImg({ className = '', onLoad, ...props }) {
  const [loaded, setLoaded] = useState(false)
  return (
    <img
      {...props}
      className={`fade-img ${loaded ? 'loaded' : ''} ${className}`}
      onLoad={(e) => {
        setLoaded(true)
        onLoad?.(e)
      }}
      onError={() => setLoaded(true)}
    />
  )
}

/** Број што „брои“ до вредноста (за статистиките на планот). */
export function CountUp({ value, decimals = 0, duration = 900 }) {
  const [shown, setShown] = useState(() => (prefersReducedMotion() ? value : 0))

  useEffect(() => {
    if (prefersReducedMotion()) {
      setShown(value)
      return
    }
    let frame
    const start = performance.now()
    const tick = (now) => {
      const t = Math.min(1, (now - start) / duration)
      const eased = 1 - Math.pow(1 - t, 3)
      setShown(value * eased)
      if (t < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [value, duration])

  return <>{shown.toFixed(decimals)}</>
}

/**
 * Збор што се менува на неколку секунди (на пр. имиња на градови во насловот).
 * Секој нов збор влегува со анимација (key го рестартира CSS-от).
 */
export function RotatingWord({ words, interval = 2200 }) {
  const [index, setIndex] = useState(0)
  useEffect(() => {
    if (prefersReducedMotion() || words.length < 2) return
    const id = setInterval(() => setIndex((i) => (i + 1) % words.length), interval)
    return () => clearInterval(id)
  }, [words.length, interval])
  return (
    <span className="rotating-word">
      <span key={index} className="rotating-word-inner">{words[index]}</span>
    </span>
  )
}
