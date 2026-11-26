/** „Скелети“: сиви облици на содржината додека таа се вчитува (наместо празна страна). */

export function TripPageSkeleton() {
  return (
    <div className="trip-skeleton" aria-busy="true" aria-label="Се вчитува планот">
      <div className="skeleton sk-hero" />
      <div className="sk-tabs">
        {[0, 1, 2, 3].map((i) => <div key={i} className="skeleton sk-tab" />)}
      </div>
      <div className="trip-layout">
        <div className="days">
          {[0, 1].map((d) => (
            <div key={d} className="card sk-day">
              <div className="skeleton sk-line w40 tall" />
              {[0, 1, 2, 3].map((i) => (
                <div key={i} className="sk-row">
                  <div className="skeleton sk-dot" />
                  <div className="sk-lines">
                    <div className="skeleton sk-line w70" />
                    <div className="skeleton sk-line w40" />
                  </div>
                </div>
              ))}
            </div>
          ))}
        </div>
        <div className="skeleton sk-map" />
      </div>
    </div>
  )
}

export function TripsSkeleton() {
  return (
    <div aria-busy="true" aria-label="Се вчитуваат патувањата">
      <div className="page-title">
        <div className="skeleton sk-line w30 tall" />
      </div>
      <div className="trip-grid">
        {[0, 1, 2].map((i) => <div key={i} className="skeleton sk-trip-card" />)}
      </div>
    </div>
  )
}

export function TextSkeleton({ lines = 3 }) {
  return (
    <div className="sk-lines" aria-hidden="true">
      {Array.from({ length: lines }, (_, i) => (
        <div key={i} className={`skeleton sk-line ${i === lines - 1 ? 'w60' : 'w100'}`} />
      ))}
    </div>
  )
}
