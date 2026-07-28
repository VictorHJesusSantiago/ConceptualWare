/**
 * Concept #12 — Circuit Breaker Pattern: protege o gateway contra um backend
 * degradado/indisponível, evitando esgotar conexões/threads com chamadas que
 * tendem a falhar (fail fast) e permitindo recuperação gradual.
 *
 * Implementação manual (sem lib externa), máquina de estados clássica:
 *   CLOSED  → chamadas passam normalmente; falhas são contadas.
 *   OPEN    → chamadas são rejeitadas imediatamente (fail fast) até o timeout.
 *   HALF_OPEN → após o timeout, permite UMA chamada de teste; sucesso fecha
 *               o circuito, falha reabre e reinicia o timeout.
 */

export type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN';

export interface CircuitBreakerOptions {
  failureThreshold: number;   // nº de falhas consecutivas para abrir o circuito
  resetTimeoutMs: number;     // tempo em OPEN antes de tentar HALF_OPEN
  name: string;
}

export class CircuitOpenError extends Error {
  constructor(circuitName: string) {
    super(`Circuit breaker "${circuitName}" está OPEN — chamada rejeitada (fail fast)`);
    this.name = 'CircuitOpenError';
  }
}

export class CircuitBreaker {
  private state: CircuitState = 'CLOSED';
  private consecutiveFailures = 0;
  private openedAt = 0;

  constructor(private readonly options: CircuitBreakerOptions) {}

  getState(): CircuitState {
    if (this.state === 'OPEN' && Date.now() - this.openedAt >= this.options.resetTimeoutMs) {
      this.state = 'HALF_OPEN';
    }
    return this.state;
  }

  async execute<T>(fn: () => Promise<T>): Promise<T> {
    const currentState = this.getState();

    if (currentState === 'OPEN') {
      throw new CircuitOpenError(this.options.name);
    }

    try {
      const result = await fn();
      this.onSuccess();
      return result;
    } catch (error) {
      this.onFailure();
      throw error;
    }
  }

  private onSuccess(): void {
    this.consecutiveFailures = 0;
    this.state = 'CLOSED';
  }

  private onFailure(): void {
    this.consecutiveFailures++;
    if (this.state === 'HALF_OPEN' || this.consecutiveFailures >= this.options.failureThreshold) {
      this.state = 'OPEN';
      this.openedAt = Date.now();
    }
  }

  snapshot() {
    return {
      name: this.options.name,
      state: this.getState(),
      consecutiveFailures: this.consecutiveFailures,
    };
  }
}

// ── Registry — um breaker por dependência downstream (ex.: "backend") ──────

const breakers = new Map<string, CircuitBreaker>();

export function getCircuitBreaker(name: string, options?: Partial<CircuitBreakerOptions>): CircuitBreaker {
  let breaker = breakers.get(name);
  if (!breaker) {
    breaker = new CircuitBreaker({
      name,
      failureThreshold: options?.failureThreshold ?? 5,
      resetTimeoutMs: options?.resetTimeoutMs ?? 30_000,
    });
    breakers.set(name, breaker);
  }
  return breaker;
}

export function allCircuitBreakersStatus() {
  return Array.from(breakers.values()).map((b) => b.snapshot());
}
