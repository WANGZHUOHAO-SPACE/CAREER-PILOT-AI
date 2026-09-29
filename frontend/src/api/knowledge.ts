import { request } from './request'
import type { KnowledgeStatus, UploadResult } from '@/types/knowledge'

export async function getKnowledgeStatus(): Promise<KnowledgeStatus> {
  const { data } = await request.get<KnowledgeStatus>('/api/knowledge/status')
  return data
}

export async function uploadKnowledge(file: File): Promise<UploadResult> {
  const form = new FormData()
  form.append('file', file)
  const { data } = await request.post<UploadResult>('/api/knowledge/upload', form)
  return data
}

export async function clearKnowledge(): Promise<KnowledgeStatus> {
  const { data } = await request.delete<KnowledgeStatus>('/api/knowledge')
  return data
}
