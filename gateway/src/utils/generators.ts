/**
 * Concept #2  — yield (gerador): Generator functions, iterator protocol
 * Concept #3  — Tipos: Iterator<T>, Generator<T, TReturn, TNext>
 * Concept #8  — Lazy evaluation via generators (infinite sequences on demand)
 * Concept #18 — Async generators (async iterator / async iteration protocol)
 */

// ── §1  Basic generator — yield keyword ─────────────────────────────────────

/** Yields integers from `start` to `end` (inclusive). */
export function* range(start: number, end: number, step = 1): Generator<number> {
  for (let i = start; i <= end; i += step) {
    yield i;                           // ← yield: suspends function, returns value
  }
}

/** Infinite Fibonacci sequence — lazy, only computes what is consumed. */
export function* fibonacci(): Generator<number> {
  let [a, b] = [0, 1];
  while (true) {
    yield a;                           // yield pauses; resumes on next() call
    [a, b] = [b, a + b];
  }
}

/** Yields prime numbers indefinitely (Sieve-free, trial division). */
export function* primes(): Generator<number> {
  yield 2;
  const found: number[] = [2];
  for (let candidate = 3; ; candidate += 2) {
    if (found.every(p => candidate % p !== 0)) {
      found.push(candidate);
      yield candidate;
    }
  }
}

// ── §2  Generator as coroutine — two-way communication via yield ─────────────

/**
 * Running total accumulator — caller sends numbers via .next(value),
 * generator yields running sum back. Demonstrates bidirectional yield.
 */
export function* runningTotal(): Generator<number, void, number> {
  let sum = 0;
  while (true) {
    const input: number = yield sum;   // yield returns value AND receives sent value
    sum += input ?? 0;
  }
}

// ── §3  Delegating generator — yield* ────────────────────────────────────────

/** Yields all elements from `a`, then all from `b`. */
export function* concat<T>(a: Iterable<T>, b: Iterable<T>): Generator<T> {
  yield* a;                            // ← yield*: delegate to another iterable
  yield* b;
}

/** Flattens a nested iterable one level deep. */
export function* flatten<T>(nested: Iterable<Iterable<T>>): Generator<T> {
  for (const inner of nested) {
    yield* inner;
  }
}

// ── §4  Async generator — async yield (Concept #18) ─────────────────────────

/**
 * Simulates paginated API calls, yielding one page at a time.
 * Demonstrates async iterator — the `for await...of` pattern.
 */
export async function* paginatedFetch<T>(
  fetcher: (page: number) => Promise<{ items: T[]; hasNext: boolean }>,
  startPage = 1
): AsyncGenerator<T[]> {
  let page = startPage;
  let hasNext = true;

  while (hasNext) {
    const result = await fetcher(page);  // async: awaits each page
    yield result.items;                  // yield each page's items
    hasNext = result.hasNext;
    page++;
  }
}

// ── §5  take() — collect N items from an infinite generator ──────────────────

export function take<T>(gen: Generator<T>, n: number): T[] {
  const result: T[] = [];
  for (const value of gen) {
    result.push(value);
    if (result.length >= n) break;       // break exits the for...of over generator
  }
  return result;
}

// ── §6  mapGen / filterGen — lazy HOFs over generators (Concept #8) ──────────

export function* mapGen<T, R>(gen: Iterable<T>, f: (x: T) => R): Generator<R> {
  for (const x of gen) yield f(x);
}

export function* filterGen<T>(gen: Iterable<T>, pred: (x: T) => boolean): Generator<T> {
  for (const x of gen) if (pred(x)) yield x;
}

export function* takeWhile<T>(gen: Iterable<T>, pred: (x: T) => boolean): Generator<T> {
  for (const x of gen) {
    if (!pred(x)) break;
    yield x;
  }
}

// ── §7  Symbol.iterator — custom iterable class (Concept #3, Symbol) ─────────

/** Range object implementing the iterable protocol via Symbol.iterator. */
export class Range implements Iterable<number> {
  constructor(
    private readonly start: number,
    private readonly end: number,
    private readonly step: number = 1
  ) {}

  [Symbol.iterator](): Iterator<number> {        // ← Symbol.iterator (Concept #3)
    let current = this.start;
    const end = this.end;
    const step = this.step;
    return {
      next(): IteratorResult<number> {
        if (current <= end) {
          const value = current;
          current += step;
          return { value, done: false };
        }
        return { value: undefined as never, done: true };
      },
    };
  }
}
