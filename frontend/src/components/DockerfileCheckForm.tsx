import { useState, type FormEvent } from 'react'

const MAX_DOCKERFILE_LENGTH = 100_000

const DOCKERFILE_PLACEHOLDER = `FROM node:20-alpine
WORKDIR /app
COPY . .
USER node
HEALTHCHECK CMD node -e "process.exit(0)"
CMD ["node", "server.js"]`

interface DockerfileCheckFormProps {
  onSubmit: (content: string) => void | Promise<void>
  loading: boolean
}

function DockerfileCheckForm({ onSubmit, loading }: DockerfileCheckFormProps) {
  const [content, setContent] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    if (content.trim() === '') {
      setValidationError('Paste Dockerfile content to analyze.')
      return
    }

    if (content.length > MAX_DOCKERFILE_LENGTH) {
      setValidationError('Dockerfile content must be 100,000 characters or fewer.')
      return
    }

    setValidationError(null)
    void onSubmit(content)
  }

  const isOverLimit = content.length > MAX_DOCKERFILE_LENGTH
  const describedBy = validationError
    ? 'dockerfile-hint dockerfile-error'
    : 'dockerfile-hint'

  return (
    <form className="check-form dockerfile-form" onSubmit={handleSubmit} noValidate>
      <div className="form-heading">
        <label htmlFor="dockerfile-content">Dockerfile content</label>
        <span
          className={`field-hint${isOverLimit ? ' field-hint-over' : ''}`}
          id="dockerfile-hint"
        >
          {content.length.toLocaleString()} / {MAX_DOCKERFILE_LENGTH.toLocaleString()}{' '}
          characters
        </span>
      </div>
      <textarea
        id="dockerfile-content"
        name="content"
        className="dockerfile-input"
        value={content}
        onChange={(event) => setContent(event.target.value)}
        placeholder={DOCKERFILE_PLACEHOLDER}
        rows={14}
        spellCheck={false}
        disabled={loading}
        aria-invalid={validationError ? true : undefined}
        aria-describedby={describedBy}
      />
      <p className="field-hint dockerfile-safety-note">
        The Dockerfile is analyzed as text and is never executed.
      </p>
      {validationError && (
        <p className="field-error" id="dockerfile-error" role="alert">
          {validationError}
        </p>
      )}
      <div className="form-actions">
        <button type="submit" disabled={loading}>
          {loading ? 'Analyzing...' : 'Analyze Dockerfile'}
        </button>
      </div>
    </form>
  )
}

export default DockerfileCheckForm
