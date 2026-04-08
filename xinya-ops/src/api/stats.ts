import request from './request'

export interface StatsOverview {
  totalPatients: number
  activePatients: number
  avgPsychEnergy: number
  checkinRate: number
  stageDistribution: Record<string, number>
  recentAlerts: Array<{
    patientId: string
    patientName: string
    level: string
    message: string
    createdAt: string
  }>
}

export interface PsychDistribution {
  healthy: number
  mild: number
  warning: number
}

export interface SymptomHeatmap {
  dates: string[]
  symptoms: string[]
  matrix: number[][]
}

export function getStatsOverview() {
  return request.get<any, { data: StatsOverview }>('/stats/overview')
    .then(res => res.data)
}

export function getPsychDistribution() {
  return request.get<any, { data: PsychDistribution }>('/stats/psych-distribution')
    .then(res => res.data)
}

export function getSymptomHeatmap(days?: number) {
  return request.get<any, { data: SymptomHeatmap }>('/stats/symptom-heatmap', {
    params: days ? { days } : {}
  }).then(res => res.data)
}
