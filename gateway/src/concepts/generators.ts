/**
 * Concept #2/#3 — Generators (yield) and Symbols in TypeScript/JavaScript
 *
 * TypeScript has native generator syntax with `yield` (ES2015+).
 * This file demonstrates all generator and Symbol concepts using the real syntax.
 */

// ─────────────────────────────────────────────────────────────────────────────
// 1. Symbol — unique, non-string property keys
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Symbol(): creates a unique, immutable primitive.
 * Even with the same description, two symbols are never equal.
 *
 * Use cases:
 *   1. Unique object property keys (no accidental name collisions)
 *   2. Well-known protocol hooks (Symbol.iterator, Symbol.toPrimitive, etc.)
 *   3. "Private-ish" properties that don't appear in JSON.stringify or for..in
 */

const sym1 = Symbol('id');
const sym2 = Symbol('id');
console.assert(sym1 !== sym2, 'Local symbols are unique even with same description');
console.assert(typeof sym1 === 'symbol');
console.log('sym1:', sym1.toString());  // Symbol(id)
console.log('sym1 === sym2:', sym1 === sym2);  // false

// Global symbol registry — Symbol.for() returns the same symbol for the same key
const globalOk1 = Symbol.for('ok');
const globalOk2 = Symbol.for('ok');
console.assert(globalOk1 === globalOk2, 'Global symbols with same key are identical');
console.log('globalOk1 === globalOk2:', globalOk1 === globalOk2);  // true
console.log('keyFor:', Symbol.keyFor(globalOk1));  // 'ok'

// Symbol as object key — does not appear in for..in or JSON.stringify
const ID   = Symbol('id');
const TYPE = Symbol('type');

const entity = {
    [ID]:   123,           // Symbol key
    [TYPE]: 'User',        // Symbol key
    name:   'Alice',       // string key
};

console.log('name:', entity.name);   // accessible
console.log('id:',   entity[ID]);    // accessible via symbol reference
console.log('JSON:', JSON.stringify(entity));   // {"name":"Alice"} — symbols hidden

// Well-known symbols — hooks into JS runtime behavior
const ResultOK:    unique symbol = Symbol.for('Result::ok');
const ResultError: unique symbol = Symbol.for('Result::error');

type Result<T, E = Error> =
    | { tag: typeof ResultOK;    value: T }
    | { tag: typeof ResultError; error: E };

function ok<T>(value: T): Result<T> {
    return { tag: ResultOK, value };
}

function err<E extends Error>(error: E): Result<never, E> {
    return { tag: ResultError, error };
}

function isOk<T, E>(r: Result<T, E>): r is { tag: typeof ResultOK; value: T } {
    return r.tag === ResultOK;
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. Generators — function* with yield
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Generator function syntax:
 *   function* name() { ... yield value; ... }
 *
 * Calling it returns a Generator object (implements Iterator + Iterable protocols).
 * Execution is SUSPENDED at each `yield` and RESUMED on `next()`.
 *
 * State machine model:
 *   → created → [suspended at yield] → [running] → done
 *                        ↑_______________|
 *
 * Memory advantage: infinite sequences with O(1) memory.
 */

// Infinite Fibonacci sequence
function* fibonacci(): Generator<number> {
    let [a, b] = [0, 1];
    while (true) {
        yield a;          // pause here, return a to caller
        [a, b] = [b, a + b];  // resume from here on next call
    }
}

// Take first N values from any generator
function take<T>(gen: Generator<T>, n: number): T[] {
    const result: T[] = [];
    for (let i = 0; i < n; i++) {
        const { value, done } = gen.next();
        if (done) break;
        result.push(value as T);
    }
    return result;
}

const fibs = take(fibonacci(), 10);
console.log('First 10 Fibonacci:', fibs);
// [0, 1, 1, 2, 3, 5, 8, 13, 21, 34]

// Range generator
function* range(start: number, end: number, step = 1): Generator<number> {
    assert(step !== 0, 'step cannot be zero');
    for (let i = start; step > 0 ? i < end : i > end; i += step) {
        yield i;
    }
}

console.log('range(0,10,2):', [...range(0, 10, 2)]);  // [0,2,4,6,8]
console.log('range(5,0,-1):', [...range(5, 0, -1)]);  // [5,4,3,2,1]

// Lazy pipeline using generator composition
function* map<T, U>(gen: Iterable<T>, fn: (v: T) => U): Generator<U> {
    for (const v of gen) yield fn(v);
}

function* filter<T>(gen: Iterable<T>, pred: (v: T) => boolean): Generator<T> {
    for (const v of gen) if (pred(v)) yield v;
}

function* takeWhile<T>(gen: Iterable<T>, pred: (v: T) => boolean): Generator<T> {
    for (const v of gen) {
        if (!pred(v)) return;
        yield v;
    }
}

// Lazy pipeline: primes below 50 via Sieve-inspired filter composition
function* naturals(start = 2): Generator<number> {
    let n = start;
    while (true) yield n++;
}

function* sieve(nums: Generator<number>): Generator<number> {
    const { value: prime } = nums.next();
    yield prime;
    // Recursively sieve out multiples — demonstrates generator delegation
    yield* sieve(filter(nums, n => n % prime !== 0) as Generator<number>);
}

// Note: sieve is recursive — works for small ranges
const primesBelow50 = [...takeWhile(sieve(naturals()), p => p < 50)];
console.log('Primes < 50:', primesBelow50);

// ─────────────────────────────────────────────────────────────────────────────
// 3. yield* — generator delegation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * yield* delegates to another iterable.
 * Equivalent to: for (const v of inner) yield v;
 */

function* flatten<T>(nested: T[][]): Generator<T> {
    for (const arr of nested) {
        yield* arr;   // delegate to inner array
    }
}

console.log('flatten:', [...flatten([[1,2],[3,4],[5]])]);  // [1,2,3,4,5]

// ─────────────────────────────────────────────────────────────────────────────
// 4. Two-way communication via yield (coroutine-like)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * `generator.next(value)` passes a value back INTO the generator.
 * The yield expression evaluates to the passed-in value.
 *
 * This enables coroutine-like behavior: caller and generator exchange data.
 */

function* calculator(): Generator<number, void, number> {
    let total = 0;
    while (true) {
        const addend: number = yield total;  // yield current total, receive next addend
        if (addend === undefined) break;
        total += addend;
    }
}

const calc = calculator();
calc.next();          // start (advance to first yield)
calc.next(10);        // add 10 → total = 10
calc.next(20);        // add 20 → total = 30
const { value: sum } = calc.next(5);   // add 5 → total = 35
console.log('Calculator total:', sum);  // 35

// ─────────────────────────────────────────────────────────────────────────────
// 5. Async generators
// ─────────────────────────────────────────────────────────────────────────────

/**
 * async function* — generator that can await Promises.
 * Used for: paginated APIs, chunked file reading, event streams, SSE.
 */

async function* paginatedFetch(pageCount: number): AsyncGenerator<{ page: number; data: string[] }> {
    for (let page = 1; page <= pageCount; page++) {
        // Simulate async fetch (would be: await fetch(`/api/items?page=${page}`))
        await Promise.resolve();
        yield {
            page,
            data: [`item-${page}-a`, `item-${page}-b`, `item-${page}-c`],
        };
    }
}

async function consumePages() {
    for await (const { page, data } of paginatedFetch(3)) {
        console.log(`Page ${page}:`, data);
    }
}

consumePages();

// ─────────────────────────────────────────────────────────────────────────────
// 6. Symbol.iterator — making objects iterable
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Any object with [Symbol.iterator]() method is iterable.
 * This enables: for..of, spread operator [...x], destructuring [a,b] = x.
 */

class InfiniteCounter implements Iterable<number> {
    constructor(private start = 0) {}

    [Symbol.iterator](): Iterator<number> {
        let current = this.start;
        return {
            next(): IteratorResult<number> {
                return { value: current++, done: false };
            }
        };
    }
}

const counter = new InfiniteCounter(10);
const firstFive = [];
for (const n of counter) {
    firstFive.push(n);
    if (firstFive.length === 5) break;
}
console.log('Counter:', firstFive);  // [10, 11, 12, 13, 14]

/**
 * Symbol.toPrimitive — customize type coercion.
 * Called when JS needs to convert an object to a primitive (number, string, default).
 */
class Money {
    constructor(
        private amount: number,
        private currency: string
    ) {}

    [Symbol.toPrimitive](hint: 'number' | 'string' | 'default'): number | string {
        if (hint === 'number')  return this.amount;
        if (hint === 'string')  return `${this.currency}${this.amount.toFixed(2)}`;
        return this.amount;   // default
    }
}

const price = new Money(9.99, '$');
console.log(`Price is ${price}`);   // "Price is $9.99" (string hint)
console.log(+price);                // 9.99 (number hint)

// Assertion helper used above
function assert(condition: boolean, message: string): asserts condition {
    if (!condition) throw new Error(`Assertion failed: ${message}`);
}

export {
    fibonacci, range, map, filter, takeWhile, flatten, paginatedFetch,
    ok, err, isOk, Result, ResultOK, ResultError,
    InfiniteCounter, Money, calculator
};
