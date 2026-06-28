import { Request, Response, NextFunction } from 'express';
import { Result, ok, err } from '../types/index.js';

/**
 * Concept #16 — Rate Limiting e Throttling
 * Concept #21 — Segurança: Rate limiting para proteção contra ataques
 * Concept #4  — Estrutura de Dados: Sliding window com Map + Queue
 * Concept #5  — Token Bucket e Sliding Window Counter algorithms
 */

interface RateLimitConfig {
  readonly windowMs: number;
  readonly maxRequests: number;
  readonly keyGenerator?: (req: Request) => string;
  readonly message?: string;
  readonly skipSuccessfulRequests?: boolean;
}

interface WindowEntry {
  count: number;
  timestamps: number[];
}

// ── Sliding Window Counter ─────────────────────────────────────────────────────

class SlidingWindowRateLimiter {
  private readonly windows = new Map<string, WindowEntry>();
  private readonly config: Required<RateLimitConfig>;

  constructor(config: RateLimitConfig) {
    this.config = {
      keyGenerator: (req) => req.ip ?? 'unknown',
      message: 'Too many requests, please try again later.',
      skipSuccessfulRequests: false,
      ...config,
    };

    // Cleanup expired windows periodically
    setInterval(() => this.cleanup(), this.config.windowMs);
  }

  check(key: string): Result<{ remaining: number; resetAt: number }, { retryAfter: number }> {
    const now = Date.now();
    const windowStart = now - this.config.windowMs;

    let entry = this.windows.get(key);
    if (!entry) {
      entry = { count: 0, timestamps: [] };
      this.windows.set(key, entry);
    }

    // Remove expired timestamps (sliding window)
    entry.timestamps = entry.timestamps.filter(ts => ts > windowStart);
    entry.count = entry.timestamps.length;

    if (entry.count >= this.config.maxRequests) {
      const oldestRequest = entry.timestamps[0] ?? now;
      const retryAfter = Math.ceil((oldestRequest + this.config.windowMs - now) / 1000);
      return err({ retryAfter });
    }

    entry.timestamps.push(now);
    entry.count++;
    const remaining = this.config.maxRequests - entry.count;
    const resetAt = (entry.timestamps[0] ?? now) + this.config.windowMs;

    return ok({ remaining, resetAt });
  }

  private cleanup(): void {
    const cutoff = Date.now() - this.config.windowMs;
    for (const [key, entry] of this.windows.entries()) {
      entry.timestamps = entry.timestamps.filter(ts => ts > cutoff);
      if (entry.timestamps.length === 0) this.windows.delete(key);
    }
  }

  middleware() {
    return (req: Request, res: Response, next: NextFunction): void => {
      const key = this.config.keyGenerator(req);
      const result = this.check(key);

      res.setHeader('X-RateLimit-Limit', this.config.maxRequests);

      if (!result.ok) {
        const { retryAfter } = result.error;
        res.setHeader('X-RateLimit-Remaining', 0);
        res.setHeader('Retry-After', retryAfter);
        res.status(429).json({
          error: { code: 'RATE_LIMIT_EXCEEDED', message: this.config.message, retryAfter },
        });
        return;
      }

      res.setHeader('X-RateLimit-Remaining', result.value.remaining);
      res.setHeader('X-RateLimit-Reset', new Date(result.value.resetAt).toISOString());
      next();
    };
  }
}

// ── Token Bucket algorithm ─────────────────────────────────────────────────────

class TokenBucketLimiter {
  private tokens: number;
  private lastRefill: number;

  constructor(
    private readonly capacity: number,
    private readonly refillRate: number, // tokens per second
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

// ── Factory functions ─────────────────────────────────────────────────────────

export function createRateLimiter(config: RateLimitConfig) {
  return new SlidingWindowRateLimiter(config).middleware();
}

// Preset rate limiters
export const apiLimiter = createRateLimiter({
  windowMs: 60_000,
  maxRequests: 60,
  message: 'API rate limit exceeded. Max 60 requests/minute.',
});

export const authLimiter = createRateLimiter({
  windowMs: 15 * 60_000, // 15 minutes
  maxRequests: 5,
  message: 'Too many auth attempts. Please wait 15 minutes.',
});

export const algorithmExecutionLimiter = createRateLimiter({
  windowMs: 60_000,
  maxRequests: 20,
  message: 'Algorithm execution limit reached. Max 20/minute.',
});
