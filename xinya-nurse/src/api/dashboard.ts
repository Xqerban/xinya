import request from './request'
import type { DashboardOverview, PsychDistribution } from '@/types'

export function getOverview(): Promise<DashboardOverview> {
  return request.get('/dashboard/overview')
}

export function getPsychDistribution(): Promise<PsychDistribution> {
  return request.get('/dashboard/psych-distribution')
}

export function getPatientReport(patientId: string): Promise<any> {
  return request.get(`/dashboard/patient-report/${patientId}`)
}
