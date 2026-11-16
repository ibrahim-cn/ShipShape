import type { DockerfileAnalysisResponse } from '../types/dockerfileAnalysis'
import type { ReadinessStatus } from '../utils/status'
import { getStatusTone, normalizeStatus } from '../utils/status'

interface DockerfileResultProps {
  result: DockerfileAnalysisResponse
}

function getResultTitle(status: ReadinessStatus): string {
  if (status === 'PASS') {
    return 'Dockerfile analysis passed'
  }

  if (status === 'WARNING') {
    return 'Dockerfile analysis found issues to review'
  }

  if (status === 'FAIL') {
    return 'Dockerfile analysis failed'
  }

  return 'Dockerfile analysis completed'
}

function DockerfileResult({ result }: DockerfileResultProps) {
  const overallStatus = normalizeStatus(result.overallStatus)
  const overallTone = getStatusTone(overallStatus)
  const findings = Array.isArray(result.findings) ? result.findings : []
  const counts: Record<ReadinessStatus, number> = {
    PASS: 0,
    WARNING: 0,
    FAIL: 0,
    UNKNOWN: 0,
  }

  findings.forEach((finding) => {
    counts[normalizeStatus(finding.status)] += 1
  })

  return (
    <section
      className={`result-card result-${overallTone}`}
      aria-live="polite"
      aria-labelledby="dockerfile-result-heading"
    >
      <header className="result-header">
        <div>
          <p className="result-eyebrow">Dockerfile analysis</p>
          <h2 id="dockerfile-result-heading">{getResultTitle(overallStatus)}</h2>
        </div>
        <span className={`badge badge-${overallTone}`}>{overallStatus}</span>
      </header>

      <section className="result-section" aria-labelledby="dockerfile-summary-heading">
        <div className="result-section-heading">
          <h3 id="dockerfile-summary-heading">Analysis Summary</h3>
          <span className="result-section-meta">
            {findings.length} {findings.length === 1 ? 'finding' : 'findings'}
          </span>
        </div>

        <dl className="result-grid">
          <div className="result-item">
            <dt>Overall result</dt>
            <dd>
              <span className={`badge badge-${overallTone}`}>{overallStatus}</span>
            </dd>
          </div>
          <div className="result-item">
            <dt>Passed checks</dt>
            <dd>{counts.PASS}</dd>
          </div>
          <div className="result-item">
            <dt>Warnings</dt>
            <dd>{counts.WARNING}</dd>
          </div>
          <div className="result-item">
            <dt>Failed checks</dt>
            <dd>{counts.FAIL}</dd>
          </div>
        </dl>

        <p className="result-note">Static text analysis only; the Dockerfile was not executed.</p>
      </section>

      <section className="result-section" aria-labelledby="dockerfile-findings-heading">
        <div className="result-section-heading">
          <h3 id="dockerfile-findings-heading">Findings and Recommendations</h3>
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
          <p className="result-empty">No findings were returned for this analysis.</p>
        )}
      </section>
    </section>
  )
}

export default DockerfileResult
