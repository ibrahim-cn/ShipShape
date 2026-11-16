import type { Finding } from './finding'

export interface WebsiteCheckRequest {
  url: string
}

export interface HeaderCheckResult {
  headerName: string
  present: boolean
  value: string | null
}

export interface WebsiteCheckResponse {
  url: string
  reachable: boolean
  httpStatusCode: number | null
  responseTimeMs: number | null
  httpsEnabled: boolean
  securityHeaders: HeaderCheckResult[]
  overallStatus: string
  findings: Finding[]
}
