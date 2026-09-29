import { request } from './request'
import type { ChatRequest, ChatResponse } from '@/types/chat'

export async function sendChat(payload: ChatRequest): Promise<ChatResponse> {
  const { data } = await request.post<ChatResponse>('/api/chat', payload)
  return data
}
