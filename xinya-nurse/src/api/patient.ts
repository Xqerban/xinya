import request from './request'
import type { PatientDto, PatientDetail, CreatePatientRequest, UpdatePatientRequest, EnergyTrend, PageResult } from '@/types'

export function getPatients(params?: {
  page?: number
  pageSize?: number
  stage?: string
  keyword?: string
}): Promise<PageResult<PatientDto>> {
  return request.get('/patients', { params })
}

export function getPatient(id: string): Promise<PatientDto> {
  return request.get(`/patients/${id}`)
}

export function getPatientDetail(id: string): Promise<PatientDetail> {
  return request.get(`/patients/${id}/detail`)
}

export function createPatient(data: CreatePatientRequest): Promise<PatientDto> {
  return request.post('/patients', data)
}

export function updatePatient(id: string, data: UpdatePatientRequest): Promise<PatientDto> {
  return request.put(`/patients/${id}`, data)
}

export function deletePatient(id: string): Promise<null> {
  return request.delete(`/patients/${id}`)
}

export function getEnergyTrend(id: string, days = 14): Promise<EnergyTrend> {
  return request.get(`/patients/${id}/energy-trend`, { params: { days } })
}
