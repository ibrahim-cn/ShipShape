export type ReadinessStatus = 'PASS' | 'WARNING' | 'FAIL' | 'UNKNOWN'
export type StatusTone = 'success' | 'warning' | 'failure' | 'neutral'

export function normalizeStatus(status: string | null | undefined): ReadinessStatus {
  const normalized = status?.trim().toUpperCase()

  if (normalized === 'PASS' || normalized === 'WARNING' || normalized === 'FAIL') {
    return normalized
  }

  return 'UNKNOWN'
}

export function getStatusTone(status: ReadinessStatus): StatusTone {
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
