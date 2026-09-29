import { request } from './request'
import { clearToken, getToken } from './token'
import { apiErrorMessage } from './errors'
import type { ChatRequest } from '@/types/chat'

export interface StreamEvent {
  runId: string
  requestId: string
  delta?: string
  status?: string
  tool?: string
  chunkCount?: number
}

/** Browser Axios does not expose a streaming body; use authenticated fetch for this one endpoint. */
export async function streamChat(body: ChatRequest, onEvent: (name: string, data: StreamEvent) => void,
  signal: AbortSignal): Promise<void> {
  const response = await fetch(`${import.meta.env.VITE_API_BASE_URL || ''}/api/chat/stream`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream',
      Authorization: `Bearer ${getToken() || ''}` }, body: JSON.stringify(body), signal
  })
  if (!response.ok) {
    if (response.status === 401) {
      clearToken()
      if (window.location.pathname !== '/login') window.location.assign('/login?expired=1')
    }
    const error: { message?: string } = await response.json().catch(() => ({}))
    throw new Error(apiErrorMessage(response.status, error.message))
  }
  if (!response.body || !response.headers.get('content-type')?.includes('text/event-stream'))
    throw new Error('The server did not return a chat stream.')
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let terminal = false
  let failed = false
  function process(): void {
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const block = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      const lines = block.split('\n')
      const name = lines.find(line => line.startsWith('event:'))?.slice(6).trim()
      const data = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n')
      if (name && data) {
        const event = JSON.parse(data) as StreamEvent
        if (typeof event.runId !== 'string' || typeof event.requestId !== 'string') throw new Error('Invalid stream event.')
        onEvent(name, event)
        if (name === 'run.completed' || name === 'run.failed') terminal = true
        if (name === 'run.failed') failed = true
      }
      boundary = buffer.indexOf('\n\n')
    }
  }
  try {
    while (true) {
      const { done, value } = await reader.read()
      buffer = (buffer + decoder.decode(value, { stream: !done })).replace(/\r\n/g, '\n')
      process()
      if (done) break
    }
    if (!terminal) throw new Error('Connection interrupted. Saved history will be reloaded.')
    if (failed) throw new Error('AI service is temporarily unavailable.')
  } finally { reader.releaseLock() }
}

export async function cancelRun(runId: string): Promise<void> {
  await request.post(`/api/chat/runs/${encodeURIComponent(runId)}/cancel`)
}
