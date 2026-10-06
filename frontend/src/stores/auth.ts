import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { clearToken, getToken, setToken } from '@/api/token'
import * as authApi from '@/api/auth'
import { useChatStore } from './chat'
import type { AuthUser } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const isAuthenticated = computed(() => !!user.value && !!getToken())

  async function fetchCurrentUser(): Promise<boolean> {
    if (!getToken()) return false
    try {
      user.value = await authApi.me()
      useChatStore().setUser(user.value.id)
      return true
    } catch {
      logout()
      return false
    }
  }

  async function login(email: string, password: string): Promise<void> {
    const result = await authApi.login(email, password)
    setToken(result.token)
    user.value = result.user
    useChatStore().setUser(result.user.id)
  }

  async function register(username: string, email: string, password: string): Promise<void> {
    await authApi.register(username, email, password)
  }

  function logout(): void {
    clearToken()
    user.value = null
    useChatStore().setUser(null)
  }

  return { user, isAuthenticated, fetchCurrentUser, login, register, logout }
})
