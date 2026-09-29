export interface ChatRequest {
  conversationId: string
  message: string
}

export interface ChatResponse {
  reply: string
  requestId: string | null
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  requestId?: string | null
}
