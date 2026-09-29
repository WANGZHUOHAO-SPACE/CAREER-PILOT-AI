import { request } from './request'
import type { ApplicationStatus, ApplicationSummary } from '@/types/application'

export async function getApplications(status?: ApplicationStatus): Promise<ApplicationSummary[]> {
  const { data } = await request.get<ApplicationSummary[]>('/api/applications', {
    params: status ? { status } : undefined
  })
  return data
}
