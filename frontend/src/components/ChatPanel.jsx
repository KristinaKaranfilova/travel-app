import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { streamChat } from '../api.js'

// eslint-disable-next-line react-refresh/only-export-components
export const CHAT_EXAMPLES = ['Каде да пробам локална храна?', 'Како да стигнам до центарот?', 'Дали е безбедно навечер?']

const SEARCHING = ['Барам во туристичкиот водич…', 'Ги споредувам деловите од водичот…']
const WRITING = ['Го составувам одговорот…', 'Уште малку…']

function loadHistory(cityId) {
  try {
    const saved = JSON.parse(sessionStorage.getItem(`chat-${cityId}`)) || []
    return saved.filter((m) => !m.streaming)
  } catch {
    return []
  }
}

/**
 * Локалниот водич (RAG асистент) како голем панел од десната страна.
 * Се отвора од банерот на планот, од предлог-прашање или од копчето долу десно.
 *
 * Одговорот стигнува „во живо“ (streaming): прво изворите (~1 s), па текстот збор по збор.
 * Ако Gemini е преоптоварен, пораката тоа го кажува и има копче „Обиди се повторно“.
 * guide: состојба на водичот за градот (READY / PREPARING / UNAVAILABLE / FAILED).
 * Секое прашање е посебно (ботот не памети претходни прашања), но разговорот
 * останува видлив додека е отворен табот (sessionStorage).
 */
export default function ChatPanel({ cityId, cityName, open, onOpen, onClose, question, guide }) {
  const [messages, setMessages] = useState(() => loadHistory(cityId))
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const endRef = useRef(null)
  const inputRef = useRef(null)
  const abortRef = useRef(null)
  const ready = guide?.state === 'READY'

  useEffect(() => {
    try {
      sessionStorage.setItem(`chat-${cityId}`, JSON.stringify(messages.filter((m) => !m.streaming)))
    } catch {
      // нема простор / приватен режим
    }
  }, [cityId, messages])

  // Прекини го одговорот ако корисникот ја напушти страната
  useEffect(() => () => abortRef.current?.abort(), [])

  // Секоја нова порака (и секој нов збор): скролај до дното
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages, open])

  useEffect(() => {
    if (!open) return
    inputRef.current?.focus({ preventScroll: true })
    const onKey = (e) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  // Прашање од банерот (предлог) се праќа веднаш, штом водичот е подготвен
  const lastAsked = useRef(null)
  useEffect(() => {
    if (!open || !ready || !question || lastAsked.current === question.at) return
    lastAsked.current = question.at
    send(question.text)
    // send намерно не е во зависностите: се праќа само при ново прашање
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, ready, question])

  function update(id, change) {
    setMessages((all) => all.map((m) => (m.id === id ? { ...m, ...change(m) } : m)))
  }

  async function send(text) {
    const q = text.trim()
    if (!q || busy || !ready) return
    setInput('')
    const id = `${Date.now()}`
    setMessages((m) => [
      ...m,
      { role: 'user', text: q },
      { id, role: 'bot', text: '', sources: [], streaming: true, stage: 'searching', question: q },
    ])
    setBusy(true)
    const controller = new AbortController()
    abortRef.current = controller
    await streamChat(
      cityId,
      q,
      {
        onSources: (sources) => update(id, () => ({ sources, stage: 'writing' })),
        onToken: (part) => update(id, (m) => ({ text: m.text + part, stage: 'writing' })),
        onDone: ({ answer, sources }) => update(id, () => ({ text: answer, sources, streaming: false })),
        onError: ({ message, retryable }) =>
          update(id, (m) => ({
            // Ако дел од одговорот веќе стигнал, го задржуваме и додаваме напомена
            text: m.text ? `${m.text}\n\n(${message})` : message,
            error: !m.text,
            retryable,
            streaming: false,
          })),
      },
      controller.signal,
    )
    setBusy(false)
  }

  function retry(msg) {
    // Неуспешниот пар (прашање + грешка) се брише и прашањето се праќа повторно
    setMessages((all) => {
      const index = all.findIndex((m) => m.id === msg.id)
      return index > 0 ? [...all.slice(0, index - 1), ...all.slice(index + 1)] : all
    })
    setTimeout(() => send(msg.question), 0)
  }

  function clear() {
    abortRef.current?.abort()
    setBusy(false)
    setMessages([])
  }

  const placeholder = ready
    ? `Прашај нешто за ${cityName}…`
    : guide?.state === 'PREPARING'
      ? 'Водичот се подготвува…'
      : 'Асистентот не е достапен за овој град'

  // Portal во <body>: панелот е над сè (и над мапата), независно од родителите
  return createPortal(
    <>
      {!open && (
        <button type="button" className="chat-launcher" onClick={onOpen}>
          <span className="launcher-avatar">🧭</span>
          <span className="launcher-text">
            <strong>Прашај го водичот</strong>
            <small>за {cityName}</small>
          </span>
        </button>
      )}

      <div className={`chat-scrim ${open ? 'open' : ''}`} onClick={onClose} aria-hidden="true" />

      <aside
        className={`chat-drawer ${open ? 'open' : ''}`}
        aria-label={`Локален водич за ${cityName}`}
        aria-hidden={!open}
        inert={!open}
      >
        <header className="chat-head">
          <div className="chat-avatar" aria-hidden="true">🧭<span className={`online ${ready ? '' : 'away'}`} /></div>
          <div className="chat-head-text">
            <strong>Локален водич · {cityName}</strong>
            <small>{ready ? 'Одговара од Wikivoyage, со извори' : 'Се подготвува…'}</small>
          </div>
          {messages.length > 0 && (
            <button type="button" className="icon-button" title="Нов разговор" onClick={clear}>↺</button>
          )}
          <button type="button" className="icon-button" title="Затвори" onClick={onClose}>✕</button>
        </header>

        <div className="chat-body">
          {!ready && <GuideState guide={guide} cityName={cityName} />}

          {ready && messages.length === 0 && (
            <div className="chat-welcome">
              <span className="welcome-icon">👋</span>
              <h3>Здраво! Јас сум твојот водич за {cityName}.</h3>
              <p>Одговарам само врз основа на проверени туристички извори и секогаш ти покажувам од каде е одговорот.</p>
              <div className="welcome-suggestions">
                {CHAT_EXAMPLES.map((ex) => (
                  <button key={ex} type="button" className="suggestion" onClick={() => send(ex)}>{ex}</button>
                ))}
              </div>
            </div>
          )}

          {messages.map((msg, i) => (
            <div key={msg.id || i} className={`msg ${msg.role} ${msg.error ? 'error' : ''}`}>
              {msg.role === 'bot' && <span className="msg-avatar" aria-hidden="true">🧭</span>}
              <div className={`bubble ${msg.streaming && !msg.text ? 'typing' : ''}`}>
                {msg.streaming && !msg.text ? (
                  <Thinking stage={msg.stage} count={msg.sources.length} />
                ) : (
                  <>
                    {msg.error && <strong className="msg-error-title">Нешто не е во ред</strong>}
                    <div className="bubble-text">
                      {msg.text}
                      {msg.streaming && <span className="caret" />}
                    </div>
                  </>
                )}
                {msg.sources?.length > 0 && (
                  <div className="msg-sources">
                    <span className="sources-label">Извори</span>
                    {msg.sources.map((s, j) => (
                      <a key={j} href={s.url} target="_blank" rel="noreferrer" className="source-chip" title={`Сличност ${s.score}`}>
                        📖 {s.section}
                        <span className="source-score" style={{ '--score': s.score }} />
                      </a>
                    ))}
                  </div>
                )}
                {msg.error && msg.retryable && msg.question && (
                  <button type="button" className="retry-button" onClick={() => retry(msg)} disabled={busy}>
                    ↻ Обиди се повторно
                  </button>
                )}
              </div>
            </div>
          ))}
          <div ref={endRef} />
        </div>

        <form
          className="chat-compose"
          onSubmit={(e) => {
            e.preventDefault()
            send(input)
          }}
        >
          <textarea
            ref={inputRef}
            rows={1}
            placeholder={placeholder}
            value={input}
            maxLength={500}
            disabled={!ready}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                send(input)
              }
            }}
          />
          <button type="submit" className="send-button" disabled={busy || !ready || !input.trim()} aria-label="Прати">
            ✈
          </button>
        </form>
      </aside>
    </>,
    document.body,
  )
}

/** Додека се чека: прво пребарување во водичот, па пишување на одговорот (со бројот на извори). */
function Thinking({ stage, count }) {
  const lines = stage === 'searching' ? SEARCHING : WRITING
  const [index, setIndex] = useState(0)
  useEffect(() => {
    setIndex(0)
    const id = setInterval(() => setIndex((i) => Math.min(i + 1, lines.length - 1)), 2200)
    return () => clearInterval(id)
  }, [stage, lines.length])
  const text = stage === 'writing' && count > 0 && index === 0 ? `Најдов ${count} ${count === 1 ? 'извор' : 'извори'}, го пишувам одговорот…` : lines[index]
  return (
    <>
      <span className="dots"><i /><i /><i /></span>
      <span key={`${stage}-${index}`} className="typing-text">{text}</span>
    </>
  )
}

/** Картичка во четот кога водичот за градот уште не е подготвен (или го нема). */
function GuideState({ guide, cityName }) {
  if (!guide || guide.state === 'PREPARING') {
    const percent = guide?.total ? Math.round((guide.done / guide.total) * 100) : null
    return (
      <div className="guide-state preparing">
        <span className="guide-state-icon">📚</span>
        <h3>Го читам водичот за {cityName}…</h3>
        <p>
          Прв пат некој прашува за {cityName}, па водичот од Wikivoyage сега се внесува
          {guide?.title ? <> (страница „{guide.title}“)</> : null}. Ова трае една до две минути и се прави само еднаш.
        </p>
        <div className="guide-progress" aria-label="Напредок">
          <span style={{ width: percent === null ? undefined : `${Math.max(6, percent)}%` }} className={percent === null ? 'indeterminate' : ''} />
        </div>
        <small className="muted">{percent === null ? 'Ја барам страницата…' : `${guide.done} од ${guide.total} делови (${percent}%)`}</small>
      </div>
    )
  }
  return (
    <div className="guide-state unavailable">
      <span className="guide-state-icon">{guide.state === 'UNAVAILABLE' ? '🗺️' : '⏳'}</span>
      <h3>{guide.state === 'UNAVAILABLE' ? 'Нема водич за овој град' : 'Водичот уште не е подготвен'}</h3>
      <p>{guide.message}</p>
      {guide.state === 'FAILED' && <small className="muted">Ќе се обидам повторно автоматски за неколку минути.</small>}
    </div>
  )
}
