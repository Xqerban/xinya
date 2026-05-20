import request from '@/utils/request'

export interface CrisisKeywordDto {
  id: number
  keyword: string
  crisisLevel: string
  isActive: boolean
  createdBy: string | null
  createdAt: string | null
}

export interface ProQuestionDto {
  id: string
  stage: string
  title: string
  type: string
  options: string | null
  scaleMin: number | null
  scaleMax: number | null
  minLabel: string | null
  maxLabel: string | null
  symptomKey: string | null
  sortOrder: number
  isActive: boolean
}

export function listCrisisKeywords() {
  return request.get<any, { data: CrisisKeywordDto[] }>('/admin/crisis-keywords')
    .then(res => res.data)
}

export function createCrisisKeyword(data: { keyword: string; crisisLevel: string }) {
  return request.post<any, { data: CrisisKeywordDto }>('/admin/crisis-keywords', data)
    .then(res => res.data)
}

export function deleteCrisisKeyword(id: number) {
  return request.delete(`/admin/crisis-keywords/${id}`)
}

export function listProQuestions(stage?: string) {
  return request.get<any, { data: ProQuestionDto[] }>('/admin/pro-questions', {
    params: stage ? { stage } : {}
  }).then(res => res.data)
}

export function updateProQuestion(id: string, data: { sortOrder?: number; isActive?: boolean }) {
  return request.put<any, { data: ProQuestionDto }>(`/admin/pro-questions/${id}`, data)
    .then(res => res.data)
}
