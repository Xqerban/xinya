import request from '@/utils/request'

export interface UserDto {
  id: string
  username: string
  displayName: string
  role: string
  phone: string | null
  enabled: boolean
  lastLoginAt: string | null
  createdAt: string
}

export interface CreateUserRequest {
  username: string
  password: string
  displayName: string
  role: string
  phone?: string
}

export interface UpdateUserRequest {
  displayName?: string
  role?: string
  phone?: string
  password?: string
}

export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

export function listUsers(params?: { role?: string; page?: number; pageSize?: number }) {
  return request.get<any, { data: PageResult<UserDto> }>('/admin/users', { params })
    .then(res => res.data)
}

export function createUser(data: CreateUserRequest) {
  return request.post<any, { data: UserDto }>('/admin/users', data)
    .then(res => res.data)
}

export function updateUser(id: string, data: UpdateUserRequest) {
  return request.put<any, { data: UserDto }>(`/admin/users/${id}`, data)
    .then(res => res.data)
}

export function deactivateUser(id: string) {
  return request.post(`/admin/users/${id}/deactivate`)
}

export function deleteUser(id: string) {
  return request.delete(`/admin/users/${id}`)
}
