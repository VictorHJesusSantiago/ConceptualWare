package com.conceptualware.core.functional;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class FunctionalUtils {

    public static <A, B, C> Function<A, Function<B, C>> curry(BiFunction<A, B, C> f) {
        return a -> b -> f.apply(a, b);
    }

    public static <A, B, C> Function<B, C> partial(BiFunction<A, B, C> f, A a) {
        return b -> f.apply(a, b);
    }

    public static <A, B, C> Function<A, C> compose(Function<B, C> f, Function<A, B> g) {
        return x -> f.apply(g.apply(x));
    }

    @SafeVarargs
    public static <T> Function<T, T> pipeline(Function<T, T>... fns) {
        return Arrays.stream(fns).reduce(Function.identity(), Function::andThen);
    }

    public static <T, R> Function<T, R> memoize(Function<T, R> fn) {
        Map<T, R> cache = new HashMap<>();
        return input -> cache.computeIfAbsent(input, fn);
    }

    public sealed interface Result<T> permits Result.Ok, Result.Err {
        record Ok<T>(T value) implements Result<T> {}
        record Err<T>(String error) implements Result<T> {}

        default <R> Result<R> map(Function<T, R> f) {
            return switch (this) {
                case Ok<T> ok -> new Ok<>(f.apply(ok.value()));
                case Err<T> err -> new Err<>(err.error());
            };
        }

        default <R> Result<R> flatMap(Function<T, Result<R>> f) {
            return switch (this) {
                case Ok<T> ok -> f.apply(ok.value());
                case Err<T> err -> new Err<>(err.error());
            };
        }

        default T getOrElse(T fallback) {
            return switch (this) {
                case Ok<T> ok -> ok.value();
                case Err<T> ignored -> fallback;
            };
        }

        default boolean isOk() { return this instanceof Ok; }
        default boolean isErr() { return this instanceof Err; }

        static <T> Result<T> of(Supplier<T> supplier) {
            try { return new Ok<>(supplier.get()); }
            catch (Exception e) { return new Err<>(e.getMessage()); }
        }
    }

    public sealed interface Maybe<T> permits Maybe.Just, Maybe.Nothing {
        record Just<T>(T value) implements Maybe<T> {}
        record Nothing<T>() implements Maybe<T> {}

        default <R> Maybe<R> map(Function<T, R> f) {
            return switch (this) {
                case Just<T> j -> new Just<>(f.apply(j.value()));
                case Nothing<T> n -> new Nothing<>();
            };
        }

        default <R> Maybe<R> flatMap(Function<T, Maybe<R>> f) {
            return switch (this) {
                case Just<T> j -> f.apply(j.value());
                case Nothing<T> n -> new Nothing<>();
            };
        }

        default T getOrElse(T fallback) {
            return switch (this) {
                case Just<T> j -> j.value();
                case Nothing<T> n -> fallback;
            };
        }

        static <T> Maybe<T> of(T value) { return value != null ? new Just<>(value) : new Nothing<>(); }
        static <T> Maybe<T> nothing()    { return new Nothing<>(); }
    }

    public interface Monoid<T> {
        T identity();
        T combine(T a, T b);

        default T fold(List<T> list) {
            return list.stream().reduce(identity(), this::combine);
        }
    }

    public static final Monoid<Integer> SUM_MONOID = new Monoid<>() {
        public Integer identity()            { return 0; }
        public Integer combine(Integer a, Integer b) { return a + b; }
    };

    public static final Monoid<String> STRING_MONOID = new Monoid<>() {
        public String identity()             { return ""; }
        public String combine(String a, String b) { return a + b; }
    };

    public static class Lazy<T> {
        private Supplier<T> supplier;
        private T value;
        private boolean computed = false;

        public Lazy(Supplier<T> supplier) { this.supplier = supplier; }

        public T get() {
            if (!computed) { value = supplier.get(); computed = true; supplier = null; }
            return value;
        }

        public <R> Lazy<R> map(Function<T, R> f) {
            return new Lazy<>(() -> f.apply(get()));
        }
    }

    public sealed interface Trampoline<T> permits Trampoline.Done, Trampoline.More {
        record Done<T>(T result) implements Trampoline<T> {}
        record More<T>(Supplier<Trampoline<T>> next) implements Trampoline<T> {}

        default T run() {
            Trampoline<T> current = this;
            while (current instanceof More<T> more) current = more.next().get();
            return ((Done<T>) current).result();
        }
    }

    public static Trampoline<Long> factorialTrampoline(long n, long acc) {
        if (n <= 1) return new Trampoline.Done<>(acc);
        return new Trampoline.More<>(() -> factorialTrampoline(n - 1, n * acc));
    }

    public static <T, R> List<R> map(List<T> list, Function<T, R> f) {
        return list.stream().map(f).collect(Collectors.toList());
    }

    public static <T> List<T> filter(List<T> list, Predicate<T> pred) {
        return list.stream().filter(pred).collect(Collectors.toList());
    }

    public static <T, R> R reduce(List<T> list, R identity, BiFunction<R, T, R> accumulator) {
        R result = identity;
        for (T item : list) result = accumulator.apply(result, item);
        return result;
    }

    public static <T, R> List<R> flatMap(List<T> list, Function<T, List<R>> f) {
        return list.stream().flatMap(x -> f.apply(x).stream()).collect(Collectors.toList());
    }

    public sealed interface Applicative<T> permits Applicative.Pure, Applicative.Empty {
        record Pure<T>(T value) implements Applicative<T> {}
        record Empty<T>() implements Applicative<T> {}

        static <T> Applicative<T> pure(T value) { return new Pure<>(value); }
        static <T> Applicative<T> empty()        { return new Empty<>(); }

        default <R> Applicative<R> map(Function<T, R> f) {
            return switch (this) {
                case Pure<T> p  -> new Pure<>(f.apply(p.value()));
                case Empty<T> e -> new Empty<>();
            };
        }

        default <R> Applicative<R> ap(Applicative<Function<T, R>> wrappedFn) {
            return switch (wrappedFn) {
                case Pure<Function<T,R>> fn -> this.map(fn.value());
                case Empty<Function<T,R>> e -> new Empty<>();
            };
        }

        default <R> Applicative<R> andThen(Applicative<R> next) {
            return switch (this) {
                case Pure<T> p  -> next;
                case Empty<T> e -> new Empty<>();
            };
        }

        default T getOrDefault(T fallback) {
            return switch (this) {
                case Pure<T> p  -> p.value();
                case Empty<T> e -> fallback;
            };
        }
    }

    public static <T, R> boolean verifyApplicativeHomomorphism(T x, Function<T, R> f) {
        var lhs = Applicative.pure(x).ap(Applicative.pure(f));
        var rhs = Applicative.<R>pure(f.apply(x));
        return lhs.equals(rhs);
    }

    @FunctionalInterface
    public interface IO<T> {
        T unsafeRun();

        static <T> IO<T> of(T value)          { return () -> value; }
        static <T> IO<T> effect(IO<T> action) { return action; }

        default <R> IO<R> map(Function<T, R> f)        { return () -> f.apply(this.unsafeRun()); }
        default <R> IO<R> flatMap(Function<T, IO<R>> f){ return () -> f.apply(this.unsafeRun()).unsafeRun(); }

        default <R> IO<R> andThen(IO<R> next) { return () -> { this.unsafeRun(); return next.unsafeRun(); }; }
    }

    public static <T> List<T> cons(T head, List<T> tail) {
        List<T> result = new ArrayList<>();
        result.add(head);
        result.addAll(tail);
        return Collections.unmodifiableList(result);
    }

    public static <T> List<T> append(List<T> list, T element) {
        List<T> result = new ArrayList<>(list);
        result.add(element);
        return Collections.unmodifiableList(result);
    }
}
