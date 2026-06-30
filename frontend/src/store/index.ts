import { create } from 'zustand';
import { persist, subscribeWithSelector } from 'zustand/middleware';
import { immer } from 'zustand/middleware/immer';
import { User, AuthTokens, Algorithm } from '../types/index.js';
import { authApi, registerTokenSource } from '../services/api.js';

/**
 * Concept #6  — Paradigma orientado a eventos, Reativo
 * Concept #7  — OOP: Estado encapsulado, Injeção de dependência via store
 * Concept #8  — FP: Imutabilidade via Immer, selectors como funções puras
 * Concept #12 — CQRS: separação de command (actions) e query (selectors)
 * Concept #18 — Async: async actions, optimistic updates
 */

// ── Auth State ────────────────────────────────────────────────────────────────
//
// ADR-004: access token fica apenas em memória (não persistido).
// Refresh token vai em sessionStorage — persiste no recarregamento da aba,
// mas não é legível por scripts de outras abas nem por ataques cross-origin.
// Ver docs/adr/ADR-004-auth-token-storage.md para target state (HttpOnly cookie).

const SESSION_REFRESH_KEY = 'cw_rt';

interface AuthState {
  user: User | null;
  accessToken: string | null;
  refreshToken: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  error: string | null;
}

interface AuthActions {
  login: (email: string, password: string) => Promise<void>;
  register: (email: string, username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  /** Chama no mount do App para restaurar sessão após recarregamento da página. */
  silentRefresh: () => Promise<boolean>;
  clearError: () => void;
}

function persistTokens(accessToken: string, refreshToken: string) {
  sessionStorage.setItem(SESSION_REFRESH_KEY, refreshToken);
  return { accessToken, refreshToken };
}

function clearPersistedTokens() {
  sessionStorage.removeItem(SESSION_REFRESH_KEY);
}

export const useAuthStore = create<AuthState & AuthActions>()(
  subscribeWithSelector(
    immer((set, get) => ({
      // Estado inicial — access token nunca vem de storage
      user: null,
      accessToken: null,
      // Lê o refresh token do sessionStorage na inicialização (sobrevive reload)
      refreshToken: sessionStorage.getItem(SESSION_REFRESH_KEY) ?? null,
      isAuthenticated: !!sessionStorage.getItem(SESSION_REFRESH_KEY),
      isLoading: false,
      error: null,

      // Commands (CQRS — Concept #12)
      login: async (email, password) => {
        set(state => { state.isLoading = true; state.error = null; });
        try {
          const tokens: AuthTokens = await authApi.login(email, password);
          persistTokens(tokens.accessToken, tokens.refreshToken);
          set(state => {
            state.accessToken = tokens.accessToken;
            state.refreshToken = tokens.refreshToken;
            state.isAuthenticated = true;
            state.isLoading = false;
          });
        } catch (e: unknown) {
          const message = e instanceof Error ? e.message : 'Login failed';
          set(state => { state.error = message; state.isLoading = false; });
          throw e;
        }
      },

      register: async (email, username, password) => {
        set(state => { state.isLoading = true; state.error = null; });
        try {
          const tokens: AuthTokens = await authApi.register(email, username, password);
          persistTokens(tokens.accessToken, tokens.refreshToken);
          set(state => {
            state.accessToken = tokens.accessToken;
            state.refreshToken = tokens.refreshToken;
            state.isAuthenticated = true;
            state.isLoading = false;
          });
        } catch (e: unknown) {
          const message = e instanceof Error ? e.message : 'Registration failed';
          set(state => { state.error = message; state.isLoading = false; });
          throw e;
        }
      },

      // Renovação silenciosa: disparada no mount do App quando há refresh token em
      // sessionStorage mas nenhum access token em memória (ex: reload da página).
      silentRefresh: async () => {
        const { refreshToken } = get();
        if (!refreshToken) return false;
        try {
          const tokens: AuthTokens = await authApi.refresh(refreshToken);
          persistTokens(tokens.accessToken, tokens.refreshToken);
          set(state => {
            state.accessToken = tokens.accessToken;
            state.refreshToken = tokens.refreshToken;
            state.isAuthenticated = true;
          });
          return true;
        } catch {
          clearPersistedTokens();
          set(state => {
            state.accessToken = null;
            state.refreshToken = null;
            state.isAuthenticated = false;
          });
          return false;
        }
      },

      logout: async () => {
        const { refreshToken } = get();
        if (refreshToken) {
          try { await authApi.logout(refreshToken); } catch { /* sempre limpa localmente */ }
        }
        clearPersistedTokens();
        set(state => {
          state.user = null;
          state.accessToken = null;
          state.refreshToken = null;
          state.isAuthenticated = false;
        });
      },

      clearError: () => set(state => { state.error = null; }),
    }))
  )
);

// ── Registra a store como fonte de token para o axios (quebra o ciclo) ────────
// Chamado uma vez após a criação da store, antes de qualquer requisição.
registerTokenSource(
  // getter do access token (memória)
  () => useAuthStore.getState().accessToken,
  // getter do refresh token (sessionStorage via store)
  () => useAuthStore.getState().refreshToken,
  // callback quando o interceptor renova os tokens com sucesso
  (accessToken, refreshToken) => {
    persistTokens(accessToken, refreshToken);
    useAuthStore.setState({ accessToken, refreshToken, isAuthenticated: true });
  },
  // callback quando a sessão expira definitivamente
  () => {
    clearPersistedTokens();
    useAuthStore.setState({ accessToken: null, refreshToken: null, isAuthenticated: false, user: null });
    window.location.href = '/login';
  },
);

// ── Algorithm Explorer State ──────────────────────────────────────────────────

interface AlgorithmState {
  currentAlgorithm: Algorithm | null;
  executionInput: number[];
  executionResult: { output: number[]; durationNs: number } | null;
  isExecuting: boolean;
  currentStep: number;
  steps: number[][];
  favorites: Set<string>;
  recentlyViewed: string[];
}

interface AlgorithmActions {
  setCurrentAlgorithm: (algo: Algorithm | null) => void;
  setExecutionInput: (input: number[]) => void;
  setExecutionResult: (result: { output: number[]; durationNs: number } | null) => void;
  setIsExecuting: (v: boolean) => void;
  nextStep: () => void;
  prevStep: () => void;
  resetSteps: () => void;
  toggleFavorite: (slug: string) => void;
  addToRecentlyViewed: (slug: string) => void;
}

export const useAlgorithmStore = create<AlgorithmState & AlgorithmActions>()(
  persist(
    immer((set) => ({
      currentAlgorithm: null,
      executionInput: [64, 34, 25, 12, 22, 11, 90],
      executionResult: null,
      isExecuting: false,
      currentStep: 0,
      steps: [],
      favorites: new Set<string>(),
      recentlyViewed: [],

      setCurrentAlgorithm: (algo) => set(state => { state.currentAlgorithm = algo; }),
      setExecutionInput: (input) => set(state => { state.executionInput = input; }),
      setExecutionResult: (result) => set(state => {
        state.executionResult = result;
        state.currentStep = 0;
      }),
      setIsExecuting: (v) => set(state => { state.isExecuting = v; }),
      nextStep: () => set(state => {
        if (state.currentStep < state.steps.length - 1) state.currentStep++;
      }),
      prevStep: () => set(state => {
        if (state.currentStep > 0) state.currentStep--;
      }),
      resetSteps: () => set(state => { state.currentStep = 0; state.steps = []; }),
      toggleFavorite: (slug) => set(state => {
        if (state.favorites.has(slug)) state.favorites.delete(slug);
        else state.favorites.add(slug);
      }),
      addToRecentlyViewed: (slug) => set(state => {
        state.recentlyViewed = [
          slug,
          ...state.recentlyViewed.filter(s => s !== slug).slice(0, 9),
        ];
      }),
    })),
    {
      name: 'conceptualware-algorithms',
      partialize: (state) => ({
        favorites: Array.from(state.favorites),
        recentlyViewed: state.recentlyViewed,
      }),
    }
  )
);

// ── UI State ──────────────────────────────────────────────────────────────────

interface UIState {
  theme: 'light' | 'dark' | 'system';
  sidebarOpen: boolean;
  conceptPanelOpen: boolean;
}

interface UIActions {
  setTheme: (theme: UIState['theme']) => void;
  toggleSidebar: () => void;
  toggleConceptPanel: () => void;
}

export const useUIStore = create<UIState & UIActions>()(
  persist(
    immer((set) => ({
      theme: 'system',
      sidebarOpen: true,
      conceptPanelOpen: false,

      setTheme: (theme) => set(state => { state.theme = theme; }),
      toggleSidebar: () => set(state => { state.sidebarOpen = !state.sidebarOpen; }),
      toggleConceptPanel: () => set(state => { state.conceptPanelOpen = !state.conceptPanelOpen; }),
    })),
    { name: 'conceptualware-ui' }
  )
);
