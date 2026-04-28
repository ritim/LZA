import { postJson } from './client';
import type { LoginResponse, LoginRequest, RefreshRequest, LogoutRequest } from './types';

export function login(username: string, password: string): Promise<LoginResponse> {
  const body: LoginRequest = { username, password };
  return postJson<LoginResponse, LoginRequest>('/api/v1/auth/login', body);
}

export function refresh(refreshToken: string): Promise<LoginResponse> {
  const body: RefreshRequest = { refreshToken };
  return postJson<LoginResponse, RefreshRequest>('/api/v1/auth/refresh', body);
}

export async function logout(refreshToken: string): Promise<void> {
  const body: LogoutRequest = { refreshToken };
  await postJson<void, LogoutRequest>('/api/v1/auth/logout', body);
}
