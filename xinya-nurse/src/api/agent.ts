import request from './request'
import type { ConversationItem, PageResult } from '@/types'

export function getConversationHistory(params: {
  patientId: string
  agentType?: 'psych' | 'nurse'
  page?: number
  pageSize?: number
}): Promise<PageResult<ConversationItem>> {
  return request.get('/agent/history', { params })
}
