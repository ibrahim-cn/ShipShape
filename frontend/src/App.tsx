import { useState } from 'react'
import { ApiError, checkWebsite } from './api/websiteCheck'
import CheckResult from './components/CheckResult'
import UrlCheckForm from './components/UrlCheckForm'
import type { WebsiteCheckResponse } from './types/websiteCheck'

function App() {
  const [result, setResult] = useState<WebsiteCheckResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function handleSubmit(url: string) {
    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const response = await checkWebsite(url)
      setResult(response)
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : 'An unexpected error occurred while checking the website.',
      )
    } finally {
      setLoading(false)
    }
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
          <h2>Check a website before deployment</h2>
          <p>
            Submit a public URL to verify that it is reachable and see the HTTP status code it
            returns.
          </p>
        </section>

        <UrlCheckForm onSubmit={handleSubmit} loading={loading} />

        {error && (
          <div className="alert alert-error" role="alert">
            <strong>Check failed</strong>
            <span>{error}</span>
          </div>
        )}

        {result && <CheckResult result={result} />}
      </main>

      <footer className="app-footer">
        <span>ShipShape - Pre-Deployment Readiness Checker</span>
      </footer>
    </div>
  )
}

export default App
