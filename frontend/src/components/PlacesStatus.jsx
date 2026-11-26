import { useEffect, useState } from 'react'

/**
 * Ред во картата за патување што кажува дали местата за градот се подготвени.
 * Првиот пат за нов град, преземањето од OpenStreetMap трае (до една минута), па
 * корисникот гледа напредок и може во меѓувреме да ги избере датумите и интересите.
 */
export default function PlacesStatus({ cityName, saving, status, onRetry }) {
  const loading = saving || status === null || status.loading
  const seconds = useElapsedSeconds(loading)

  if (status?.ready) {
    return (
      <div className="places-status ready" role="status">
        <span className="ps-icon pop">✓</span>
        <span className="ps-text">
          <strong>{cityName} е подготвен за планирање</strong>
          <span className="ps-counts">
            <span>🏛️ {status.attractions} атракции</span>
            <span>🍽️ {status.restaurants} ресторани</span>
            <span>☕ {status.cafes} кафулиња</span>
          </span>
        </span>
      </div>
    )
  }

  if (status?.error) {
    return (
      <div className="places-status failed" role="status">
        <span className="ps-icon">!</span>
        <span className="ps-text">
          <strong>Местата моментално не може да се преземат</strong>
          <span className="small">{status.error}</span>
        </span>
        <button type="button" className="chip-button retry" onClick={onRetry}>Обиди се пак</button>
      </div>
    )
  }

  return (
    <div className="places-status loading" role="status">
      <span className="ps-icon"><span className="radar" /></span>
      <span className="ps-text">
        <strong>Ги собираме местата во {cityName}…</strong>
        <span className="small">
          {seconds < 4
            ? 'Атракции, ресторани и кафулиња од OpenStreetMap.'
            : seconds < 60
              ? `За нов град ова трае до една минута (${seconds} s). Во меѓувреме избери датуми и интереси.`
              : `Серверите на OpenStreetMap се зафатени денес, па пробуваме и резервен сервер (${seconds} s). Само уште малку…`}
        </span>
      </span>
      <span className="ps-bar" aria-hidden="true"><span /></span>
    </div>
  )
}

function useElapsedSeconds(running) {
  const [seconds, setSeconds] = useState(0)
  useEffect(() => {
    setSeconds(0)
    if (!running) return
    const id = setInterval(() => setSeconds((s) => s + 1), 1000)
    return () => clearInterval(id)
  }, [running])
  return seconds
}
