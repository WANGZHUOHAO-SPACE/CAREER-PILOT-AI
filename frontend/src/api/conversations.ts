import { request } from './request'
import type { Conversation, ConversationMessage, Page } from '@/types/conversation'

export async function createConversation(): Promise<Conversation> {
  return (await request.post<Conversation>('/api/conversations')).data
}
export async function listConversations(page = 0): Promise<Page<Conversation>> {
  return (await request.get<Page<Conversation>>('/api/conversations', { params: { page, size: 20 } })).data
}
export async function getMessages(id: string, page = 0): Promise<Page<ConversationMessage>> {
  return (await request.get<Page<ConversationMessage>>(`/api/conversations/${encodeURIComponent(id)}/messages`,
    { params: { page, size: 100 } })).data
}
export async function renameConversation(id: string, title: string): Promise<Conversation> {
  return (await request.patch<Conversation>(`/api/conversations/${encodeURIComponent(id)}`, { title })).data
}
export async function deleteConversation(id: string): Promise<void> {
  await request.delete(`/api/conversations/${encodeURIComponent(id)}`)
}
