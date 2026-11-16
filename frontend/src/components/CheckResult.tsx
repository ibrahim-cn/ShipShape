import type { WebsiteCheckResponse } from '../types/websiteCheck'

interface CheckResultProps {
  result: WebsiteCheckResponse
}

type ReadinessStatus = 'PASS' | 'WARNING' | 'FAIL' | 'UNKNOWN'
type StatusTone = 'success' | 'warning' | 'failure' | 'neutral'

function normalizeStatus(status: string | null | undefined): ReadinessStatus {
  const normalized = status?.trim().toUpperCase()

  if (normalized === 'PASS' || normalized === 'WARNING' || normalized === 'FAIL') {
    return normalized
  }

  return 'UNKNOWN'
}

function getStatusTone(status: ReadinessStatus): StatusTone {
  if (status === 'PASS') {
    return 'success'
  }

  if (status === 'WARNING') {
    return 'warning'
  }

  if (status === 'FAIL') {
    return 'failure'
  }

  return 'neutral'
}

function formatResponseTime(responseTimeMs: number | null | undefined): string {
  if (typeof responseTimeMs !== 'number' || !Number.isFinite(responseTimeMs)) {
    return 'Not available'
  }

  return `${responseTimeMs.toLocaleString()} ms`
}

function getResultTitle(status: ReadinessStatus): string {
  if (status === 'PASS') {
    return 'Website readiness check passed'
  }

  if (status === 'WARNING') {
    return 'Website readiness check needs attention'
  }

  if (status === 'FAIL') {
    return 'Website readiness check failed'
  }

  return 'Website readiness check completed'
}

function CheckResult({ result }: CheckResultProps) {
  const overallStatus = normalizeStatus(result.overallStatus)
  const overallTone = getStatusTone(overallStatus)
  const securityHeaders = Array.isArray(result.securityHeaders) ? result.securityHeaders : []
  const findings = Array.isArray(result.findings) ? result.findings : []
  const httpsStatus =
    typeof result.httpsEnabled === 'boolean'
      ? result.httpsEnabled
        ? 'Enabled'
        : 'Disabled'
      : 'Not available'
  const reachabilityStatus =
    typeof result.reachable === 'boolean'
      ? result.reachable
        ? 'Reachable'
        : 'Not reachable'
      : 'Not available'

  return (
    <section className={`result-card result-${overallTone}`} aria-live="polite">
      <header className="result-header">
        <div>
          <p className="result-eyebrow">Readiness result</p>
          <h2>{getResultTitle(overallStatus)}</h2>
        </div>
        <span className={`badge badge-${overallTone}`}>{overallStatus}</span>
      </header>

      <section className="result-section" aria-labelledby="website-overview-heading">
        <div className="result-section-heading">
          <h3 id="website-overview-heading">Website Overview</h3>
        </div>

        <dl className="result-grid">
          <div className="result-item result-item-wide">
            <dt>Submitted URL</dt>
            <dd className="mono">{result.url || 'Not available'}</dd>
          </div>
          <div className="result-item">
            <dt>Reachability</dt>
            <dd>{reachabilityStatus}</dd>
          </div>
          <div className="result-item">
            <dt>HTTP status code</dt>
            <dd className="mono result-status-code">
              {result.httpStatusCode ?? 'Not available'}
            </dd>
          </div>
          <div className="result-item">
            <dt>HTTPS</dt>
            <dd>{httpsStatus}</dd>
          </div>
          <div className="result-item">
            <dt>Response time</dt>
            <dd>{formatResponseTime(result.responseTimeMs)}</dd>
          </div>
          <div className="result-item">
            <dt>Overall readiness</dt>
            <dd>
              <span className={`badge badge-${overallTone}`}>{overallStatus}</span>
            </dd>
          </div>
        </dl>
      </section>

      <section className="result-section" aria-labelledby="security-headers-heading">
        <div className="result-section-heading">
          <h3 id="security-headers-heading">Security Headers</h3>
          <span className="result-section-meta">
            {securityHeaders.length} {securityHeaders.length === 1 ? 'header' : 'headers'} checked
          </span>
        </div>

        {securityHeaders.length > 0 ? (
          <ul className="header-list">
            {securityHeaders.map((header, index) => {
              const hasPresentFlag = typeof header.present === 'boolean'
              const isPresent = header.present === true
              const tone: StatusTone = hasPresentFlag
                ? isPresent
                  ? 'success'
                  : 'warning'
                : 'neutral'
              const presentLabel = hasPresentFlag
                ? isPresent
                  ? 'Present'
                  : 'Missing'
                : 'Unknown'
              const value = header.value?.trim()

              return (
                <li className="header-row" key={`${header.headerName}-${index}`}>
                  <div className="header-main">
                    <span className="header-name mono">
                      {header.headerName || 'Unnamed header'}
                    </span>
                    {value && <span className="header-value mono">{value}</span>}
                  </div>
                  <span className={`badge badge-${tone}`}>{presentLabel}</span>
                </li>
              )
            })}
          </ul>
        ) : (
          <p className="result-empty">No security header results were returned.</p>
        )}
      </section>

      <section className="result-section" aria-labelledby="findings-heading">
        <div className="result-section-heading">
          <h3 id="findings-heading">Findings and Recommendations</h3>
          <span className="result-section-meta">
            {findings.length} {findings.length === 1 ? 'finding' : 'findings'}
          </span>
        </div>

        {findings.length > 0 ? (
          <ul className="finding-list">
            {findings.map((finding, index) => {
              const findingStatus = normalizeStatus(finding.status)
              const findingTone = getStatusTone(findingStatus)

              return (
                <li className="finding-item" key={`${finding.name}-${index}`}>
                  <div className="finding-heading">
                    <h4>{finding.name || 'Untitled finding'}</h4>
                    <span className={`badge badge-${findingTone}`}>{findingStatus}</span>
                  </div>
                  <p>{finding.explanation || 'No explanation was provided.'}</p>
                  <p className="finding-action">
                    <strong>Suggested action:</strong>{' '}
                    {finding.suggestedAction || 'No suggested action was provided.'}
                  </p>
                </li>
              )
            })}
          </ul>
        ) : (
          <p className="result-empty">No findings were returned for this check.</p>
        )}
      </section>
    </section>
  )
}

export default CheckResult
