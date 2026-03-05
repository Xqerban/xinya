import request from './request'
import type { HopeTreeDto, GrowthHistoryItem, PageResult } from '@/types'

export function getHopeTree(patientId: string): Promise<HopeTreeDto> {
  return request.get(`/hopetree/${patientId}`)
}

export function getGrowthHistory(patientId: string, params?: { page?: number; pageSize?: number }): Promise<PageResult<GrowthHistoryItem>> {
  return request.get(`/hopetree/${patientId}/history`, { params })
}
