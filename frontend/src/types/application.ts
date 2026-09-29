export type ApplicationStatus = 'SAVED' | 'APPLIED' | 'INTERVIEW' | 'OFFER' | 'REJECTED' | 'WITHDRAWN'

export interface ApplicationSummary {
  id: number
  jobId: number
  company: string | null
  position: string | null
  status: ApplicationStatus
  applyTime: string | null
  remark: string | null
}
