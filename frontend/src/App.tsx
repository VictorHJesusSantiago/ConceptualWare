import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useAuthStore } from './store/index.js';

/**
 * Concept #6  — SPA, Event-Driven, Reativo (React)
 * Concept #7  — OOP: Componentes como objetos/classes, encapsulamento de estado
 * Concept #18 — Async: lazy loading, Suspense (code splitting — Concept #26)
 * Concept #26 — Performance: Code splitting, Tree-shaking, Lazy import
 * Concept #13 — Padrões: Compound Components, Render Props, HOC
 */

// Code splitting — lazy loading routes (Concept #26)
const Dashboard         = lazy(() => import('./pages/Dashboard.js'));
const AlgorithmExplorer = lazy(() => import('./pages/AlgorithmExplorer.js'));
const AlgorithmDetail   = lazy(() => import('./pages/AlgorithmDetail.js'));
const ChallengesPage    = lazy(() => import('./pages/ChallengesPage.js'));
const LoginPage         = lazy(() => import('./pages/LoginPage.js'));
const RegisterPage      = lazy(() => import('./pages/RegisterPage.js'));
const ProfilePage       = lazy(() => import('./pages/ProfilePage.js'));
const ConceptMapPage    = lazy(() => import('./pages/ConceptMapPage.js'));

// React Query client — Concept #18 (async data fetching & caching)
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60_000,     // 5 minutes
      gcTime: 30 * 60_000,       // 30 minutes
      retry: 2,
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 1,
    },
  },
});

// Protected route component — Concept #7 (HOC pattern)
function ProtectedRoute({ children }: { children: React.ReactNode }): React.ReactElement {
  const isAuthenticated = useAuthStore(s => s.isAuthenticated);
  return isAuthenticated ? <>{children}</> : <Navigate to="/login" replace />;
}

// Loading fallback
function PageLoader(): React.ReactElement {
  return (
    <div className="flex items-center justify-center min-h-screen bg-gray-950">
      <div className="flex flex-col items-center gap-4">
        <div className="w-12 h-12 border-4 border-blue-500 border-t-transparent rounded-full animate-spin" />
        <p className="text-gray-400 text-sm">Loading ConceptualWare...</p>
      </div>
    </div>
  );
}

export default function App(): React.ReactElement {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Suspense fallback={<PageLoader />}>
          <Routes>
            {/* Public routes */}
            <Route path="/login"    element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />

            {/* Semi-public — accessible without auth, extra features with auth */}
            <Route path="/algorithms"        element={<AlgorithmExplorer />} />
            <Route path="/algorithms/:slug"  element={<AlgorithmDetail />} />
            <Route path="/concepts"          element={<ConceptMapPage />} />

            {/* Protected routes */}
            <Route path="/" element={
              <ProtectedRoute><Dashboard /></ProtectedRoute>
            } />
            <Route path="/challenges" element={
              <ProtectedRoute><ChallengesPage /></ProtectedRoute>
            } />
            <Route path="/profile" element={
              <ProtectedRoute><ProfilePage /></ProtectedRoute>
            } />

            <Route path="*" element={<Navigate to="/algorithms" replace />} />
          </Routes>
        </Suspense>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
