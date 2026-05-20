import request from '@/utils/request'
import type { EducationContent, EducationProgress, ClinicalStage } from '@/types'

export function getContents(params?: {
  stage?: ClinicalStage
  category?: string
  contentType?: string
  keyword?: string
  page?: number
  pageSize?: number
}): Promise<{ list: EducationContent[]; total: number }> {
  return request.get('/education/contents', { params })
}

export function getContent(id: string): Promise<EducationContent> {
  return request.get(`/education/contents/${id}`)
}

export function getEducationProgress(patientId: string): Promise<EducationProgress> {
  return request.get(`/education/progress/${patientId}`)
}
