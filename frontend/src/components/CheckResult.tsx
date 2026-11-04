import type { WebsiteCheckResponse } from '../types/websiteCheck'

interface CheckResultProps {
  result: WebsiteCheckResponse
}

function CheckResult({ result }: CheckResultProps) {
  const reachable = result.reachable

  return (
    <section
      className={`result-card ${reachable ? 'result-success' : 'result-failure'}`}
      aria-live="polite"
    >
      <div className="result-header">
        <div>
          <p className="result-eyebrow">Check result</p>
          <h2>{reachable ? 'Website is reachable' : 'Website is not reachable'}</h2>
        </div>
        <span className={`badge ${reachable ? 'badge-success' : 'badge-failure'}`}>
          {reachable ? 'PASS' : 'FAIL'}
        </span>
      </div>

      <dl className="result-grid">
        <div className="result-item result-item-wide">
          <dt>Submitted URL</dt>
          <dd className="mono">{result.url}</dd>
        </div>
        <div className="result-item">
          <dt>Reachability</dt>
          <dd>
            {reachable
              ? 'The website responded successfully.'
              : 'The website did not respond successfully or was unreachable.'}
          </dd>
        </div>
        <div className="result-item">
          <dt>HTTP status code</dt>
          <dd className="mono result-status-code">
            {result.httpStatusCode ?? 'Not available'}
          </dd>
        </div>
      </dl>
    </section>
  )
}

export default CheckResult
