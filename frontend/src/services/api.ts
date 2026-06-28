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

const api: AxiosInstance = axios.create({
  baseURL: import.meta.env['VITE_API_URL'] ?? 'http://localhost:3001',
  timeout: 15_000,
  headers: {
    'Content-Type': 'application/json',
    Accept: 'application/json',
  },
});

// ── Request interceptor — attach JWT (Concept #21) ────────────────────────────

api.interceptors.request.use(config => {
  const token = localStorage.getItem('accessToken');
  if (token) config.headers['Authorization'] = `Bearer ${token}`;
  config.headers['X-Request-ID'] = crypto.randomUUID().slice(0, 8);
  return config;
});

// ── Response interceptor — token refresh (Concept #21) ────────────────────────

let isRefreshing = false;
let pendingQueue: Array<{ resolve: (token: string) => void; reject: (e: unknown) => void }> = [];

api.interceptors.response.use(
  response => response,
  async (error: AxiosError) => {
    const originalRequest = error.config!;

    if (error.response?.status === 401 && !('_retry' in originalRequest)) {
      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          pendingQueue.push({ resolve, reject });
        }).then(token => {
          originalRequest.headers['Authorization'] = `Bearer ${token}`;
          return api(originalRequest);
        });
      }

      (originalRequest as Record<string, unknown>)['_retry'] = true;
      isRefreshing = true;

      try {
        const refreshToken = localStorage.getItem('refreshToken');
        if (!refreshToken) throw new Error('No refresh token');

        const { data } = await api.post<AuthTokens>('/api/v1/auth/refresh', { refreshToken });
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);

        pendingQueue.forEach(({ resolve }) => resolve(data.accessToken));
        pendingQueue = [];
        return api(originalRequest);
      } catch (refreshError) {
        pendingQueue.forEach(({ reject }) => reject(refreshError));
        pendingQueue = [];
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login';
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
