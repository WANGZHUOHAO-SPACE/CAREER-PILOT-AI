import { request } from './request'
import type { JobSummary } from '@/types/job'

export async function getJobs(): Promise<JobSummary[]> {
  const { data } = await request.get<JobSummary[]>('/api/jobs')
  return data
}
