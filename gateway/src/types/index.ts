/**
 * Concept #3 — Variáveis e Tipos de Dados:
 *   Tipagem estática, Inferência de tipos, Tipo genérico/Template/Paramétrico,
 *   Tipo nulável, Tipo opcional, Union types, Intersection types, Alias de tipo,
 *   Tipo void, Tipo never/bottom, Enum (TypeScript), Symbol, Tupla,
 *   Tipo Any/Object, Variável imutável (readonly), Literal types
 *
 * Concept #8 — FP: Either/Result type, Option/Maybe (via Optional<T>)
 */

// ── Primitive Type Aliases (Concept #3) ──────────────────────────────────────

export type UserId     = string & { readonly __brand: 'UserId' };
export type AlgoSlug   = string & { readonly __brand: 'AlgoSlug' };
export type Email      = string & { readonly __brand: 'Email' };
export type Timestamp  = number & { readonly __brand: 'Timestamp' };

// Branded type constructors (nominal typing in structural TS)
export const UserId   = (id: string): UserId     => id as UserId;
export const AlgoSlug = (s: string): AlgoSlug    => s as AlgoSlug;
export const mkEmail  = (e: string): Email        => e as Email;

// ── Union Types (Concept #3) ──────────────────────────────────────────────────

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD' | 'EXPERT';
export type Category   = 'SORTING' | 'SEARCHING' | 'GRAPH' | 'DYNAMIC_PROGRAMMING' |
                         'STRING' | 'GREEDY' | 'DIVIDE_AND_CONQUER' | 'BACKTRACKING' |
                         'MATHEMATICAL' | 'DATA_STRUCTURES' | 'COMPRESSION' | 'CRYPTOGRAPHY';

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE' | 'OPTIONS' | 'HEAD';
export type StatusCode = 200 | 201 | 204 | 400 | 401 | 403 | 404 | 409 | 422 | 429 | 500 | 503;

// ── Intersection Types (Concept #3) ──────────────────────────────────────────

export type Timestamped  = { readonly createdAt: string; readonly updatedAt: string };
export type Identifiable = { readonly id: string };
export type Entity       = Identifiable & Timestamped;

// ── Generic Types (Concept #3) ────────────────────────────────────────────────

export type Optional<T>   = T | null | undefined;
export type NonEmpty<T>   = T extends Array<infer U> ? [U, ...U[]] : never;
export type Readonly<T>   = { readonly [K in keyof T]: T[K] };
export type DeepReadonly<T> = { readonly [K in keyof T]: T[K] extends object ? DeepReadonly<T[K]> : T[K] };

// Paginated response — reusable generic (Concept #3 & #25)
export interface Paginated<T> {
  readonly items: readonly T[];
  readonly total: number;
  readonly page: number;
  readonly pageSize: number;
  readonly hasNext: boolean;
  readonly hasPrev: boolean;
}

// ── Result / Either Type (Concept #8) ────────────────────────────────────────

export type Ok<T>  = { readonly ok: true;  readonly value: T };
export type Err<E> = { readonly ok: false; readonly error: E };
export type Result<T, E = string> = Ok<T> | Err<E>;

export const ok  = <T>(value: T): Ok<T>   => ({ ok: true, value });
export const err = <E>(error: E): Err<E>  => ({ ok: false, error });

export function mapResult<T, U, E>(result: Result<T, E>, f: (v: T) => U): Result<U, E> {
  return result.ok ? ok(f(result.value)) : result;
}

export function flatMapResult<T, U, E>(
  result: Result<T, E>,
  f: (v: T) => Result<U, E>
): Result<U, E> {
  return result.ok ? f(result.value) : result;
}

export function getOrElse<T, E>(result: Result<T, E>, fallback: T): T {
  return result.ok ? result.value : fallback;
}

// ── Maybe / Option Type (Concept #8) ──────────────────────────────────────────

export type Maybe<T> = { readonly hasValue: true; readonly value: T }
                     | { readonly hasValue: false };

export const just    = <T>(value: T): Maybe<T> => ({ hasValue: true, value });
export const nothing = <T>(): Maybe<T>          => ({ hasValue: false });

export function mapMaybe<T, U>(m: Maybe<T>, f: (v: T) => U): Maybe<U> {
  return m.hasValue ? just(f(m.value)) : nothing();
}

// ── API Types (Concept #25) ───────────────────────────────────────────────────

export interface ApiResponse<T> {
  readonly data: T;
  readonly meta?: {
    readonly requestId: string;
    readonly timestamp: string;
    readonly version: string;
  };
}

export interface ApiError {
  readonly code: string;
  readonly message: string;
  readonly details?: unknown;
  readonly requestId: string;
}

// ── Algorithm Types ────────────────────────────────────────────────────────────

export interface Algorithm extends Entity {
  readonly slug: AlgoSlug;
  readonly name: string;
  readonly description: string;
  readonly category: Category;
  readonly difficulty: Difficulty;
  readonly timeComplexity: Complexity;
  readonly spaceComplexity: Complexity;
  readonly tags: readonly string[];
  readonly viewCount: number;
  readonly likeCount: number;
  readonly averageRating: number;
}

export interface Complexity {
  readonly time: string;
  readonly space: string;
  readonly description: string;
}

export interface ExecutionRequest {
  readonly input: readonly number[];
}

export interface ExecutionResult {
  readonly algorithmName: string;
  readonly input: readonly number[];
  readonly output: readonly number[];
  readonly durationNs: number;
  readonly timeComplexity: string;
  readonly spaceComplexity: string;
}

// ── User Types ─────────────────────────────────────────────────────────────────

export type Role        = 'USER' | 'PREMIUM' | 'ADMIN';
export type SkillLevel  = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED' | 'EXPERT';

export interface User extends Entity {
  readonly username: string;
  readonly email: Email;
  readonly roles: readonly Role[];
  readonly skillLevel: SkillLevel;
  readonly totalPoints: number;
  readonly challengesSolved: number;
  readonly currentStreak: number;
}

// ── Auth Types ─────────────────────────────────────────────────────────────────

export interface AuthTokens {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly userId: UserId;
  readonly username: string;
}

export interface JwtPayload {
  readonly sub: string;
  readonly email: string;
  readonly roles: readonly Role[];
  readonly type: 'access' | 'refresh';
  readonly exp: number;
  readonly iat: number;
}

// ── Environment (Concept #3 — variáveis de ambiente) ─────────────────────────

export interface AppConfig {
  readonly port: number;
  readonly nodeEnv: 'development' | 'production' | 'test';
  readonly backendUrl: string;
  readonly mongodbUri: string;
  readonly jwtSecret: string;
}

// ── Tuple types (Concept #3) ──────────────────────────────────────────────────

export type Pair<A, B>       = readonly [A, B];
export type Triple<A, B, C>  = readonly [A, B, C];
export type SortedPair       = Pair<number, number>; // [smaller, larger]

// ── Enum-like const objects (Concept #3) ──────────────────────────────────────

export const ComplexityLevel = {
  CONSTANT:    'O(1)',
  LOGARITHMIC: 'O(log n)',
  LINEAR:      'O(n)',
  LINEARITHMIC:'O(n log n)',
  QUADRATIC:   'O(n²)',
  CUBIC:       'O(n³)',
  EXPONENTIAL: 'O(2ⁿ)',
  FACTORIAL:   'O(n!)',
} as const;

export type ComplexityLevel = typeof ComplexityLevel[keyof typeof ComplexityLevel];
