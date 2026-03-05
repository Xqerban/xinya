import request from './request'
import type { PageResult, SymptomTrend } from '@/types'

export function getProHistory(params: {
  patientId: string
  startDate?: string
  endDate?: string
  page?: number
  pageSize?: number
}): Promise<any> {
  return request.get('/pro/history', { params })
}

export function getSymptomTrend(params: {
  patientId: string
  questionId: string
  days?: number
}): Promise<SymptomTrend> {
  return request.get('/pro/symptom-trend', { params })
}
