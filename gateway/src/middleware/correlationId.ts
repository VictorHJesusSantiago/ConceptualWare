import { Request, Response, NextFunction } from 'express';
import { randomUUID } from 'crypto';

/**
 * Concept #27 — Observabilidade: Correlation ID / Trace ID propagado
 * ponta a ponta (cliente → gateway → backend → resposta).
 *
 *   - Reaproveita X-Correlation-ID do cliente se presente (permite ao
 *     frontend correlacionar múltiplas chamadas de um mesmo fluxo).
 *   - Caso ausente, gera um novo UUID.
 *   - Anexa em req para uso por outros middlewares/rotas (proxy ao backend).
 *   - Devolve no header de resposta para debugging client-side.
 */
declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Express {
    interface Request {
      correlationId: string;
    }
  }
}

export function correlationId(req: Request, res: Response, next: NextFunction): void {
  const incoming = req.headers['x-correlation-id'];
  const id = typeof incoming === 'string' && incoming.length > 0 ? incoming : randomUUID();

  req.correlationId = id;
  res.setHeader('X-Correlation-ID', id);
  next();
}

/** Headers a propagar em toda chamada proxy ao backend — inclui correlation-id. */
export function forwardedHeaders(req: Request): Record<string, string> {
  return {
    'X-Correlation-ID': req.correlationId,
  };
}
