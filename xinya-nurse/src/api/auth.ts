import request from './request'
import type { LoginResponse, BindCodeResponse } from '@/types'

export function login(username: string, password: string): Promise<LoginResponse> {
  return request.post('/auth/login', { username, password })
}

export function logout(): Promise<null> {
  return request.post('/auth/logout')
}

export function generateBindCode(patientId: string): Promise<BindCodeResponse> {
  return request.post('/auth/robot/bind-code', { patientId })
}

export function unbindDevice(deviceId: string, reason: string, operatorNote?: string): Promise<any> {
  return request.post('/auth/robot/unbind', { deviceId, reason, operatorNote })
}

export function getDeviceBindStatus(deviceId: string): Promise<any> {
  return request.get('/auth/robot/bind-status', { params: { deviceId } })
}

export function refreshToken(refreshToken: string): Promise<LoginResponse> {
  return request.post('/auth/refresh', { refreshToken })
}
