/**
 * Concept #3 — Variáveis e Tipos de Dados (complete coverage):
 *   Symbol/Atom, Branded types, Nominal typing, Type guards,
 *   Discriminated unions, Intersection types, Never type, Void type,
 *   Literal types, Template literal types, Conditional types,
 *   Mapped types, Infer keyword, Tuple types, const enum
 *
 * Concept #6  — Paradigma de programação genérica
 * Concept #8  — Pattern matching funcional via type narrowing
 */

// ── §1  Symbol — unique, immutable primitive (Concept #3) ────────────────────
// Symbol is JS/TS's equivalent of Lisp/Elixir Atoms:
// guaranteed-unique identifiers that cannot be created twice.

export const ConceptSymbol = {
  SORTING:     Symbol('SORTING'),     // ← each Symbol() call creates a unique value
  GRAPH:       Symbol('GRAPH'),
  DP:          Symbol('DYNAMIC_PROGRAMMING'),
  AUTH:        Symbol('AUTH'),
  EXECUTE:     Symbol('EXECUTE'),
} as const;

export type ConceptSymbolKey = keyof typeof ConceptSymbol;

/** Well-known Symbol — override iterator behaviour (Symbol.iterator is built-in) */
export class SymbolDemo {
  private readonly data: number[];

  constructor(...values: number[]) { this.data = values; }

  /** Implement Symbol.iterator to make class iterable (Concept #2 — for...of) */
  [Symbol.iterator](): Iterator<number> {
    let index = 0;
    const data = this.data;
    return {
      next(): IteratorResult<number> {
        return index < data.length
          ? { value: data[index++]!, done: false }
          : { value: undefined as never, done: true };
      },
    };
  }

  /** Symbol.toPrimitive — control type coercion (Concept #3 — coerção) */
  [Symbol.toPrimitive](hint: 'string' | 'number' | 'default'): string | number {
    if (hint === 'number') return this.data.reduce((s, v) => s + v, 0);
    return `SymbolDemo(${this.data.join(', ')})`;
  }
}

// ── §2  Atom-like pattern — unique singleton constants ────────────────────────
// Erlang/Elixir Atoms ≈ TypeScript `const` + Symbol for runtime uniqueness

const _OK     = Symbol('Ok');
const _ERR    = Symbol('Err');
const _EMPTY  = Symbol('Empty');

export const Atom = {
  Ok:    _OK,
  Err:   _ERR,
  Empty: _EMPTY,
} as const;

export type Atom = typeof Atom[keyof typeof Atom];

// ── §3  Branded (Nominal) Types ───────────────────────────────────────────────
// TypeScript is structurally typed by default. Branded types add nominal identity.

declare const __brand: unique symbol;                           // Symbol-based brand
type Brand<T, B> = T & { readonly [__brand]: B };

export type Milliseconds = Brand<number, 'Milliseconds'>;
export type Bytes        = Brand<number, 'Bytes'>;
export type Percentage   = Brand<number, 'Percentage'>;

export const ms    = (n: number): Milliseconds => n as Milliseconds;
export const bytes = (n: number): Bytes        => n as Bytes;
export const pct   = (n: number): Percentage   => {
  if (n < 0 || n > 100) throw new RangeError(`Percentage must be 0-100, got ${n}`);
  return n as Percentage;
};

// ── §4  Literal types & Template literal types ───────────────────────────────

export type Env  = 'development' | 'staging' | 'production';
export type LogLevel = 'debug' | 'info' | 'warn' | 'error' | 'fatal';

export type CacheKey = `cache:${string}:${number}`;
export type ApiPath  = `/api/v${number}/${string}`;

// ── §5  Discriminated unions (tagged unions / sum types) ──────────────────────

export type LoadingState<T> =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; error: string };

export function renderState<T>(state: LoadingState<T>): string {
  switch (state.status) {
    case 'idle':    return 'Idle';
    case 'loading': return 'Loading...';
    case 'success': return `Data: ${JSON.stringify(state.data)}`;
    case 'error':   return `Error: ${state.error}`;
  }
}

// ── §6  Intersection types ────────────────────────────────────────────────────

type Timestamped = { createdAt: Date; updatedAt: Date };
type Identifiable = { id: string };
type Auditable = { createdBy: string; updatedBy: string };

export type Entity  = Identifiable & Timestamped;                  // intersection
export type Audited = Identifiable & Timestamped & Auditable;      // triple intersection

// ── §7  Mapped types ──────────────────────────────────────────────────────────

export type Readonly_<T> = { readonly [K in keyof T]: T[K] };
export type Partial_<T>  = { [K in keyof T]?: T[K] };
export type Nullable<T>  = { [K in keyof T]: T[K] | null };
export type DeepPartial<T> = { [K in keyof T]?: T[K] extends object ? DeepPartial<T[K]> : T[K] };

// ── §8  Conditional types & infer ────────────────────────────────────────────

export type Unwrap<T> = T extends Promise<infer U> ? U : T;
export type ArrayElement<T> = T extends (infer U)[] ? U : never;
export type ReturnType_<T> = T extends (...args: never[]) => infer R ? R : never;
export type NonNullable_<T> = T extends null | undefined ? never : T;

// ── §9  const enum (Concept #3) ───────────────────────────────────────────────

export const ComplexityLevel = {
  CONSTANT:    'O(1)',
  LOGARITHMIC: 'O(log n)',
  LINEAR:      'O(n)',
  LINEARITHMIC:'O(n log n)',
  QUADRATIC:   'O(n²)',
  CUBIC:       'O(n³)',
  EXPONENTIAL: 'O(2^n)',
  FACTORIAL:   'O(n!)',
} as const;

export type ComplexityLevel = typeof ComplexityLevel[keyof typeof ComplexityLevel];

// ── §10  never type — exhaustive checks (Concept #3) ─────────────────────────

function assertNever(x: never): never {
  throw new Error(`Unexpected value: ${x}`);
}

export function getComplexityDescription(level: ComplexityLevel): string {
  switch (level) {
    case ComplexityLevel.CONSTANT:     return 'Constant time — best possible';
    case ComplexityLevel.LOGARITHMIC:  return 'Logarithmic — halves problem each step';
    case ComplexityLevel.LINEAR:       return 'Linear — proportional to input size';
    case ComplexityLevel.LINEARITHMIC: return 'Linearithmic — typical for optimal sorts';
    case ComplexityLevel.QUADRATIC:    return 'Quadratic — nested loops';
    case ComplexityLevel.CUBIC:        return 'Cubic — triple nested loops';
    case ComplexityLevel.EXPONENTIAL:  return 'Exponential — doubles with each input';
    case ComplexityLevel.FACTORIAL:    return 'Factorial — all permutations';
    default: return assertNever(level); // compile-time exhaustiveness check via never
  }
}

// ── §11  Tuple types (Concept #3) ────────────────────────────────────────────

export type Pair<A, B>    = readonly [A, B];
export type Triple<A,B,C> = readonly [A, B, C];
export type Point2D = Pair<number, number>;
export type Point3D = Triple<number, number, number>;

export const distance = ([x1, y1]: Point2D, [x2, y2]: Point2D): number =>
  Math.sqrt((x2 - x1) ** 2 + (y2 - y1) ** 2);

// ── §12  Type guard functions ─────────────────────────────────────────────────

export const isString  = (v: unknown): v is string  => typeof v === 'string';
export const isNumber  = (v: unknown): v is number  => typeof v === 'number' && !isNaN(v);
export const isBoolean = (v: unknown): v is boolean => typeof v === 'boolean';
export const isArray   = (v: unknown): v is unknown[] => Array.isArray(v);
export const isObject  = (v: unknown): v is Record<string, unknown> =>
  v !== null && typeof v === 'object' && !Array.isArray(v);
