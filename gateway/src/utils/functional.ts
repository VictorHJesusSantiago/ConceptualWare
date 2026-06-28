/**
 * Concept #8 — Programação Funcional (TypeScript):
 *   Funções puras, Imutabilidade, Currying, Composição de funções,
 *   Map/Filter/Reduce, Closure, Avaliação preguiçosa, Transparência referencial,
 *   Funtor, Mônada, Monóide, Either, Option, IO Monad, Trampolim
 *
 * Concept #6 — Paradigma funcional, Paradigma declarativo
 */

// ── Function types ────────────────────────────────────────────────────────────

type Fn<A, B>    = (a: A) => B;
type Predicate<A> = Fn<A, boolean>;
type UnaryOp<A>  = Fn<A, A>;

// ── Currying (Concept #8) ─────────────────────────────────────────────────────

export function curry<A, B, C>(f: (a: A, b: B) => C): (a: A) => (b: B) => C {
  return a => b => f(a, b);
}

export function curry3<A, B, C, D>(f: (a: A, b: B, c: C) => D): (a: A) => (b: B) => (c: C) => D {
  return a => b => c => f(a, b, c);
}

// Partial application (Concept #8)
export function partial<A, B, C>(f: (a: A, b: B) => C, a: A): (b: B) => C {
  return b => f(a, b);
}

// ── Function Composition (Concept #8) ─────────────────────────────────────────

export function compose<A, B, C>(f: Fn<B, C>, g: Fn<A, B>): Fn<A, C> {
  return x => f(g(x));
}

export function pipe<A>(...fns: [Fn<A, A>, ...Fn<A, A>[]]): Fn<A, A> {
  return x => fns.reduce((v, f) => f(v), x);
}

// Pipe for multiple types (Concept #8)
export function flow<A, B>(f: Fn<A, B>): Fn<A, B>;
export function flow<A, B, C>(f: Fn<A, B>, g: Fn<B, C>): Fn<A, C>;
export function flow<A, B, C, D>(f: Fn<A, B>, g: Fn<B, C>, h: Fn<C, D>): Fn<A, D>;
export function flow(...fns: Fn<unknown, unknown>[]): Fn<unknown, unknown> {
  return (x: unknown) => fns.reduce((v, f) => f(v), x);
}

// ── Higher-Order Functions (Concept #8) ────────────────────────────────────────

export const map     = <A, B>(f: Fn<A, B>)     => (xs: readonly A[]): readonly B[] => xs.map(f);
export const filter  = <A>(p: Predicate<A>)    => (xs: readonly A[]): readonly A[] => xs.filter(p);
export const reduce  = <A, B>(f: (acc: B, x: A) => B, init: B) =>
                         (xs: readonly A[]): B => xs.reduce(f, init);
export const flatMap = <A, B>(f: Fn<A, readonly B[]>) =>
                         (xs: readonly A[]): readonly B[] => xs.flatMap(f);

// ── Memoization (Concept #8) ─────────────────────────────────────────────────

export function memoize<T extends unknown[], R>(fn: (...args: T) => R): (...args: T) => R {
  const cache = new Map<string, R>();
  return (...args: T): R => {
    const key = JSON.stringify(args);
    if (!cache.has(key)) cache.set(key, fn(...args));
    return cache.get(key)!;
  };
}

// ── Lazy Evaluation (Concept #8) ──────────────────────────────────────────────

export class Lazy<T> {
  private evaluated = false;
  private value!: T;
  constructor(private readonly thunk: () => T) {}

  get(): T {
    if (!this.evaluated) { this.value = this.thunk(); this.evaluated = true; }
    return this.value;
  }

  map<U>(f: Fn<T, U>): Lazy<U> { return new Lazy(() => f(this.get())); }
}

// ── IO Monad — encapsulates side effects (Concept #8) ─────────────────────────

export class IO<T> {
  constructor(private readonly effect: () => T) {}

  static of<T>(value: T): IO<T>           { return new IO(() => value); }
  run(): T                                 { return this.effect(); }
  map<U>(f: Fn<T, U>): IO<U>              { return new IO(() => f(this.run())); }
  flatMap<U>(f: Fn<T, IO<U>>): IO<U>      { return new IO(() => f(this.run()).run()); }
  chain<U>(f: Fn<T, IO<U>>): IO<U>        { return this.flatMap(f); }
}

// ── Trampolining — avoids stack overflow for deep recursion (Concept #8) ──────

type TrampolineResult<T> = { done: true; value: T } | { done: false; next: () => TrampolineResult<T> };

export function trampoline<T>(f: () => TrampolineResult<T>): T {
  let result = f();
  while (!result.done) result = result.next();
  return result.value;
}

export function fibTrampoline(n: number): number {
  function inner(n: number, a: number, b: number): TrampolineResult<number> {
    if (n === 0) return { done: true, value: a };
    return { done: false, next: () => inner(n - 1, b, a + b) };
  }
  return trampoline(() => inner(n, 0, 1));
}

// ── Monoid (Concept #8) ────────────────────────────────────────────────────────

interface Monoid<A> {
  readonly empty: A;
  concat(a: A, b: A): A;
}

export const Sum: Monoid<number>   = { empty: 0, concat: (a, b) => a + b };
export const Product: Monoid<number> = { empty: 1, concat: (a, b) => a * b };
export const Str: Monoid<string>   = { empty: '', concat: (a, b) => a + b };
export const All: Monoid<boolean>  = { empty: true, concat: (a, b) => a && b };
export const Any: Monoid<boolean>  = { empty: false, concat: (a, b) => a || b };

export function foldMonoid<A>(monoid: Monoid<A>, xs: readonly A[]): A {
  return xs.reduce(monoid.concat.bind(monoid), monoid.empty);
}

// ── Observable (Reactive Programming — Concept #18) ───────────────────────────

export class Observable<T> {
  constructor(
    private readonly subscribe: (observer: Observer<T>) => () => void
  ) {}

  static fromArray<T>(items: T[]): Observable<T> {
    return new Observable(observer => {
      items.forEach(item => observer.next(item));
      observer.complete();
      return () => {};
    });
  }

  static interval(ms: number): Observable<number> {
    return new Observable(observer => {
      let i = 0;
      const id = setInterval(() => observer.next(i++), ms);
      return () => clearInterval(id);
    });
  }

  map<U>(f: Fn<T, U>): Observable<U> {
    return new Observable(observer =>
      this.subscribe({ next: x => observer.next(f(x)), complete: observer.complete })
    );
  }

  filter(p: Predicate<T>): Observable<T> {
    return new Observable(observer =>
      this.subscribe({ next: x => { if (p(x)) observer.next(x); }, complete: observer.complete })
    );
  }

  take(n: number): Observable<T> {
    return new Observable(observer => {
      let count = 0;
      return this.subscribe({
        next: x => { if (count++ < n) observer.next(x); else observer.complete(); },
        complete: observer.complete
      });
    });
  }

  toArray(): Promise<T[]> {
    return new Promise(resolve => {
      const items: T[] = [];
      this.subscribe({ next: x => items.push(x), complete: () => resolve(items) });
    });
  }
}

interface Observer<T> {
  next: (value: T) => void;
  complete: () => void;
}

// ── Closure (Concept #8) ──────────────────────────────────────────────────────

export function makeCounter(initial = 0, step = 1): () => number {
  let count = initial;         // captured in closure
  return () => { count += step; return count; };
}

export function makeAdder(n: number): Fn<number, number> {
  return x => x + n;          // n captured in closure
}

// ── Applicative Functor (Concept #8) ─────────────────────────────────────────
// Applicative extends Functor with `ap`: apply a wrapped function to a wrapped value.
// Laws: identity, homomorphism, interchange, composition.
//   pure(id) <*> v  == v                         (identity)
//   pure(f)  <*> pure(x) == pure(f(x))           (homomorphism)

export type Applicative<T> =
  | { readonly _tag: 'Some'; readonly value: T }
  | { readonly _tag: 'None' };

export const Applicative = {
  /** pure: lift value into applicative context */
  pure: <T>(value: T): Applicative<T> => ({ _tag: 'Some', value }),
  none: <T>(): Applicative<T> => ({ _tag: 'None' }),

  /** map: Functor fmap */
  map: <T, R>(fa: Applicative<T>, f: (a: T) => R): Applicative<R> =>
    fa._tag === 'Some' ? Applicative.pure(f(fa.value)) : Applicative.none(),

  /** ap: <*> — apply wrapped function to wrapped value (the Applicative operation) */
  ap: <T, R>(fab: Applicative<(a: T) => R>, fa: Applicative<T>): Applicative<R> => {
    if (fab._tag === 'None') return Applicative.none();
    return Applicative.map(fa, fab.value);
  },

  /** liftA2: apply binary function to two Applicatives */
  liftA2: <A, B, C>(f: (a: A) => (b: B) => C) =>
    (fa: Applicative<A>) => (fb: Applicative<B>): Applicative<C> =>
      Applicative.ap(Applicative.map(fa, f), fb),

  getOrElse: <T>(fa: Applicative<T>, fallback: T): T =>
    fa._tag === 'Some' ? fa.value : fallback,
};

/** Verify Applicative homomorphism law: ap(pure(f), pure(x)) == pure(f(x)) */
export function verifyApplicativeLaw<T, R>(x: T, f: (a: T) => R): boolean {
  const lhs = Applicative.ap(Applicative.pure(f), Applicative.pure(x));
  const rhs = Applicative.pure(f(x));
  return (lhs as { value?: R }).value === (rhs as { value: R }).value;
}

// ── Referential Transparency examples (Concept #8) ────────────────────────────

// Pure function: same input → always same output, no side effects
export const add = (a: number, b: number): number => a + b;
export const multiply = (a: number, b: number): number => a * b;
export const square = (n: number): number => n * n;

// Impure (not referential transparent): depends on external state
// export const now = () => Date.now(); // side effect — reads from system clock
