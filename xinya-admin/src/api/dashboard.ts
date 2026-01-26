import request from './request'
import type { DashboardData } from '@/types'

export function getDashboardOverview(): Promise<DashboardData> {
  return request.get('/dashboard/overview')
}
