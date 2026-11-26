// Преводи на вредностите од backend-от (enum-и) на македонски.

export const INTEREST_OPTIONS = [
  { value: 'CULTURE', icon: '🏛️', title: 'Култура', hint: 'музеи, галерии' },
  { value: 'HISTORY', icon: '🏰', title: 'Историја', hint: 'тврдини, споменици' },
  { value: 'NATURE', icon: '🌳', title: 'Природа', hint: 'паркови, видиковци' },
  { value: 'RELIGION', icon: '⛪', title: 'Религија', hint: 'цркви, џамии' },
]

export const PACE_OPTIONS = [
  { value: 'RELAXED', icon: '🐢', title: 'Опуштено', hint: '4 места дневно' },
  { value: 'NORMAL', icon: '🚶', title: 'Нормално', hint: '5 места дневно' },
  { value: 'INTENSIVE', icon: '🏃', title: 'Интензивно', hint: '7 места дневно' },
]

export const CATEGORY_ICONS = {
  MUSEUM: '🏛️',
  GALLERY: '🖼️',
  ATTRACTION: '📍',
  CASTLE: '🏰',
  MONUMENT: '🗿',
  RUINS: '🏺',
  PARK: '🌳',
  VIEWPOINT: '🌄',
  CHURCH: '⛪',
}

export const CATEGORY_LABELS = {
  MUSEUM: 'Музеј',
  GALLERY: 'Галерија',
  ATTRACTION: 'Атракција',
  CASTLE: 'Тврдина',
  MONUMENT: 'Споменик',
  RUINS: 'Археолошки локалитет',
  PARK: 'Парк',
  VIEWPOINT: 'Видиковец',
  CHURCH: 'Верски објект',
  RESTAURANT: 'Ресторан',
  CAFE: 'Кафуле',
}

export const ITEM_LABELS = {
  LUNCH: 'Ручек',
  COFFEE: 'Кафе пауза',
  DINNER: 'Вечера',
}

/** Бои за деновите на мапата (ден 1, ден 2, ...). */
export const DAY_COLORS = ['#0f766e', '#ea580c', '#7c3aed', '#2563eb', '#db2777', '#65a30d', '#0891b2']

export function dayColor(dayNumber) {
  return DAY_COLORS[(dayNumber - 1) % DAY_COLORS.length]
}

/**
 * WMO код за времето (од Open-Meteo) → икона и опис.
 * https://open-meteo.com/en/docs (табела „WMO Weather interpretation codes“)
 */
export function weatherInfo(code) {
  if (code === null || code === undefined) return { icon: '❔', label: 'Нема прогноза' }
  if (code === 0) return { icon: '☀️', label: 'Ведро' }
  if (code <= 2) return { icon: '🌤️', label: 'Делумно облачно' }
  if (code === 3) return { icon: '☁️', label: 'Облачно' }
  if (code <= 48) return { icon: '🌫️', label: 'Магла' }
  if (code <= 57) return { icon: '🌦️', label: 'Росулка' }
  if (code <= 67) return { icon: '🌧️', label: 'Дожд' }
  if (code <= 77) return { icon: '❄️', label: 'Снег' }
  if (code <= 82) return { icon: '🌧️', label: 'Пороен дожд' }
  if (code <= 86) return { icon: '🌨️', label: 'Снежни врнежи' }
  return { icon: '⛈️', label: 'Невреме' }
}

// Рачно, бидејќи не секој прелистувач има македонски јазик за датуми (toLocaleDateString('mk'))
const WEEKDAYS = ['недела', 'понеделник', 'вторник', 'среда', 'четврток', 'петок', 'сабота']
const MONTHS = ['јануари', 'февруари', 'март', 'април', 'мај', 'јуни',
  'јули', 'август', 'септември', 'октомври', 'ноември', 'декември']

/** "2026-11-20" → "петок, 20 ноември" */
export function formatDate(isoDate) {
  const d = new Date(isoDate + 'T00:00:00')
  return `${WEEKDAYS[d.getDay()]}, ${d.getDate()} ${MONTHS[d.getMonth()]}`
}

/** "09:00:00" → "09:00" */
export function formatTime(time) {
  return time ? time.slice(0, 5) : ''
}
