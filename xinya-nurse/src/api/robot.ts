import request from './request'
import type { DeviceStatus } from '@/types'

export function getDeviceStatus(patientId?: string): Promise<DeviceStatus> {
  return request.get('/robot/devices', { params: patientId ? { patientId } : undefined })
}
