import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

const TOKEN_KEY = 'xinya_ops_token'
const USER_KEY = 'xinya_ops_user'

export interface LoginResponse {
  token: string
  refreshToken: string
  expiresIn: number
  userId: string
  username: string
  displayName: string
  role: string
}

export const useUserStore = defineStore('user', () => {
  const token = ref<string>(localStorage.getItem(TOKEN_KEY) || '')

  const _saved = localStorage.getItem(USER_KEY)
  const _parsed = _saved ? JSON.parse(_saved) : null

  const userId = ref<string>(_parsed?.userId || '')
  const username = ref<string>(_parsed?.username || '')
  const displayName = ref<string>(_parsed?.displayName || '')
  const name = computed(() => displayName.value || '运维管理员')
  const role = ref<string>(_parsed?.role || '')
  const roles = computed(() => role.value ? [role.value] : ['ADMIN'])

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => role.value === 'ADMIN')

  function setLogin(data: LoginResponse) {
    token.value = data.token
    userId.value = data.userId
    username.value = data.username
    displayName.value = data.displayName
    role.value = data.role

    localStorage.setItem(TOKEN_KEY, data.token)
    localStorage.setItem(USER_KEY, JSON.stringify({
      userId: data.userId,
      username: data.username,
      displayName: data.displayName,
      role: data.role
    }))
  }

  function logout() {
    token.value = ''
    userId.value = ''
    username.value = ''
    displayName.value = ''
    role.value = ''
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
  }

  return {
    token, userId, username, displayName, name, role, roles,
    isLoggedIn, isAdmin,
    setLogin, logout
  }
})
