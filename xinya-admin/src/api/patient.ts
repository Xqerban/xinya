import request from './request'
import type { Patient } from '@/types'

export function getPatients(): Promise<Patient[]> {
  return request.get('/patients')
}

export function getPatient(id: string): Promise<Patient> {
  return request.get(`/patients/${id}`)
}

export function createPatient(data: {
  name: string
  roomNumber?: string
  admissionDate: string
}): Promise<Patient> {
  return request.post('/patients', data)
}

export function updatePatientStage(patientId: string, targetStage: string): Promise<Patient> {
  return request.post('/clinical/transition', { patientId, targetStage })
}
