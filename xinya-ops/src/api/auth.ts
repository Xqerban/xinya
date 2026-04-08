import request from './request'

export interface LoginResponse {
  token: string
  refreshToken: string
  expiresIn: number
  userId: string
  username: string
  displayName: string
  role: string
}

export function login(username: string, password: string) {
  return request.post<any, { data: LoginResponse }>('/auth/login', { username, password })
    .then(res => res.data)
}

export function refreshToken(refreshToken: string) {
  return request.post<any, { data: LoginResponse }>('/auth/refresh', { refreshToken })
    .then(res => res.data)
}

export function logout() {
  return request.post('/auth/logout')
}
