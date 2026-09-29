import { request } from './request'
import type { RecentRequests, ToolStatistics, TraceResponse, UsageRange, UsageSummary } from '@/types/observability'

export async function getUsageSummary(range: UsageRange): Promise<UsageSummary> {
  const { data } = await request.get<UsageSummary>('/api/observability/summary', { params: { range } })
  return data
}

export async function getRecentRequests(page = 1, size = 20, sort: 'recent' | 'slowest' = 'recent'): Promise<RecentRequests> {
  const { data } = await request.get<RecentRequests>('/api/observability/requests', { params: { page, size, sort } })
  return data
}

export async function getToolStatistics(range: UsageRange): Promise<ToolStatistics[]> {
  const { data } = await request.get<ToolStatistics[]>('/api/observability/tools', { params: { range } })
  return data
}

export async function getTrace(requestId: string): Promise<TraceResponse> {
  const { data } = await request.get<TraceResponse>(`/api/observability/traces/${encodeURIComponent(requestId)}`)
  return data
}
