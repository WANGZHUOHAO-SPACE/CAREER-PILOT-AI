export type UsageRange = 'today' | '7d' | '30d'

export interface UsageSummary {
  totalRequests: number
  successfulRequests: number
  failedRequests: number
  totalTokens: number | null
  averageLatencyMs: number
  toolCalls: number
  ragRequests: number
}

export interface AiRequest {
  requestId: string
  model: string | null
  status: 'SUCCESS' | 'FAILED' | 'CANCELLED'
  latencyMs: number
  totalTokens: number | null
  toolCallCount: number
  ragUsed: boolean
  createdAt: string
  timeToFirstTokenMs: number | null
  streamDurationMs: number | null
  outputChunkCount: number | null
  cancelled: boolean
}

export interface RecentRequests {
  page: number
  size: number
  hasMore: boolean
  items: AiRequest[]
}

export interface ToolStatistics {
  toolName: string
  callCount: number
  successCount: number
  failureCount: number
  avgLatencyMs: number
}

export interface TraceEvent {
  type: string
  name: string
  status: string
  durationMs: number
  topK: number | null
  chunkCount: number | null
  memoryMessageCount: number | null
  createdAt: string
}

export interface TraceResponse {
  request: AiRequest
  provider: string
  promptVersion: string
  errorType: string | null
  tokenUsage: { input: number | null; output: number | null; total: number | null }
  events: TraceEvent[]
}
