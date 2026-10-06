import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { clearToken, getToken, setToken } from '@/api/token'
import * as authApi from '@/api/auth'
import { useChatStore } from './chat'
import type { AuthUser } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const user = ref<AuthUser | null>(null)
  const initialized = ref(false)

  let initializationPromise: Promise<void> | null = null

  const isAuthenticated = computed(() => {
    return Boolean(user.value && getToken())
  })

  function syncChatUser(userId: number | null): void {
    try {
      useChatStore().setUser(userId)
    } catch (error) {
      console.error('Failed to sync chat user:', error)
    }
  }

  async function initializeAuth(): Promise<void> {
    if (initialized.value) {
      return
    }

    if (initializationPromise) {
      await initializationPromise
      return
    }

    initializationPromise = (async () => {
      const token = getToken()

      if (!token) {
        user.value = null
        initialized.value = true
        return
      }

      try {
        const currentUser = await authApi.me()
        user.value = currentUser
        syncChatUser(currentUser.id)
      } catch (error) {
        console.error('Failed to restore authentication:', error)
        clearToken()
        user.value = null
        syncChatUser(null)
      } finally {
        initialized.value = true
      }
    })()

    try {
      await initializationPromise
    } finally {
      initializationPromise = null
    }
  }

  async function login(email: string, password: string): Promise<void> {
    const result = await authApi.login(email, password)

    setToken(result.token)
    user.value = result.user
    initialized.value = true

    syncChatUser(result.user.id)
  }

  async function register(
    username: string,
    email: string,
    password: string
  ): Promise<void> {
    await authApi.register(username, email, password)
  }

  function logout(): void {
    clearToken()
    user.value = null
    initialized.value = true
    syncChatUser(null)
  }

  return {
    user,
    initialized,
    isAuthenticated,
    initializeAuth,
    login,
    register,
    logout
  }
})