# ADR-001: Hexagonal Architecture (Ports & Adapters)

**Concept #24 — Architecture Decision Record (ADR)**

An ADR documents a significant architectural decision: the context, the options considered,
the decision made, and its consequences. ADRs create an immutable decision log that helps
future developers understand WHY the system is designed as it is.

---

**Date:** 2024-01-15
**Status:** ACCEPTED
**Deciders:** Platform Team
**Technical Domain:** Backend Architecture

---

## Context and Problem Statement

ConceptualWare's backend must implement 800+ software concepts across 30 categories.
The system needs to demonstrate database, OS, security, ML, and compiler concepts while
maintaining clean, testable code. Early prototypes mixed business logic with infrastructure
concerns (MongoDB queries directly in service methods), making it hard to:
- Test business logic without running a database
- Swap databases without touching business logic
- Add new concept categories without refactoring existing ones

## Decision Drivers

* Testability — unit tests must run without infrastructure dependencies
* Extensibility — adding new concept categories should be low-friction
* Demonstrate architecture concepts — the codebase itself teaches Clean Architecture
* Maintainability — clear boundaries between layers prevent accidental coupling
* Team scalability — parallel development on different concept categories

## Considered Options

**Option 1: Layered Architecture (N-tier)**
- Controller → Service → Repository → Database
- Simple, familiar, but creates tight coupling between layers
- Business logic often leaks into controllers or repositories

**Option 2: Hexagonal Architecture (Ports & Adapters)**
- Core domain (concepts, business logic) in center
- Ports: interfaces the domain exposes (inbound) or requires (outbound)
- Adapters: implementations connecting to external systems
- Dependencies always point INWARD toward the domain

**Option 3: Vertical Slice Architecture**
- Feature-based slices: each feature has its own handler, validator, repository
- Good for large teams, but may cause duplicate infrastructure code

## Decision

**Use Hexagonal Architecture (Option 2)**

### Structure:
```
backend/
├── core/                     ← DOMAIN (no framework dependencies)
│   ├── algorithms/           ← Algorithm concept implementations
│   ├── datastructures/       ← Data structure implementations
│   ├── ml/                   ← ML algorithms
│   ├── compiler/             ← Lexer, Parser, AST, Interpreter
│   ├── os/                   ← OS simulation concepts
│   ├── ports/
│   │   ├── in/               ← Inbound ports (use cases)
│   │   │   └── AlgorithmExecutionUseCase.java
│   │   └── out/              ← Outbound ports (repository interfaces)
│   │       ├── ConceptRepository.java
│   │       └── MetricsPort.java
│   └── domain/               ← Domain models (no @Entity annotations)
├── application/              ← Application services (orchestration)
│   └── AlgorithmService.java
├── infrastructure/           ← Adapters (framework-dependent)
│   ├── web/                  ← REST controllers (inbound adapter)
│   ├── persistence/          ← MongoDB repositories (outbound adapter)
│   ├── database/             ← H2, Redis, Cassandra concepts
│   └── security/             ← Spring Security configuration
└── ConceptualWareApplication.java
```

## Consequences

**Positive:**
- Core domain is pure Java — tested without Spring, MongoDB, Redis
- Swapping MongoDB for another DB: implement new outbound adapter only
- New concept categories: add to `core/` with no framework coupling
- `@SpringBootTest` tests can run with H2 instead of production MongoDB
- The architecture itself demonstrates Clean Architecture (Concept #X)

**Negative:**
- More interfaces and classes than layered architecture
- Developers unfamiliar with ports-and-adapters need onboarding time
- Can feel over-engineered for simple CRUD operations

**Mitigating factors:**
- Package-by-feature within each layer reduces navigation overhead
- The complexity is justified by the 800+ concepts requiring isolated testing
