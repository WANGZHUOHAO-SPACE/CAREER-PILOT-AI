import axios from 'axios'
import { clearToken, getToken } from './token'
import { apiErrorMessage } from './errors'

export const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 120_000
})

request.interceptors.request.use(config => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  response => response,
  error => {
    if (!axios.isAxiosError(error)) return Promise.reject(error)
    if (error.response?.status === 401 && !/\/api\/auth\/(login|register)$/.test(error.config?.url || '')) {
      clearToken()
      if (window.location.pathname !== '/login') window.location.assign('/login?expired=1')
    }
    const serverMessage = (error.response?.data as { message?: unknown } | undefined)?.message
    const message = error.code === 'ECONNABORTED'
        ? 'Request timed out. Please try again.'
        : error.response
          ? apiErrorMessage(error.response.status, serverMessage)
          : 'Cannot reach the backend. Check that Spring Boot is running.'
    return Promise.reject(new Error(message))
  }
)
