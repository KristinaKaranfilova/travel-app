import { useEffect, useRef } from 'react'

/** Дали корисникот побарал помалку анимации (системска поставка). */
export function prefersReducedMotion() {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
}

/** 3D навалување на картичка според позицијата на глувчето (како разгледница во рака). */
export function useTilt(max = 7) {
  const ref = useRef(null)
  useEffect(() => {
    const el = ref.current
    if (!el || prefersReducedMotion() || !window.matchMedia('(hover: hover)').matches) return
    const move = (e) => {
      const r = el.getBoundingClientRect()
      const x = (e.clientX - r.left) / r.width - 0.5
      const y = (e.clientY - r.top) / r.height - 0.5
      el.style.setProperty('--tilt-x', `${(-y * max).toFixed(2)}deg`)
      el.style.setProperty('--tilt-y', `${(x * max).toFixed(2)}deg`)
      el.style.setProperty('--glare-x', `${((x + 0.5) * 100).toFixed(0)}%`)
      el.style.setProperty('--glare-y', `${((y + 0.5) * 100).toFixed(0)}%`)
    }
    const leave = () => {
      el.style.setProperty('--tilt-x', '0deg')
      el.style.setProperty('--tilt-y', '0deg')
    }
    el.addEventListener('mousemove', move)
    el.addEventListener('mouseleave', leave)
    return () => {
      el.removeEventListener('mousemove', move)
      el.removeEventListener('mouseleave', leave)
    }
  }, [max])
  return ref
}
