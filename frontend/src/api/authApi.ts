import { postJson } from './httpClient';
import type { LoginResponse } from '../types/auth';

// POST /api/auth/register
export function register(username: string, password: string, nickname: string): Promise<void> {
  return postJson<void>('/api/auth/register', { username, password, nickname });
}

// POST /api/auth/login
export function login(username: string, password: string): Promise<LoginResponse> {
  return postJson<LoginResponse>('/api/auth/login', { username, password });
}
