import type { WebsiteCheckRequest, WebsiteCheckResponse } from '../types/websiteCheck'

export class ApiError extends Error {
  readonly status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function readJson(response: Response): Promise<unknown> {
  const contentType = response.headers.get('content-type') ?? ''

  if (!contentType.includes('application/json')) {
    return null
  }

  try {
    return await response.json()
  } catch {
    return null
  }
}

function getErrorMessage(payload: unknown, status: number): string {
  if (payload && typeof payload === 'object' && 'error' in payload) {
    const error = (payload as { error?: unknown }).error

    if (typeof error === 'string' && error.trim() !== '') {
      return error
    }
  }

  return `Request failed with HTTP ${status}.`
}

export async function checkWebsite(url: string): Promise<WebsiteCheckResponse> {
  const body: WebsiteCheckRequest = { url }

  let response: Response

  try {
    response = await fetch('/api/checks/website', {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(body),
    })
  } catch {
    throw new ApiError(
      'Could not reach the ShipShape backend. Make sure it is running on port 8080.',
      0,
    )
  }

  const payload = await readJson(response)

  if (!response.ok) {
    throw new ApiError(getErrorMessage(payload, response.status), response.status)
  }

  if (!payload || typeof payload !== 'object') {
    throw new ApiError('The backend returned an unexpected response.', response.status)
  }

  return payload as WebsiteCheckResponse
}
