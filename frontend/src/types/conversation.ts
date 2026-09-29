export interface Conversation {
  conversationId: string
  title: string
  createdAt: string
  updatedAt: string
  lastMessageAt: string | null
  messageCount: number
}
export interface ConversationMessage {
  id: number
  role: 'USER' | 'ASSISTANT'
  content: string
  sequenceNo: number
  createdAt: string
  requestId: string | null
  status: 'SENT' | 'COMPLETED'
}
export interface Page<T> {
  page: number
  size: number
  hasMore: boolean
  items: T[]
}
