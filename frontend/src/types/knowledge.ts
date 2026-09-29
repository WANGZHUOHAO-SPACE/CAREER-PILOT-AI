export interface KnowledgeStatus {
  documentCount: number
  chunkCount: number
  vectorStoreType: string
}

export interface UploadResult {
  fileName: string
  status: string
  chunks: number
}
