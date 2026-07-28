import { Request, Response, NextFunction } from 'express';

/**
 * Concept #12 — Feature Flags (config-driven, sem SaaS externo): permite
 * habilitar/desabilitar rotas ou comportamentos via variável de ambiente,
 * sem precisar de deploy. Padrão simples de toggle por nome de flag.
 *
 *   Configuração: FEATURE_FLAGS="newDashboard,circuitBreakerV2" (CSV no .env)
 */

function parseEnabledFlags(): Set<string> {
  const raw = process.env['FEATURE_FLAGS'] ?? '';
  return new Set(
    raw.split(',').map((f) => f.trim()).filter((f) => f.length > 0)
  );
}

let enabledFlags = parseEnabledFlags();

export function isFeatureEnabled(flag: string): boolean {
  return enabledFlags.has(flag);
}

/** Recarrega as flags a partir do ambiente (útil em testes ou hot-reload de config). */
export function reloadFeatureFlags(): void {
  enabledFlags = parseEnabledFlags();
}

/** Middleware: bloqueia a rota com 404 se a flag não estiver habilitada. */
export function requireFeature(flag: string) {
  return (_req: Request, res: Response, next: NextFunction): void => {
    if (!isFeatureEnabled(flag)) {
      res.status(404).json({ error: 'Not Found' });
      return;
    }
    next();
  };
}

export function listFeatureFlags(): string[] {
  return Array.from(enabledFlags);
}
