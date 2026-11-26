import { weatherInfo } from '../labels.js'

/** Ознака со прогнозата за денот. weather е null ако денот е предалеку. */
export default function WeatherBadge({ weather }) {
  if (!weather) {
    return (
      <span className="weather-badge" title="Прогноза има само до 16 дена однапред">
        <span className="weather-icon">❔</span> Нема прогноза
      </span>
    )
  }
  const { icon, label } = weatherInfo(weather.weatherCode)
  const rainy = weather.precipitationProbability >= 50
  return (
    <span className={`weather-badge ${rainy ? 'rainy' : 'dry'}`} title={label}>
      <span className="weather-icon">{icon}</span>
      <span>
        <strong>{Math.round(weather.tempMax)}°</strong> / {Math.round(weather.tempMin)}°
      </span>
      {weather.precipitationProbability !== null && <span className="rain">💧 {weather.precipitationProbability}%</span>}
    </span>
  )
}
