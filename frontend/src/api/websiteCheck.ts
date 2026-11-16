import { postJson } from './http'
import type { WebsiteCheckRequest, WebsiteCheckResponse } from '../types/websiteCheck'

export async function checkWebsite(url: string): Promise<WebsiteCheckResponse> {
  const body: WebsiteCheckRequest = { url }

  return postJson<WebsiteCheckResponse>('/api/checks/website', body)
}
