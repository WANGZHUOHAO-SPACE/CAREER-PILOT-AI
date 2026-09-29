import { request } from './request'

export interface AuthUser { id: number; username: string; email: string; displayName: string }
export interface LoginResponse { token: string; user: AuthUser }

export async function login(email: string, password: string): Promise<LoginResponse> {
  const { data } = await request.post<LoginResponse>('/api/auth/login', { email, password })
  return data
}

export async function register(username: string, email: string, password: string): Promise<AuthUser> {
  const { data } = await request.post<AuthUser>('/api/auth/register', { username, email, password })
  return data
}

export async function me(): Promise<AuthUser> {
  const { data } = await request.get<AuthUser>('/api/auth/me')
  return data
}
