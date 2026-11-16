import type { Finding } from './finding'

export interface DockerfileAnalysisRequest {
  content: string
}

export interface DockerfileAnalysisResponse {
  overallStatus: string
  findings: Finding[]
}
