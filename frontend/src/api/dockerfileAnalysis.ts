import { postJson } from './http'
import type { DockerfileAnalysisRequest, DockerfileAnalysisResponse } from '../types/dockerfileAnalysis'

export async function analyzeDockerfile(content: string): Promise<DockerfileAnalysisResponse> {
  const body: DockerfileAnalysisRequest = { content }

  return postJson<DockerfileAnalysisResponse>('/api/checks/dockerfile', body)
}
