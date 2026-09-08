import type { ErrorResponse } from '../types/room';

const BASE_URL: string = import.meta.env.VITE_API_BASE_URL;

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function handleResponse<T>(response: Response): Promise<T> {
  if (!response.ok) {
    let message = `요청이 실패했습니다. (${response.status})`;
    try {
      const body = (await response.json()) as ErrorResponse;
      if (body?.message) {
        message = body.message;
      }
    } catch {
      // 에러 응답 본문이 JSON이 아니면 기본 메시지를 그대로 사용한다.
    }
    throw new ApiError(response.status, message);
  }

  const text = await response.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export function getJson<T>(path: string): Promise<T> {
  return fetch(`${BASE_URL}${path}`).then((response) => handleResponse<T>(response));
}

export function postJson<T>(path: string, body?: unknown): Promise<T> {
  return fetch(`${BASE_URL}${path}`, {
    method: 'POST',
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  }).then((response) => handleResponse<T>(response));
}
