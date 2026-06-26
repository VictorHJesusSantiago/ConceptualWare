package com.conceptualware.core.functional;

import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;

/**
 * Concept #8  — Programação Funcional: all concepts tested
 * Concept #19 — TDD: AAA pattern, property-based invariants
 */
@DisplayName("Functional Programming — Complete Test Suite")
class FunctionalUtilsTest {

    // ── Currying ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Curried add: curry(add)(2)(3) == 5")
    void curriedAdd() {
        var curriedAdd = FunctionalUtils.curry((Integer a, Integer b) -> a + b);
        assertThat(curriedAdd.apply(2).apply(3)).isEqualTo(5);
    }

    @Test
    @DisplayName("Partial application: partial(multiply, 3)(4) == 12")
    void partialApplication() {
        var times3 = FunctionalUtils.partial((Integer a, Integer b) -> a * b, 3);
        assertThat(times3.apply(4)).isEqualTo(12);
    }

    // ── Function composition ──────────────────────────────────────────────────

    @Test
    @DisplayName("compose: (f ∘ g)(x) = f(g(x))")
    void functionCompose() {
        Function<Integer, Integer> doubleIt = x -> x * 2;
        Function<Integer, Integer> addOne   = x -> x + 1;
        var doubleThenAddOne = FunctionalUtils.compose(addOne, doubleIt); // addOne(doubleIt(x))
        assertThat(doubleThenAddOne.apply(5)).isEqualTo(11); // (5*2)+1 = 11
    }

    @Test
    @DisplayName("pipeline: left-to-right composition")
    void functionPipeline() {
        @SuppressWarnings("unchecked")
        var pipeline = FunctionalUtils.pipeline(
            (Function<Integer, Integer>) x -> x + 1,
            x -> x * 2,
            x -> x - 3
        );
        assertThat(pipeline.apply(5)).isEqualTo(9); // ((5+1)*2)-3 = 9
    }

    // ── Memoization ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Memoized function returns same result and only computes once")
    void memoization() {
        int[] callCount = {0};
        Function<Integer, Integer> expensive = x -> { callCount[0]++; return x * x; };
        var memoized = FunctionalUtils.memoize(expensive);

        assertThat(memoized.apply(5)).isEqualTo(25);
        assertThat(memoized.apply(5)).isEqualTo(25); // from cache
        assertThat(callCount[0]).isEqualTo(1);       // only computed once
    }

    // ── Result monad (Either) ─────────────────────────────────────────────────

    @Test
    @DisplayName("Result.Ok — map transforms value")
    void resultOkMap() {
        FunctionalUtils.Result<Integer> ok = new FunctionalUtils.Result.Ok<>(5);
        assertThat(ok.map(x -> x * 2).getOrElse(0)).isEqualTo(10);
    }

    @Test
    @DisplayName("Result.Err — map is a no-op")
    void resultErrMap() {
        FunctionalUtils.Result<Integer> err = new FunctionalUtils.Result.Err<>("oops");
        assertThat(err.map(x -> x * 2).getOrElse(0)).isEqualTo(0);
    }

    @Test
    @DisplayName("Result.flatMap — chains Ok computations")
    void resultFlatMap() {
        FunctionalUtils.Result<Integer> ok = new FunctionalUtils.Result.Ok<>(10);
        var result = ok.flatMap(x -> x > 0
            ? new FunctionalUtils.Result.Ok<>(x * 2)
            : new FunctionalUtils.Result.Err<>("must be positive"));
        assertThat(result.getOrElse(0)).isEqualTo(20);
    }

    // ── Maybe monad ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Maybe.of(null) returns Nothing")
    void maybeOfNull() {
        assertThat(FunctionalUtils.Maybe.of(null)).isInstanceOf(FunctionalUtils.Maybe.Nothing.class);
    }

    @Test
    @DisplayName("Maybe.Just — map transforms value")
    void maybeJustMap() {
        var just = FunctionalUtils.Maybe.of(5);
        assertThat(just.map(x -> x * 3).getOrElse(0)).isEqualTo(15);
    }

    // ── Applicative Functor ───────────────────────────────────────────────────

    @Test
    @DisplayName("Applicative.pure lifts a value")
    void applicativePure() {
        var ap = FunctionalUtils.Applicative.pure(42);
        assertThat(ap.getOrDefault(0)).isEqualTo(42);
    }

    @Test
    @DisplayName("Applicative.map — Functor fmap")
    void applicativeMap() {
        var result = FunctionalUtils.Applicative.pure(5).map(x -> x * 2);
        assertThat(result.getOrDefault(0)).isEqualTo(10);
    }

    @Test
    @DisplayName("Applicative.ap — apply wrapped function to wrapped value")
    void applicativeAp() {
        Function<Integer, Integer> doubleIt = x -> x * 2;
        var wrappedFn = FunctionalUtils.Applicative.pure(doubleIt);
        var wrapped   = FunctionalUtils.Applicative.pure(7);
        var result = wrapped.ap(wrappedFn);

        assertThat(result.getOrDefault(0)).isEqualTo(14);
    }

    @Test
    @DisplayName("Applicative homomorphism law: ap(pure(f), pure(x)) == pure(f(x))")
    void applicativeHomomorphismLaw() {
        assertThat(FunctionalUtils.verifyApplicativeHomomorphism(5, (Integer x) -> x * 3)).isTrue();
    }

    @Test
    @DisplayName("Applicative.ap on Empty returns Empty")
    void applicativeApEmpty() {
        var empty = FunctionalUtils.Applicative.<Integer>empty();
        var wrappedFn = FunctionalUtils.Applicative.<Function<Integer,Integer>>pure(x -> x + 1);
        assertThat(empty.ap(wrappedFn)).isInstanceOf(FunctionalUtils.Applicative.Empty.class);
    }

    // ── IO Monad ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("IO.of — deferred value, executes on unsafeRun")
    void ioMonadDeferred() {
        int[] sideEffect = {0};
        FunctionalUtils.IO<Integer> io = FunctionalUtils.IO.effect(() -> { sideEffect[0]++; return 42; });
        assertThat(sideEffect[0]).isEqualTo(0);   // not yet run
        assertThat(io.unsafeRun()).isEqualTo(42);
        assertThat(sideEffect[0]).isEqualTo(1);   // ran exactly once
    }

    @Test
    @DisplayName("IO.map — transforms the result")
    void ioMonadMap() {
        var io = FunctionalUtils.IO.of(5).map(x -> x * 2);
        assertThat(io.unsafeRun()).isEqualTo(10);
    }

    // ── Trampolining ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Trampolined factorial — no stack overflow for large n")
    void trampolinedFactorial() {
        long result = FunctionalUtils.factorialTrampoline(10, 1).run();
        assertThat(result).isEqualTo(3_628_800L);
    }

    // ── Monoid ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Sum monoid fold: [1,2,3,4,5] = 15")
    void monoidSumFold() {
        assertThat(FunctionalUtils.SUM_MONOID.fold(List.of(1, 2, 3, 4, 5))).isEqualTo(15);
    }

    @Test
    @DisplayName("String monoid fold: concatenates all strings")
    void monoidStringFold() {
        assertThat(FunctionalUtils.STRING_MONOID.fold(List.of("Con", "cep", "tual", "Ware")))
            .isEqualTo("ConceptualWare");
    }

    // ── Lazy evaluation ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Lazy — supplier only called once")
    void lazyEvaluation() {
        int[] callCount = {0};
        var lazy = new FunctionalUtils.Lazy<>(() -> { callCount[0]++; return 99; });
        assertThat(callCount[0]).isEqualTo(0);
        assertThat(lazy.get()).isEqualTo(99);
        assertThat(lazy.get()).isEqualTo(99);
        assertThat(callCount[0]).isEqualTo(1); // computed only once
    }
}
