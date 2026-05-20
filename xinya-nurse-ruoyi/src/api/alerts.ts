import request from '@/utils/request'
import type { AlertsResponse, AlertDto, AlertLevel } from '@/types'

export function getAlerts(params?: {
  resolved?: boolean
  level?: AlertLevel
  patientId?: string
  page?: number
  pageSize?: number
}): Promise<AlertsResponse> {
  return request.get('/alerts', { params })
}

export function resolveAlert(id: string, resolvedNote: string): Promise<AlertDto> {
  return request.put(`/alerts/${id}/resolve`, { resolvedNote })
}

export function createAlert(data: {
  patientId: string
  alertType: string
  level: AlertLevel
  message: string
}): Promise<AlertDto> {
  return request.post('/alerts', data)
}
