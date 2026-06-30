import axios, { AxiosInstance, AxiosError } from 'axios';
import { Algorithm, AuthTokens, ExecutionResult, Paginated, User } from '../types/index.js';

/**
 * Concept #25 — REST API client: GET, POST, PUT, PATCH, DELETE
 *   Paginação, Filtros, Autenticação via Bearer token
 * Concept #18 — Async: async/await, Promise, interceptors
 * Concept #16 — HTTP: status codes, headers, CORS
 * Concept #8  — FP: composição de funções (interceptors como middleware pipeline)
 */

// ── Axios Instance (Concept #14 — DRY, Singleton-like config) ────────────────

// VITE_API_BASE_URL definido em .env (ver .env.example).
// Não use VITE_API_URL — nome divergente do .env.example causava fallback silencioso.
const api: AxiosInstance = axios.create({
  baseURL: import.meta.env['VITE_API_BASE_URL'] ?? 'http://localhost:3001',
  timeout: 15_000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

// ── Token source injection (quebra o ciclo api ↔ store) ───────────────────────
//
// ADR-004: access token fica apenas em memória (na store Zustand), nunca em
// localStorage. api.ts não pode importar a store diretamente pois cria ciclo:
//   api.ts → store/index.ts → services/api.ts
//
// Solução: padrão de injeção — a store se registra como fonte de token logo
// após ser criada (ver store/index.ts: registerTokenSource / registerOnExpired).
// Antes do registro, as callbacks são no-ops seguros.

let _getAccessToken: () => string | null = () => null;
let _getRefreshToken: () => string | null = () => null;
let _onTokenRefreshed: (accessToken: string, refreshToken: string) => void = () => {};
let _onSessionExpired: () => void = () => { window.location.href = '/login'; };

export function registerTokenSource(
  getAccess: () => string | null,
  getRefresh: () => string | null,
  onRefreshed: (access: string, refresh: string) => void,
  onExpired: () => void,
): void {
  _getAccessToken   = getAccess;
  _getRefreshToken  = getRefresh;
  _onTokenRefreshed = onRefreshed;
  _onSessionExpired = onExpired;
}

// ── Request interceptor — attach JWT (Concept #21) ────────────────────────────

api.interceptors.request.use(config => {
  const token = _getAccessToken();
  if (token) config.headers['Authorization'] = `Bearer ${token}`;
  config.headers['X-Request-ID'] = crypto.randomUUID().slice(0, 8);
  return config;
});

// ── Response interceptor — silent refresh em 401 (Concept #21) ────────────────

let isRefreshing = false;
let pendingQueue: Array<{ resolve: (token: string) => void; reject: (e: unknown) => void }> = [];

api.interceptors.response.use(
  response => response,
  async (error: AxiosError) => {
    const originalRequest = error.config!;

    if (error.response?.status === 401 && !('_retry' in originalRequest)) {
      if (isRefreshing) {
        return new Promise<string>((resolve, reject) => {
          pendingQueue.push({ resolve, reject });
        }).then(token => {
          originalRequest.headers['Authorization'] = `Bearer ${token}`;
          return api(originalRequest);
        });
      }

      (originalRequest as Record<string, unknown>)['_retry'] = true;
      isRefreshing = true;

      try {
        const refreshToken = _getRefreshToken();
        if (!refreshToken) throw new Error('No refresh token');

        const { data } = await api.post<AuthTokens>('/api/v1/auth/refresh', { refreshToken });

        _onTokenRefreshed(data.accessToken, data.refreshToken);

        pendingQueue.forEach(({ resolve }) => resolve(data.accessToken));
        pendingQueue = [];
        originalRequest.headers['Authorization'] = `Bearer ${data.accessToken}`;
        return api(originalRequest);
      } catch (refreshError) {
        pendingQueue.forEach(({ reject }) => reject(refreshError));
        pendingQueue = [];
        _onSessionExpired();
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }

    return Promise.reject(error);
  }
);

// ── Auth API ──────────────────────────────────────────────────────────────────

export const authApi = {
  register: (email: string, username: string, password: string) =>
    api.post<AuthTokens>('/api/v1/auth/register', { email, username, password }).then(r => r.data),

  login: (email: string, password: string) =>
    api.post<AuthTokens>('/api/v1/auth/login', { email, password }).then(r => r.data),

  logout: (refreshToken: string) =>
    api.post('/api/v1/auth/logout', { refreshToken }),

  refresh: (refreshToken: string) =>
    api.post<AuthTokens>('/api/v1/auth/refresh', { refreshToken }).then(r => r.data),
};

// ── Algorithm API (Concept #25) ───────────────────────────────────────────────

export const algorithmApi = {
  list: (params?: {
    category?: string; difficulty?: string;
    page?: number; size?: number; sort?: string; order?: string;
  }) => api.get<Paginated<Algorithm>>('/api/v1/algorithms', { params }).then(r => r.data),

  search: (q: string, page = 0, size = 10) =>
    api.get<Paginated<Algorithm>>('/api/v1/algorithms/search', { params: { q, page, size } })
       .then(r => r.data),

  getBySlug: (slug: string) =>
    api.get<Algorithm>(`/api/v1/algorithms/${slug}`).then(r => r.data),

  execute: (slug: string, input: number[]) =>
    api.post<ExecutionResult>(`/api/v1/algorithms/${slug}/execute`, { input }).then(r => r.data),

  getComplexity: (slug: string) =>
    api.get(`/api/v1/algorithms/${slug}/complexity`).then(r => r.data),

  like: (slug: string) =>
    api.post(`/api/v1/algorithms/${slug}/like`).then(r => r.data),

  rate: (slug: string, rating: number) =>
    api.post(`/api/v1/algorithms/${slug}/rate`, { rating }).then(r => r.data),
};

// ── User API ──────────────────────────────────────────────────────────────────

export const userApi = {
  me: () => api.get<User>('/api/v1/users/me').then(r => r.data),

  leaderboard: (limit = 10) =>
    api.get<User[]>('/api/v1/users/leaderboard', { params: { limit } }).then(r => r.data),

  progress: () =>
    api.get('/api/v1/users/me/progress').then(r => r.data),
};

export default api;
