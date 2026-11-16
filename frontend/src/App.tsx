import { useState, type KeyboardEvent } from 'react'
import { analyzeDockerfile } from './api/dockerfileAnalysis'
import { ApiError } from './api/http'
import { checkWebsite } from './api/websiteCheck'
import CheckResult from './components/CheckResult'
import DockerfileCheckForm from './components/DockerfileCheckForm'
import DockerfileResult from './components/DockerfileResult'
import UrlCheckForm from './components/UrlCheckForm'
import type { DockerfileAnalysisResponse } from './types/dockerfileAnalysis'
import type { WebsiteCheckResponse } from './types/websiteCheck'

type CheckMode = 'website' | 'dockerfile'

function App() {
  const [activeCheck, setActiveCheck] = useState<CheckMode>('website')
  const [websiteResult, setWebsiteResult] = useState<WebsiteCheckResponse | null>(null)
  const [websiteError, setWebsiteError] = useState<string | null>(null)
  const [websiteLoading, setWebsiteLoading] = useState(false)
  const [dockerfileResult, setDockerfileResult] = useState<DockerfileAnalysisResponse | null>(null)
  const [dockerfileError, setDockerfileError] = useState<string | null>(null)
  const [dockerfileLoading, setDockerfileLoading] = useState(false)

  async function handleWebsiteSubmit(url: string) {
    setWebsiteLoading(true)
    setWebsiteError(null)
    setWebsiteResult(null)

    try {
      const response = await checkWebsite(url)
      setWebsiteResult(response)
    } catch (err) {
      setWebsiteError(
        err instanceof ApiError
          ? err.message
          : 'An unexpected error occurred while checking the website.',
      )
    } finally {
      setWebsiteLoading(false)
    }
  }

  async function handleDockerfileSubmit(content: string) {
    setDockerfileLoading(true)
    setDockerfileError(null)
    setDockerfileResult(null)

    try {
      const response = await analyzeDockerfile(content)
      setDockerfileResult(response)
    } catch (err) {
      setDockerfileError(
        err instanceof ApiError
          ? err.message
          : 'An unexpected error occurred while analyzing the Dockerfile.',
      )
    } finally {
      setDockerfileLoading(false)
    }
  }

  function handleTabKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') {
      return
    }

    event.preventDefault()
    const nextCheck: CheckMode = activeCheck === 'website' ? 'dockerfile' : 'website'
    setActiveCheck(nextCheck)
    document.getElementById(`${nextCheck}-tab`)?.focus()
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">
            SS
          </span>
          <div>
            <h1>ShipShape</h1>
            <p>Pre-Deployment Readiness Checker</p>
          </div>
        </div>
      </header>

      <main className="app-main">
        <section className="intro">
          <h2>
            {activeCheck === 'website'
              ? 'Check a website before deployment'
              : 'Analyze a Dockerfile before deployment'}
          </h2>
          <p>
            {activeCheck === 'website'
              ? 'Submit a public URL to verify that it is reachable and review its HTTP and security readiness.'
              : 'Paste Dockerfile contents to run rule-based static checks. The file is analyzed as text and is never executed.'}
          </p>
        </section>

        <nav className="check-tabs" role="tablist" aria-label="Readiness check type">
          <button
            type="button"
            role="tab"
            id="website-tab"
            className="check-tab"
            aria-selected={activeCheck === 'website'}
            aria-controls="website-panel"
            tabIndex={activeCheck === 'website' ? 0 : -1}
            onClick={() => setActiveCheck('website')}
            onKeyDown={handleTabKeyDown}
          >
            Website Check
          </button>
          <button
            type="button"
            role="tab"
            id="dockerfile-tab"
            className="check-tab"
            aria-selected={activeCheck === 'dockerfile'}
            aria-controls="dockerfile-panel"
            tabIndex={activeCheck === 'dockerfile' ? 0 : -1}
            onClick={() => setActiveCheck('dockerfile')}
            onKeyDown={handleTabKeyDown}
          >
            Dockerfile Analysis
          </button>
        </nav>

        {activeCheck === 'website' ? (
          <div role="tabpanel" id="website-panel" aria-labelledby="website-tab">
            <UrlCheckForm onSubmit={handleWebsiteSubmit} loading={websiteLoading} />

            {websiteError && (
              <div className="alert alert-error" role="alert">
                <strong>Check failed</strong>
                <span>{websiteError}</span>
              </div>
            )}

            {websiteResult && <CheckResult result={websiteResult} />}
          </div>
        ) : (
          <div role="tabpanel" id="dockerfile-panel" aria-labelledby="dockerfile-tab">
            <DockerfileCheckForm
              onSubmit={handleDockerfileSubmit}
              loading={dockerfileLoading}
            />

            {dockerfileError && (
              <div className="alert alert-error" role="alert">
                <strong>Analysis failed</strong>
                <span>{dockerfileError}</span>
              </div>
            )}

            {dockerfileResult && <DockerfileResult result={dockerfileResult} />}
          </div>
        )}
      </main>

      <footer className="app-footer">
        <span>ShipShape - Pre-Deployment Readiness Checker</span>
      </footer>
    </div>
  )
}

export default App
