import { Request, Response, NextFunction } from 'express';
import { Result, ok, err } from '../types/index.js';

/**
 * Concept #16 — Rate Limiting e Throttling
 * Concept #21 — Segurança: proteção contra ataques de força bruta
 * Concept #4  — Estrutura de Dados: Sliding Window Counter com Redis SORTED SET
 * Concept #5  — Token Bucket e Sliding Window algoritmos
 * Concept #11 — Redis: ZADD/ZREMRANGEBYSCORE/ZCARD para rate limiting distribuído
 *
 * Design:
 *   - Em produção (REDIS_URL definido): Sliding Window via Redis SORTED SET.
 *     Cada request registra um membro SCORE=timestamp, expira membros antigos com
 *     ZREMRANGEBYSCORE. Atômico, compartilhado entre réplicas, sobrevive restart.
 *
 *   - Em dev/test (sem REDIS_URL): Sliding Window via Map em memória.
 *     Funciona apenas em processo único — NÃO usar em deployment multi-réplica.
 *
 * AVISO: a versão em memória anterior (SlidingWindowRateLimiter) violava o
 * 12-Factor App fator VI (stateless processes): limitava apenas dentro de uma
 * instância, ou seja, N réplicas implicavam N vezes o limite real.
 */

// ── Redis Sliding Window (distribuído, multi-réplica) ──────────────────────────

interface RateLimitConfig {
  readonly windowMs: number;
  readonly maxRequests: number;
  readonly keyGenerator?: (req: Request) => string;
  readonly message?: string;
}

interface CheckResult {
  allowed: boolean;
  remaining: number;
  retryAfter: number;
  resetAt: number;
}

// Importação lazy para evitar erro de inicialização quando Redis não está disponível
let redisClient: import('ioredis').Redis | null = null;
let redisAvailable = false;

async function getRedisClient(): Promise<import('ioredis').Redis | null> {
  if (redisClient) return redisClient;

  const redisUrl = process.env['REDIS_URL'];
  if (!redisUrl) return null;

  try {
    const { default: Redis } = await import('ioredis');
    const client = new Redis(redisUrl, {
      maxRetriesPerRequest: 1,
      connectTimeout: 2000,
      lazyConnect: true,
    });

    await client.connect();
    redisAvailable = true;
    redisClient = client;

    client.on('error', (e) => {
      console.error('[rate-limiter] Redis error, falling back to in-memory:', e.message);
      redisAvailable = false;
    });

    client.on('connect', () => {
      redisAvailable = true;
    });

    return client;
  } catch (e) {
    console.warn('[rate-limiter] Redis unavailable, using in-memory fallback:', (e as Error).message);
    return null;
  }
}

/**
 * Sliding window usando Redis SORTED SET.
 * Cada membro = score único (timestamp_ms + random suffix para evitar colisão),
 * score = timestamp. ZREMRANGEBYSCORE remove entradas fora da janela. Atômico via pipeline.
 */
async function checkRedis(
  client: import('ioredis').Redis,
  key: string,
  windowMs: number,
  maxRequests: number,
): Promise<CheckResult> {
  const now = Date.now();
  const windowStart = now - windowMs;
  const member = `${now}:${Math.random().toString(36).slice(2)}`;
  const expireSec = Math.ceil(windowMs / 1000) + 1;

  const pipeline = client.pipeline();
  pipeline.zremrangebyscore(key, 0, windowStart);
  pipeline.zadd(key, now, member);
  pipeline.zcard(key);
  pipeline.expire(key, expireSec);

  const results = await pipeline.exec();
  const count = (results?.[2]?.[1] as number) ?? 0;

  if (count > maxRequests) {
    // Já ultrapassou: desfaz a adição do membro atual
    await client.zrem(key, member);
    const oldest = await client.zrange(key, 0, 0, 'WITHSCORES');
    const oldestTs = oldest[1] ? parseInt(oldest[1], 10) : now;
    const retryAfter = Math.ceil((oldestTs + windowMs - now) / 1000);
    return { allowed: false, remaining: 0, retryAfter, resetAt: oldestTs + windowMs };
  }

  return {
    allowed: true,
    remaining: maxRequests - count,
    retryAfter: 0,
    resetAt: now + windowMs,
  };
}

// ── In-Memory Sliding Window (fallback dev/test) ──────────────────────────────

interface WindowEntry {
  timestamps: number[];
}

class InMemorySlidingWindow {
  private readonly windows = new Map<string, WindowEntry>();
  private readonly cleanupInterval: ReturnType<typeof setInterval>;

  constructor(private readonly windowMs: number) {
    // Cleanup periódico para evitar vazamento de memória — entradas expiradas removidas
    this.cleanupInterval = setInterval(() => this.cleanup(), windowMs * 2);
    // Não bloqueia o processo na saída
    if (this.cleanupInterval.unref) this.cleanupInterval.unref();
  }

  check(key: string, maxRequests: number): CheckResult {
    const now = Date.now();
    const windowStart = now - this.windowMs;

    let entry = this.windows.get(key);
    if (!entry) {
      entry = { timestamps: [] };
      this.windows.set(key, entry);
    }

    entry.timestamps = entry.timestamps.filter(ts => ts > windowStart);

    if (entry.timestamps.length >= maxRequests) {
      const oldestTs = entry.timestamps[0] ?? now;
      const retryAfter = Math.ceil((oldestTs + this.windowMs - now) / 1000);
      return { allowed: false, remaining: 0, retryAfter, resetAt: oldestTs + this.windowMs };
    }

    entry.timestamps.push(now);
    const remaining = maxRequests - entry.timestamps.length;
    const resetAt = (entry.timestamps[0] ?? now) + this.windowMs;
    return { allowed: true, remaining, retryAfter: 0, resetAt };
  }

  private cleanup(): void {
    const cutoff = Date.now() - this.windowMs;
    for (const [key, entry] of this.windows.entries()) {
      entry.timestamps = entry.timestamps.filter(ts => ts > cutoff);
      if (entry.timestamps.length === 0) this.windows.delete(key);
    }
  }
}

// Instâncias únicas por windowMs para reuso de cleanup interval
const inMemoryInstances = new Map<number, InMemorySlidingWindow>();
function getInMemory(windowMs: number): InMemorySlidingWindow {
  let inst = inMemoryInstances.get(windowMs);
  if (!inst) {
    inst = new InMemorySlidingWindow(windowMs);
    inMemoryInstances.set(windowMs, inst);
  }
  return inst;
}

// ── Token Bucket (demonstração do conceito — Concept #5) ─────────────────────

export class TokenBucketLimiter {
  private tokens: number;
  private lastRefill: number;

  constructor(
    private readonly capacity: number,
    private readonly refillRate: number, // tokens por segundo
  ) {
    this.tokens = capacity;
    this.lastRefill = Date.now();
  }

  tryConsume(count = 1): boolean {
    this.refill();
    if (this.tokens >= count) {
      this.tokens -= count;
      return true;
    }
    return false;
  }

  private refill(): void {
    const now = Date.now();
    const elapsed = (now - this.lastRefill) / 1000;
    this.tokens = Math.min(this.capacity, this.tokens + elapsed * this.refillRate);
    this.lastRefill = now;
  }
}

// ── Factory ───────────────────────────────────────────────────────────────────

export function createRateLimiter(config: RateLimitConfig) {
  const {
    windowMs,
    maxRequests,
    keyGenerator = (req: Request) => req.ip ?? 'unknown',
    message = 'Too many requests, please try again later.',
  } = config;

  return async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    const key = `rl:${keyGenerator(req)}:${windowMs}`;

    let result: CheckResult;

    try {
      const client = await getRedisClient();
      if (client && redisAvailable) {
        result = await checkRedis(client, key, windowMs, maxRequests);
      } else {
        result = getInMemory(windowMs).check(key, maxRequests);
      }
    } catch {
      // Falha aberta: deixa passar para não derrubar o serviço se Redis cair
      result = { allowed: true, remaining: -1, retryAfter: 0, resetAt: Date.now() + windowMs };
    }

    res.setHeader('X-RateLimit-Limit', maxRequests);
    res.setHeader('X-RateLimit-Remaining', Math.max(0, result.remaining));
    res.setHeader('X-RateLimit-Reset', new Date(result.resetAt).toISOString());

    if (!result.allowed) {
      res.setHeader('Retry-After', result.retryAfter);
      res.status(429).json({
        error: { code: 'RATE_LIMIT_EXCEEDED', message, retryAfter: result.retryAfter },
      });
      return;
    }

    next();
  };
}

// ── Limitadores pré-configurados ──────────────────────────────────────────────

export const apiLimiter = createRateLimiter({
  windowMs: 60_000,
  maxRequests: 60,
  message: 'API rate limit exceeded. Max 60 requests/minute.',
});

export const authLimiter = createRateLimiter({
  windowMs: 15 * 60_000,
  maxRequests: 5,
  message: 'Too many auth attempts. Please wait 15 minutes.',
});

export const algorithmExecutionLimiter = createRateLimiter({
  windowMs: 60_000,
  maxRequests: 20,
  message: 'Algorithm execution limit reached. Max 20/minute.',
});
