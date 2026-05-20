import request from '@/utils/request'
import type { PageResult } from './users'

export interface EducationContentDto {
  id: string
  title: string
  stage: string | null
  category: string
  description: string | null
  contentType: string
  durationSeconds: number | null
  thumbnailUrl: string | null
  mediaUrl: string | null
  tags: string | null
  sortOrder: number
  isActive: boolean
  syncedToClinical: boolean
  createdBy: string | null
  createdAt: string
  updatedAt: string
}

export interface CreateEducationContentRequest {
  title: string
  stage?: string
  category: string
  description?: string
  contentType: string
  durationSeconds?: number
  thumbnailUrl?: string
  mediaUrl?: string
  tags?: string
  sortOrder?: number
}

export interface UpdateEducationContentRequest {
  title?: string
  stage?: string
  category?: string
  description?: string
  contentType?: string
  durationSeconds?: number
  thumbnailUrl?: string
  mediaUrl?: string
  tags?: string
  sortOrder?: number
  isActive?: boolean
}

export function listEducationContents(params?: {
  stage?: string
  category?: string
  contentType?: string
  keyword?: string
  page?: number
  pageSize?: number
}) {
  return request.get<any, { data: PageResult<EducationContentDto> }>('/education/contents', { params })
    .then(res => res.data)
}

export function getEducationContent(id: string) {
  return request.get<any, { data: EducationContentDto }>(`/education/contents/${id}`)
    .then(res => res.data)
}

export function createEducationContent(data: CreateEducationContentRequest) {
  return request.post<any, { data: EducationContentDto }>('/education/contents', data)
    .then(res => res.data)
}

export function updateEducationContent(id: string, data: UpdateEducationContentRequest) {
  return request.put<any, { data: EducationContentDto }>(`/education/contents/${id}`, data)
    .then(res => res.data)
}

export function deactivateEducationContent(id: string) {
  return request.delete(`/education/contents/${id}`)
}
