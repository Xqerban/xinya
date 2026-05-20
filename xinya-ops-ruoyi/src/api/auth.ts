import request from '@/utils/request'
import type { LoginResponse } from '@/store/modules/user'

export function login(username: string, password: string): Promise<LoginResponse> {
  return request.post<any, { data: LoginResponse }>('/auth/login', { username, password })
    .then(res => res.data)
}

export function refreshToken(token: string): Promise<LoginResponse> {
  return request.post<any, { data: LoginResponse }>('/auth/refresh', { refreshToken: token })
    .then(res => res.data)
}

export function logout() {
  return request.post('/auth/logout')
}
