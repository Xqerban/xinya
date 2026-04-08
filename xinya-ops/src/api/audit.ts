import request from './request'
import type { PageResult } from './users'

export interface AuditLogDto {
  id: number
  operatorId: string | null
  operatorName: string | null
  action: string
  targetType: string | null
  targetId: string | null
  detail: string | null
  ipAddress: string | null
  createdAt: string
}

export function listAuditLogs(params?: {
  userId?: string
  action?: string
  targetType?: string
  targetId?: string
  startDate?: string
  endDate?: string
  page?: number
  pageSize?: number
}) {
  return request.get<any, { data: PageResult<AuditLogDto> }>('/admin/audit-logs', { params })
    .then(res => res.data)
}
