import { useEffect, useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { askChat } from '../api.js'
import { prefersReducedMotion } from '../motion.js'

// eslint-disable-next-line react-refresh/only-export-components
export const CHAT_EXAMPLES = ['Каде да пробам локална храна?', 'Како да стигнам до центарот?', 'Дали е безбедно навечер?']

const THINKING = ['Барам во туристичките водичи…', 'Ги читам најрелевантните делови…', 'Го составувам одговорот…']

function loadHistory(cityId) {
  try {
    return JSON.parse(sessionStorage.getItem(`chat-${cityId}`)) || []
  } catch {
    return []
  }
}

/**
 * Локалниот водич (RAG асистент) како голем панел од десната страна.
 * Се отвора од банерот на планот, од предлог-прашање или од копчето долу десно.
 * Секое прашање е посебно (ботот не памети претходни прашања), но разговорот
 * останува видлив додека е отворен табот (sessionStorage).
 * Под секој одговор се прикажуваат изворите од Wikivoyage.
 */
export default function ChatPanel({ cityId, cityName, open, onOpen, onClose, question }) {
  const [messages, setMessages] = useState(() => loadHistory(cityId))
  const [input, setInput] = useState('')
  const [loading, setLoading] = useState(false)
  const [thinking, setThinking] = useState(0)
  const endRef = useRef(null)
  const inputRef = useRef(null)

  useEffect(() => {
    try {
      sessionStorage.setItem(`chat-${cityId}`, JSON.stringify(messages.map((m) => ({ ...m, fresh: false }))))
    } catch {
      // нема простор / приватен режим
    }
  }, [cityId, messages])

  // Секоја нова порака: скролај до дното
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [messages, loading, open])

  useEffect(() => {
    if (!open) return
    inputRef.current?.focus({ preventScroll: true })
    const onKey = (e) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [open, onClose])

  // Додека се чека одговорот, пораката се менува (за корисникот да знае дека нешто се случува)
  useEffect(() => {
    if (!loading) return
    setThinking(0)
    const id = setInterval(() => setThinking((t) => Math.min(t + 1, THINKING.length - 1)), 1800)
    return () => clearInterval(id)
  }, [loading])

  // Прашање од банерот (предлог) се праќа веднаш
  const lastAsked = useRef(null)
  useEffect(() => {
    if (!open || !question || lastAsked.current === question.at) return
    lastAsked.current = question.at
    send(question.text)
    // send намерно не е во зависностите: се праќа само при ново прашање
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, question])

  async function send(text) {
    const q = text.trim()
    if (!q || loading) return
    setInput('')
    setMessages((m) => [...m, { role: 'user', text: q }])
    setLoading(true)
    try {
      const res = await askChat(cityId, q)
      setMessages((m) => [...m, { role: 'bot', text: res.answer, sources: res.sources, fresh: true }])
    } catch (err) {
      setMessages((m) => [...m, { role: 'bot', text: err.message, error: true, fresh: true }])
    } finally {
      setLoading(false)
    }
  }

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
          <div className="chat-avatar" aria-hidden="true">🧭<span className="online" /></div>
          <div className="chat-head-text">
            <strong>Локален водич · {cityName}</strong>
            <small>Одговара од Wikivoyage, со извори</small>
          </div>
          {messages.length > 0 && (
            <button type="button" className="icon-button" title="Нов разговор" onClick={() => setMessages([])}>↺</button>
          )}
          <button type="button" className="icon-button" title="Затвори" onClick={onClose}>✕</button>
        </header>

        <div className="chat-body">
          {messages.length === 0 && !loading && (
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
            <div key={i} className={`msg ${msg.role} ${msg.error ? 'error' : ''}`}>
              {msg.role === 'bot' && <span className="msg-avatar" aria-hidden="true">🧭</span>}
              <div className="bubble">
                {msg.error && <strong className="msg-error-title">Нешто не е во ред</strong>}
                <div className="bubble-text">
                  {msg.fresh ? <Typewriter text={msg.text} /> : msg.text}
                </div>
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
              </div>
            </div>
          ))}

          {loading && (
            <div className="msg bot">
              <span className="msg-avatar" aria-hidden="true">🧭</span>
              <div className="bubble typing">
                <span className="dots"><i /><i /><i /></span>
                <span key={thinking} className="typing-text">{THINKING[thinking]}</span>
              </div>
            </div>
          )}
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
            placeholder={`Прашај нешто за ${cityName}…`}
            value={input}
            maxLength={500}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                send(input)
              }
            }}
          />
          <button type="submit" className="send-button" disabled={loading || !input.trim()} aria-label="Прати">
            ✈
          </button>
        </form>
      </aside>
    </>,
    document.body,
  )
}

/** Одговорот се „пишува“ брзо (најмногу ~1.2 s), за разговорот да делува живо. */
function Typewriter({ text }) {
  const [count, setCount] = useState(() => (prefersReducedMotion() ? text.length : 0))
  useEffect(() => {
    if (prefersReducedMotion()) return
    const step = Math.max(2, Math.ceil(text.length / 70))
    const id = setInterval(() => {
      setCount((c) => {
        if (c + step >= text.length) {
          clearInterval(id)
          return text.length
        }
        return c + step
      })
    }, 16)
    return () => clearInterval(id)
  }, [text])
  return (
    <>
      {text.slice(0, count)}
      {count < text.length && <span className="caret" />}
    </>
  )
}
