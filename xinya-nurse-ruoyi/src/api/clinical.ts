import request from '@/utils/request'
import type { ClinicalStageInfo, StageHistory, TransitionRequest, PatientDto } from '@/types'

export function getCurrentStage(patientId: string): Promise<ClinicalStageInfo> {
  return request.get(`/clinical/stage/${patientId}`)
}

export function transitionStage(data: TransitionRequest): Promise<PatientDto> {
  return request.post('/clinical/transition', data)
}

export function getStageHistory(patientId: string): Promise<StageHistory[]> {
  return request.get(`/clinical/history/${patientId}`)
}
