import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginResponse, UserRole } from '@/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string>(localStorage.getItem('nurse_token') || '')
  const userInfo = ref<Omit<LoginResponse, 'token' | 'refreshToken' | 'expiresIn'> | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const displayName = computed(() => userInfo.value?.displayName || '医护人员')
  const role = computed<UserRole>(() => userInfo.value?.role || 'NURSE')

  function setLogin(data: LoginResponse) {
    token.value = data.token
    localStorage.setItem('nurse_token', data.token)
    localStorage.setItem('nurse_refresh_token', data.refreshToken)
    userInfo.value = {
      userId: data.userId,
      username: data.username,
      role: data.role,
      displayName: data.displayName
    }
    localStorage.setItem('nurse_user', JSON.stringify(userInfo.value))
  }

  function loadFromStorage() {
    const saved = localStorage.getItem('nurse_user')
    if (saved) {
      try { userInfo.value = JSON.parse(saved) } catch {}
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem('nurse_token')
    localStorage.removeItem('nurse_refresh_token')
    localStorage.removeItem('nurse_user')
  }

  loadFromStorage()

  return { token, userInfo, isLoggedIn, displayName, role, setLogin, logout }
})
