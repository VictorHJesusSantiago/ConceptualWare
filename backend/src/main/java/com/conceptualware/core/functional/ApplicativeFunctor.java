package com.conceptualware.core.functional;

import java.util.*;
import java.util.function.*;

/**
 * Concept #8 — Applicative Functor
 *
 * Type class hierarchy in functional programming:
 *   Functor → Applicative → Monad
 *
 * ┌──────────────┬──────────────────────────────────────────────────────────┐
 * │ Typeclass    │ Core operation                                            │
 * ├──────────────┼──────────────────────────────────────────────────────────┤
 * │ Functor      │ map:  F<A> → (A→B) → F<B>            (transform value)  │
 * │ Applicative  │ pure: A → F<A>                        (lift into context) │
 * │              │ ap:   F<A→B> → F<A> → F<B>           (apply wrapped fn) │
 * │ Monad        │ flatMap: F<A> → (A→F<B>) → F<B>      (chain effects)    │
 * └──────────────┴──────────────────────────────────────────────────────────┘
 *
 * Applicative sits between Functor and Monad:
 *   - More powerful than Functor (can apply MULTIPLE independent effects)
 *   - Less powerful than Monad (effects are independent — no sequencing based on value)
 *
 * The KEY insight of Applicative:
 *   You have a FUNCTION in a context F<A→B> and a VALUE in a context F<A>.
 *   Applicative applies them: ap(F<A→B>, F<A>) = F<B>.
 *
 * Applicative laws:
 *   1. Identity:     ap(pure(id), v) = v
 *   2. Homomorphism: ap(pure(f), pure(x)) = pure(f(x))
 *   3. Interchange:  ap(u, pure(y)) = ap(pure(f→f(y)), u)
 *   4. Composition:  ap(ap(ap(pure(∘), u), v), w) = ap(u, ap(v, w))
 *
 * Practical use: validate/combine INDEPENDENT fields with accumulated errors.
 * (Monad would short-circuit on first error; Applicative collects ALL errors.)
 */
public class ApplicativeFunctor {

    // ── Maybe<A> — Functor, Applicative, Monad ─────────────────────────────────
    /**
     * Maybe<A> (also called Option<A>) models a value that might be absent.
     *
     * As Functor:      maybe.map(f)      → apply f if present, propagate absence
     * As Applicative:  Maybe.ap(mf, mv)  → apply wrapped function to wrapped value
     * As Monad:        maybe.flatMap(f)  → chain computations that might fail
     */
    public sealed interface Maybe<A> permits Maybe.Just, Maybe.Nothing {

        record Just<A>(A value) implements Maybe<A> {}
        record Nothing<A>()    implements Maybe<A> {}

        // ── Constructors ──────────────────────────────────────────────────────

        static <A> Maybe<A> just(A value) { return new Just<>(value); }
        static <A> Maybe<A> nothing()     { return new Nothing<>();    }
        static <A> Maybe<A> ofNullable(A value) {
            return value != null ? just(value) : nothing();
        }

        // ── Functor: map ──────────────────────────────────────────────────────

        default <B> Maybe<B> map(Function<A, B> f) {
            return switch (this) {
                case Just<A> j  -> just(f.apply(j.value()));
                case Nothing<A> ignored -> nothing();
            };
        }

        // ── Applicative: pure (lift) ───────────────────────────────────────────

        /** Lift a value into the Maybe context. */
        static <A> Maybe<A> pure(A value) { return just(value); }

        /**
         * ap: apply a wrapped function to a wrapped value.
         *   ap(Just(f), Just(x))      = Just(f(x))
         *   ap(Nothing, _)            = Nothing
         *   ap(_, Nothing)            = Nothing
         *
         * Key difference from Monad: BOTH sides are evaluated eagerly —
         * you cannot skip mf based on the content of mv.
         */
        static <A, B> Maybe<B> ap(Maybe<Function<A, B>> mf, Maybe<A> mv) {
            return switch (mf) {
                case Just<Function<A,B>> jf -> switch (mv) {
                    case Just<A> jv  -> just(jf.value().apply(jv.value()));
                    case Nothing<A> ignored -> nothing();
                };
                case Nothing<Function<A,B>> ignored -> nothing();
            };
        }

        /**
         * liftA2: combine two independent Maybe values with a binary function.
         *   liftA2(f, Just(a), Just(b)) = Just(f(a, b))
         *   liftA2(f, Nothing, Just(b)) = Nothing
         *
         * This is what makes Applicative practical: combine independent effects.
         */
        static <A, B, C> Maybe<C> liftA2(BiFunction<A, B, C> f, Maybe<A> ma, Maybe<B> mb) {
            return ap(ma.map(a -> (B b) -> f.apply(a, b)), mb);
        }

        // ── Monad: flatMap ────────────────────────────────────────────────────

        default <B> Maybe<B> flatMap(Function<A, Maybe<B>> f) {
            return switch (this) {
                case Just<A> j  -> f.apply(j.value());
                case Nothing<A> ignored -> nothing();
            };
        }

        // ── Utilities ─────────────────────────────────────────────────────────

        default A getOrElse(A defaultValue) {
            return switch (this) {
                case Just<A> j  -> j.value();
                case Nothing<A> ignored -> defaultValue;
            };
        }

        default boolean isPresent() { return this instanceof Just; }
    }

    // ── Validation<E, A> — Applicative that ACCUMULATES errors ─────────────────
    /**
     * Validation<E, A> is an Applicative but NOT a Monad.
     *
     * The critical difference from Either/Result (which IS a Monad):
     *   Either.flatMap: SHORT-CIRCUITS on first error (sequential dependency)
     *   Validation.ap:  COLLECTS ALL errors (independent validations)
     *
     * Example — form validation:
     *   Monad (Either): fails on username error, doesn't check email at all
     *   Applicative:    validates username AND email AND age — reports ALL failures
     *
     * This is why Applicative matters in practice: form validation, config parsing,
     * JSON decoding — you want ALL errors, not just the first one.
     */
    public sealed interface Validation<E, A>
            permits Validation.Valid, Validation.Invalid {

        record Valid<E, A>(A value)           implements Validation<E, A> {}
        record Invalid<E, A>(List<E> errors)  implements Validation<E, A> {}

        // ── Constructors ──────────────────────────────────────────────────────

        static <E, A> Validation<E, A> valid(A value)   { return new Valid<>(value); }
        static <E, A> Validation<E, A> invalid(E error) {
            return new Invalid<>(List.of(error));
        }
        static <E, A> Validation<E, A> pure(A value) { return valid(value); }

        // ── Functor: map ──────────────────────────────────────────────────────

        default <B> Validation<E, B> map(Function<A, B> f) {
            return switch (this) {
                case Valid<E, A>   v -> valid(f.apply(v.value()));
                case Invalid<E, A> i -> new Invalid<>(i.errors());
            };
        }

        /**
         * ap: ACCUMULATING errors — the defining feature of Validation.
         *
         *   ap(Valid(f),   Valid(a))    = Valid(f(a))
         *   ap(Invalid(e), Valid(_))    = Invalid(e)          // propagate
         *   ap(Valid(_),   Invalid(e))  = Invalid(e)          // propagate
         *   ap(Invalid(e1),Invalid(e2)) = Invalid(e1 ++ e2)   // ACCUMULATE both!
         *
         * A Monad cannot accumulate: flatMap's second argument depends on the first.
         * Applicative's ap is independent: mf and mv are evaluated regardless.
         */
        static <E, A, B> Validation<E, B> ap(
                Validation<E, Function<A, B>> vf,
                Validation<E, A> va) {
            return switch (vf) {
                case Valid<E, Function<A,B>> jf -> switch (va) {
                    case Valid<E, A>   jv -> valid(jf.value().apply(jv.value()));
                    case Invalid<E, A> iv -> new Invalid<>(iv.errors());
                };
                case Invalid<E, Function<A,B>> ef -> switch (va) {
                    case Valid<E, A>    ignored   -> new Invalid<>(ef.errors());
                    case Invalid<E, A>  ev -> {
                        List<E> combined = new ArrayList<>(ef.errors());
                        combined.addAll(ev.errors());
                        yield new Invalid<>(combined);  // ← accumulation
                    }
                };
            };
        }

        /** liftA2: combine two independent Validations. */
        static <E, A, B, C> Validation<E, C> liftA2(
                BiFunction<A, B, C> f,
                Validation<E, A> va,
                Validation<E, B> vb) {
            return ap(va.map(a -> (B b) -> f.apply(a, b)), vb);
        }

        /** liftA3: combine three independent Validations. */
        static <E, A, B, C, D> Validation<E, D> liftA3(
                TriFunction<A, B, C, D> f,
                Validation<E, A> va,
                Validation<E, B> vb,
                Validation<E, C> vc) {
            // ap(ap(va.map(curry(f)), vb), vc)
            Validation<E, Function<B, Function<C, D>>> curried =
                va.map(a -> b -> c -> f.apply(a, b, c));
            Validation<E, Function<C, D>> partial = ap(curried, vb);
            return ap(partial, vc);
        }

        default boolean isValid()   { return this instanceof Valid; }
        default boolean isInvalid() { return this instanceof Invalid; }
    }

    @FunctionalInterface
    interface TriFunction<A, B, C, D> { D apply(A a, B b, C c); }

    // ── Form validation example using Applicative accumulation ────────────────
    /**
     * Practical example: register a new user.
     * All fields are validated INDEPENDENTLY — all errors reported at once.
     */
    public record UserForm(String username, String email, int age) {}

    public static Validation<String, UserForm> validateUser(
            String username, String email, int age) {

        Validation<String, String> validUsername = validateUsername(username);
        Validation<String, String> validEmail    = validateEmail(email);
        Validation<String, Integer> validAge     = validateAge(age);

        // liftA3: if ALL three are valid, construct UserForm.
        // If ANY are invalid, ACCUMULATE all error messages.
        return Validation.liftA3(UserForm::new, validUsername, validEmail, validAge);
    }

    private static Validation<String, String> validateUsername(String name) {
        if (name == null || name.isBlank())
            return Validation.invalid("username: must not be blank");
        if (name.length() < 3)
            return Validation.invalid("username: must be at least 3 characters");
        if (!name.matches("[a-zA-Z0-9_]+"))
            return Validation.invalid("username: only alphanumeric and underscore allowed");
        return Validation.valid(name);
    }

    private static Validation<String, String> validateEmail(String email) {
        if (email == null || !email.contains("@"))
            return Validation.invalid("email: invalid format");
        return Validation.valid(email.toLowerCase());
    }

    private static Validation<String, Integer> validateAge(int age) {
        if (age < 13)  return Validation.invalid("age: must be at least 13");
        if (age > 120) return Validation.invalid("age: unrealistic value");
        return Validation.valid(age);
    }

    // ── Applicative for List<A> — "cartesian product" effect ─────────────────
    /**
     * List is also an Applicative. Its ap = cartesian product of functions × values.
     *
     *   ap([f, g], [x, y]) = [f(x), f(y), g(x), g(y)]
     *
     * Useful for: generating all combinations of independent choices.
     */
    public static <A, B> List<B> listAp(List<Function<A, B>> fs, List<A> xs) {
        List<B> result = new ArrayList<>();
        for (var f : fs) for (var x : xs) result.add(f.apply(x));
        return result;
    }

    public static <A> List<A> listPure(A value) { return List.of(value); }

    /**
     * Example: generate all combinations of clothing.
     *   [Small, Medium] × [Red, Blue] = [(Small,Red),(Small,Blue),(Medium,Red),(Medium,Blue)]
     */
    public static List<String> generateCombinations(List<String> sizes, List<String> colors) {
        List<Function<String, String>> fns = sizes.stream()
            .<Function<String, String>>map(size -> color -> size + "-" + color)
            .toList();
        return listAp(fns, colors);
    }
}
