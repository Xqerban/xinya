import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginResponse } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('ops_token') || '')
  const refreshTokenVal = ref<string>(localStorage.getItem('ops_refresh_token') || '')
  const userId = ref<string>(localStorage.getItem('ops_user_id') || '')
  const username = ref<string>(localStorage.getItem('ops_username') || '')
  const displayName = ref<string>(localStorage.getItem('ops_display_name') || '')
  const role = ref<string>(localStorage.getItem('ops_role') || '')

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')

  function setLogin(data: LoginResponse) {
    token.value = data.token
    refreshTokenVal.value = data.refreshToken
    userId.value = data.userId
    username.value = data.username
    displayName.value = data.displayName
    role.value = data.role

    localStorage.setItem('ops_token', data.token)
    localStorage.setItem('ops_refresh_token', data.refreshToken)
    localStorage.setItem('ops_user_id', data.userId)
    localStorage.setItem('ops_username', data.username)
    localStorage.setItem('ops_display_name', data.displayName)
    localStorage.setItem('ops_role', data.role)
  }

  function clearLogin() {
    token.value = ''
    refreshTokenVal.value = ''
    userId.value = ''
    username.value = ''
    displayName.value = ''
    role.value = ''

    localStorage.removeItem('ops_token')
    localStorage.removeItem('ops_refresh_token')
    localStorage.removeItem('ops_user_id')
    localStorage.removeItem('ops_username')
    localStorage.removeItem('ops_display_name')
    localStorage.removeItem('ops_role')
  }

  return {
    token, refreshToken: refreshTokenVal, userId, username, displayName, role,
    isLoggedIn, isAdmin,
    setLogin, clearLogin
  }
})
