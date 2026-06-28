package com.conceptualware.core.functional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Applicative Functor")
class ApplicativeFunctorTest {

    // ── Maybe Functor ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Maybe.map: Just transforms value")
    void testMaybeMapJust() {
        var result = ApplicativeFunctor.Maybe.just(5).map(x -> x * 2);
        assertTrue(result.isPresent());
        assertEquals(10, ((ApplicativeFunctor.Maybe.Just<Integer>) result).value());
    }

    @Test
    @DisplayName("Maybe.map: Nothing propagates")
    void testMaybeMapNothing() {
        ApplicativeFunctor.Maybe<Integer> nothing = ApplicativeFunctor.Maybe.nothing();
        assertFalse(nothing.map(x -> x * 2).isPresent());
    }

    // ── Maybe Applicative ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Maybe.ap: Just(f) applied to Just(x) = Just(f(x))")
    void testMaybeApBothJust() {
        Function<Integer, String> fn = i -> "val=" + i;
        var mf = ApplicativeFunctor.Maybe.just(fn);
        var mv = ApplicativeFunctor.Maybe.just(42);
        var result = ApplicativeFunctor.Maybe.ap(mf, mv);
        assertEquals("val=42", ((ApplicativeFunctor.Maybe.Just<String>) result).value());
    }

    @Test
    @DisplayName("Maybe.ap: Nothing(f) applied to Just(x) = Nothing")
    void testMaybeApNothingFn() {
        ApplicativeFunctor.Maybe<Function<Integer, Integer>> mf = ApplicativeFunctor.Maybe.nothing();
        var mv = ApplicativeFunctor.Maybe.just(10);
        assertFalse(ApplicativeFunctor.Maybe.ap(mf, mv).isPresent());
    }

    @Test
    @DisplayName("Maybe.ap: Just(f) applied to Nothing = Nothing")
    void testMaybeApNothingVal() {
        var mf = ApplicativeFunctor.Maybe.just((Integer x) -> x + 1);
        ApplicativeFunctor.Maybe<Integer> mv = ApplicativeFunctor.Maybe.nothing();
        assertFalse(ApplicativeFunctor.Maybe.ap(mf, mv).isPresent());
    }

    @Test
    @DisplayName("Maybe.liftA2: combines two Just values")
    void testMaybeLiftA2() {
        var result = ApplicativeFunctor.Maybe.liftA2(
            Integer::sum,
            ApplicativeFunctor.Maybe.just(3),
            ApplicativeFunctor.Maybe.just(4)
        );
        assertEquals(7, ((ApplicativeFunctor.Maybe.Just<Integer>) result).value());
    }

    @Test
    @DisplayName("Maybe.liftA2: Nothing propagates")
    void testMaybeLiftA2Nothing() {
        var result = ApplicativeFunctor.Maybe.liftA2(
            Integer::sum,
            ApplicativeFunctor.Maybe.nothing(),
            ApplicativeFunctor.Maybe.just(4)
        );
        assertFalse(result.isPresent());
    }

    // ── Validation — error accumulation ───────────────────────────────────────

    @Test
    @DisplayName("Validation: all valid fields produce Valid result")
    void testValidationAllValid() {
        var result = ApplicativeFunctor.validateUser("alice99", "alice@example.com", 25);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Validation: invalid username accumulates one error")
    void testValidationInvalidUsername() {
        var result = ApplicativeFunctor.validateUser("a!", "good@email.com", 20);
        assertTrue(result.isInvalid());
        var errors = ((ApplicativeFunctor.Validation.Invalid<String, ApplicativeFunctor.UserForm>) result).errors();
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("username"));
    }

    @Test
    @DisplayName("Validation: ALL three invalid fields accumulate ALL three errors")
    void testValidationAllFieldsInvalid() {
        // blank username + bad email + underage
        var result = ApplicativeFunctor.validateUser("", "not-an-email", 5);
        assertTrue(result.isInvalid());
        var errors = ((ApplicativeFunctor.Validation.Invalid<String, ApplicativeFunctor.UserForm>) result).errors();
        assertEquals(3, errors.size(),
            "Applicative must accumulate ALL errors, not short-circuit");
    }

    @Test
    @DisplayName("Validation.ap: both Invalid sides combine their error lists")
    void testValidationApBothInvalid() {
        ApplicativeFunctor.Validation<String, Function<Integer, Integer>> vf =
            ApplicativeFunctor.Validation.invalid("error-from-function-side");
        ApplicativeFunctor.Validation<String, Integer> va =
            ApplicativeFunctor.Validation.invalid("error-from-value-side");

        var combined = ApplicativeFunctor.Validation.ap(vf, va);
        assertTrue(combined.isInvalid());
        var errors = ((ApplicativeFunctor.Validation.Invalid<String, Integer>) combined).errors();
        assertEquals(2, errors.size());
        assertTrue(errors.contains("error-from-function-side"));
        assertTrue(errors.contains("error-from-value-side"));
    }

    // ── List Applicative (cartesian product) ──────────────────────────────────

    @Test
    @DisplayName("listAp: cartesian product of functions × values")
    void testListAp() {
        List<Function<Integer, Integer>> fns = List.of(
            x -> x + 10,
            x -> x * 2
        );
        List<Integer> xs = List.of(1, 2, 3);
        var result = ApplicativeFunctor.listAp(fns, xs);
        // [f1(1), f1(2), f1(3), f2(1), f2(2), f2(3)]
        assertEquals(List.of(11, 12, 13, 2, 4, 6), result);
    }

    @Test
    @DisplayName("generateCombinations: size × color pairs")
    void testGenerateCombinations() {
        var combos = ApplicativeFunctor.generateCombinations(
            List.of("S", "M"),
            List.of("Red", "Blue")
        );
        assertEquals(List.of("S-Red", "S-Blue", "M-Red", "M-Blue"), combos);
    }
}
