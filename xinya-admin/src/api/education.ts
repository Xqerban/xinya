import request from './request'
import type { EducationContent } from '@/types'

export function getEducationContents(params?: {
  category?: string
  page?: number
  pageSize?: number
}): Promise<{ contents: EducationContent[]; total: number }> {
  return request.get('/education/contents', { params })
}
