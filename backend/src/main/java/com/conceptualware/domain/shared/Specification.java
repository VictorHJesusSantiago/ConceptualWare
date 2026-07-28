package com.conceptualware.domain.shared;

/**
 * Concept #12 — DDD: Specification Pattern — encapsula uma regra de negócio
 * combinável (predicado nomeado) usada tanto para validação em memória quanto,
 * potencialmente, traduzida para uma query de persistência.
 *
 *   Composição via and/or/not permite construir regras complexas a partir
 *   de regras simples, mantendo cada uma testável isoladamente (SRP).
 */
public interface Specification<T> {

    boolean isSatisfiedBy(T candidate);

    default Specification<T> and(Specification<T> other) {
        return candidate -> this.isSatisfiedBy(candidate) && other.isSatisfiedBy(candidate);
    }

    default Specification<T> or(Specification<T> other) {
        return candidate -> this.isSatisfiedBy(candidate) || other.isSatisfiedBy(candidate);
    }

    default Specification<T> not() {
        return candidate -> !this.isSatisfiedBy(candidate);
    }

    static <T> Specification<T> of(java.util.function.Predicate<T> predicate) {
        return predicate::test;
    }
}
