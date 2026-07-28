import { Request, Response, NextFunction } from 'express';

/**
 * Concept #26 — Cache de resposta em memória com TTL (LRU) para rotas
 * idempotentes (GET). Reduz round-trips ao backend para dados que mudam
 * pouco (ex.: catálogo de algoritmos). Cache por processo — em múltiplas
 * réplicas cada instância tem seu próprio cache (aceitável para dados
 * read-mostly; para invalidação coordenada entre réplicas, ver Redis
 * usado no rate limiter).
 */

interface CacheEntry {
  body: unknown;
  expiresAt: number;
}

class TtlLruCache {
  private readonly store = new Map<string, CacheEntry>();

  constructor(private readonly maxEntries: number) {}

  get(key: string): unknown | undefined {
    const entry = this.store.get(key);
    if (!entry) return undefined;

    if (Date.now() > entry.expiresAt) {
      this.store.delete(key);
      return undefined;
    }

    // Reinsere para marcar como recentemente usado (ordem de iteração do Map = LRU)
    this.store.delete(key);
    this.store.set(key, entry);
    return entry.body;
  }

  set(key: string, body: unknown, ttlMs: number): void {
    if (this.store.size >= this.maxEntries) {
      const oldestKey = this.store.keys().next().value;
      if (oldestKey !== undefined) this.store.delete(oldestKey);
    }
    this.store.set(key, { body, expiresAt: Date.now() + ttlMs });
  }

  clear(): void {
    this.store.clear();
  }

  size(): number {
    return this.store.size;
  }
}

const cache = new TtlLruCache(500);

export function cacheResponse(ttlMs: number) {
  return (req: Request, res: Response, next: NextFunction): void => {
    if (req.method !== 'GET') {
      next();
      return;
    }

    const key = req.originalUrl;
    const cached = cache.get(key);
    if (cached !== undefined) {
      res.setHeader('X-Cache', 'HIT');
      res.json(cached);
      return;
    }

    res.setHeader('X-Cache', 'MISS');
    const originalJson = res.json.bind(res);
    res.json = (body: unknown) => {
      if (res.statusCode >= 200 && res.statusCode < 300) {
        cache.set(key, body, ttlMs);
      }
      return originalJson(body);
    };
    next();
  };
}

export function cacheStats() {
  return { size: cache.size() };
}
