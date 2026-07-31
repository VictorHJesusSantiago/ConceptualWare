package com.conceptualware.core.functional;

import java.util.*;
import java.util.function.*;

public class ApplicativeFunctor {

    public sealed interface Maybe<A> permits Maybe.Just, Maybe.Nothing {

        record Just<A>(A value) implements Maybe<A> {}
        record Nothing<A>()    implements Maybe<A> {}

        static <A> Maybe<A> just(A value) { return new Just<>(value); }
        static <A> Maybe<A> nothing()     { return new Nothing<>();    }
        static <A> Maybe<A> ofNullable(A value) {
            return value != null ? just(value) : nothing();
        }

        default <B> Maybe<B> map(Function<A, B> f) {
            return switch (this) {
                case Just<A> j  -> just(f.apply(j.value()));
                case Nothing<A> ignored -> nothing();
            };
        }

        static <A> Maybe<A> pure(A value) { return just(value); }

        static <A, B> Maybe<B> ap(Maybe<Function<A, B>> mf, Maybe<A> mv) {
            return switch (mf) {
                case Just<Function<A,B>> jf -> switch (mv) {
                    case Just<A> jv  -> just(jf.value().apply(jv.value()));
                    case Nothing<A> ignored -> nothing();
                };
                case Nothing<Function<A,B>> ignored -> nothing();
            };
        }

        static <A, B, C> Maybe<C> liftA2(BiFunction<A, B, C> f, Maybe<A> ma, Maybe<B> mb) {
            return ap(ma.map(a -> (B b) -> f.apply(a, b)), mb);
        }

        default <B> Maybe<B> flatMap(Function<A, Maybe<B>> f) {
            return switch (this) {
                case Just<A> j  -> f.apply(j.value());
                case Nothing<A> ignored -> nothing();
            };
        }

        default A getOrElse(A defaultValue) {
            return switch (this) {
                case Just<A> j  -> j.value();
                case Nothing<A> ignored -> defaultValue;
            };
        }

        default boolean isPresent() { return this instanceof Just; }
    }

    public sealed interface Validation<E, A>
            permits Validation.Valid, Validation.Invalid {

        record Valid<E, A>(A value)           implements Validation<E, A> {}
        record Invalid<E, A>(List<E> errors)  implements Validation<E, A> {}

        static <E, A> Validation<E, A> valid(A value)   { return new Valid<>(value); }
        static <E, A> Validation<E, A> invalid(E error) {
            return new Invalid<>(List.of(error));
        }
        static <E, A> Validation<E, A> pure(A value) { return valid(value); }

        default <B> Validation<E, B> map(Function<A, B> f) {
            return switch (this) {
                case Valid<E, A>   v -> valid(f.apply(v.value()));
                case Invalid<E, A> i -> new Invalid<>(i.errors());
            };
        }

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
                        yield new Invalid<>(combined);
                    }
                };
            };
        }

        static <E, A, B, C> Validation<E, C> liftA2(
                BiFunction<A, B, C> f,
                Validation<E, A> va,
                Validation<E, B> vb) {
            return ap(va.map(a -> (B b) -> f.apply(a, b)), vb);
        }

        static <E, A, B, C, D> Validation<E, D> liftA3(
                TriFunction<A, B, C, D> f,
                Validation<E, A> va,
                Validation<E, B> vb,
                Validation<E, C> vc) {
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

    public record UserForm(String username, String email, int age) {}

    public static Validation<String, UserForm> validateUser(
            String username, String email, int age) {

        Validation<String, String> validUsername = validateUsername(username);
        Validation<String, String> validEmail    = validateEmail(email);
        Validation<String, Integer> validAge     = validateAge(age);

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

    public static <A, B> List<B> listAp(List<Function<A, B>> fs, List<A> xs) {
        List<B> result = new ArrayList<>();
        for (var f : fs) for (var x : xs) result.add(f.apply(x));
        return result;
    }

    public static <A> List<A> listPure(A value) { return List.of(value); }

    public static List<String> generateCombinations(List<String> sizes, List<String> colors) {
        List<Function<String, String>> fns = sizes.stream()
            .<Function<String, String>>map(size -> color -> size + "-" + color)
            .toList();
        return listAp(fns, colors);
    }
}
