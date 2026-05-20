import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { LoginResponse, UserRole } from '@/types'

const TOKEN_KEY = 'xinya_nurse_token'
const REFRESH_KEY = 'xinya_nurse_refresh_token'
const USER_KEY = 'xinya_nurse_user'

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')
  const userInfo = ref<Omit<LoginResponse, 'token' | 'refreshToken' | 'expiresIn'> | null>(null)

  const isLoggedIn = computed(() => !!token.value)
  const displayName = computed(() => userInfo.value?.displayName || '医护人员')
  const name = computed(() => userInfo.value?.displayName || '医护人员')
  const role = computed<UserRole>(() => userInfo.value?.role || 'NURSE')
  const roles = computed<UserRole[]>(() => userInfo.value ? [userInfo.value.role] : ['NURSE'])

  function setLogin(data: LoginResponse) {
    token.value = data.token
    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(REFRESH_KEY, data.refreshToken)
    userInfo.value = {
      userId: data.userId,
      username: data.username,
      role: data.role,
      displayName: data.displayName
    }
    localStorage.setItem(USER_KEY, JSON.stringify(userInfo.value))
  }

  function loadFromStorage() {
    const saved = localStorage.getItem(USER_KEY)
    if (saved) {
      try { userInfo.value = JSON.parse(saved) } catch {}
    }
  }

  function logout() {
    token.value = ''
    userInfo.value = null
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(REFRESH_KEY)
    localStorage.removeItem(USER_KEY)
  }

  loadFromStorage()

  return { token, userInfo, isLoggedIn, displayName, name, role, roles, setLogin, logout }
})
