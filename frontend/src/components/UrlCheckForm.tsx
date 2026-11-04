import { useState, type FormEvent } from 'react'

interface UrlCheckFormProps {
  onSubmit: (url: string) => void | Promise<void>
  loading: boolean
}

function UrlCheckForm({ onSubmit, loading }: UrlCheckFormProps) {
  const [url, setUrl] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const trimmedUrl = url.trim()

    if (trimmedUrl === '') {
      setValidationError('Enter a website URL to check.')
      return
    }

    setValidationError(null)
    void onSubmit(trimmedUrl)
  }

  return (
    <form className="check-form" onSubmit={handleSubmit} noValidate>
      <label htmlFor="website-url">Website URL</label>
      <div className="input-row">
        <input
          id="website-url"
          name="url"
          type="url"
          value={url}
          onChange={(event) => setUrl(event.target.value)}
          placeholder="https://example.com"
          autoComplete="url"
          spellCheck={false}
          disabled={loading}
          aria-invalid={validationError ? true : undefined}
          aria-describedby={validationError ? 'url-error' : undefined}
        />
        <button type="submit" disabled={loading}>
          {loading ? 'Checking...' : 'Check Website'}
        </button>
      </div>
      {validationError && (
        <p className="field-error" id="url-error" role="alert">
          {validationError}
        </p>
      )}
    </form>
  )
}

export default UrlCheckForm
