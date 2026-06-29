# Concept #23 — Scrum Framework

Scrum is an Agile framework for developing complex products using iterative, incremental
delivery. It provides roles, events, and artifacts to help teams self-organize and adapt.

---

## Core Principles

- **Empiricism**: decisions based on observed reality, not speculation
- **Transparency**: progress visible to all stakeholders
- **Inspection**: regular review of work and process
- **Adaptation**: adjust plan based on what's learned

---

## Scrum Roles

| Role | Responsibilities |
|------|-----------------|
| **Product Owner** | Maximizes product value; owns and prioritizes Product Backlog; defines acceptance criteria |
| **Scrum Master** | Coaches team on Scrum; removes impediments; facilitates events; shields team from interruptions |
| **Development Team** | Self-organizing; cross-functional; 3-9 people; accountable for Sprint Goal |

---

## Scrum Events

### Sprint (1-4 weeks, fixed-length)
- All work happens within a Sprint
- Goal fixed once committed (no scope creep during Sprint)
- Cancelled only by Product Owner (rarely)

### Sprint Planning (max 8h for 4-week Sprint)
- **What**: Select PBIs from top of backlog that fit capacity
- **How**: Break PBIs into tasks; estimate effort
- Output: Sprint Goal + Sprint Backlog

### Daily Scrum (15 min, same time/place)
- What did I do yesterday toward Sprint Goal?
- What will I do today toward Sprint Goal?
- Any impediments?
- NOT a status meeting for managers

### Sprint Review (max 4h for 4-week Sprint)
- Team demonstrates working software to stakeholders
- Product Owner declares what is DONE
- Feedback incorporated into Product Backlog

### Sprint Retrospective (max 3h for 4-week Sprint)
- Inspect: what went well, what didn't, what could improve
- Adapt: commit to 1-3 process improvements for next Sprint
- Focus: people, relationships, process, tools

---

## Scrum Artifacts

### Product Backlog
- Ordered list of all desired product work
- Product Owner owns it; team estimates it
- Items (PBIs): User Stories, Bugs, Technical Debt, Spikes
- **Definition of Ready** (DoR): PBI is small enough, has acceptance criteria, estimated

### Sprint Backlog
- PBIs selected for this Sprint + task breakdown
- Team owns it — self-organizing to achieve Sprint Goal
- Updated daily

### Increment
- Sum of all completed PBIs in current + all prior Sprints
- Must meet **Definition of Done** (DoD)
- Potentially shippable even if not shipped

### Definition of Done (DoD) — ConceptualWare

```
A concept is DONE when:
□ All code is reviewed and merged to main
□ Unit tests written with > 80% coverage
□ Integration tests passing (CI green)
□ API documented in Swagger
□ Concept explanation written
□ Time/Space complexity documented
□ At least 2 challenge problems created
□ Performance benchmarks recorded
□ No P1/P2 bugs open
□ Feature flag enabled for 10% canary
```

---

## Story Point Estimation

Fibonacci sequence: 1, 2, 3, 5, 8, 13, 21, ?

| Points | Effort | Example |
|--------|--------|---------|
| 1 | Trivial | Add a field to existing DTO |
| 2 | Simple | Implement a simple search endpoint |
| 3 | Small | Implement Bubble Sort with tests |
| 5 | Medium | Implement Red-Black Tree insert with tests |
| 8 | Large | Implement full LSTM from scratch |
| 13 | Very Large | Full compiler pipeline (Lexer→Interpreter) |
| 21 | Epic (split!) | All 30 ML algorithms |

---

## ConceptualWare Sprint Template

```
Sprint 12 — Category 10: Compiler Concepts
Duration: Nov 4 – Nov 17, 2024 (2 weeks)
Sprint Goal: Developers can run ConceptLang programs in the REPL

Sprint Backlog:
  PBI-201: Lexer + Token types           [5 pts] → Alice
  PBI-202: Recursive descent Parser      [8 pts] → Bob
  PBI-203: AST node hierarchy            [3 pts] → Alice
  PBI-204: Semantic Analyzer             [8 pts] → Bob, Carol
  PBI-205: IR Generator (TAC)            [5 pts] → Carol
  PBI-206: Optimizer (constant folding)  [5 pts] → Dave
  PBI-207: Interpreter + REPL            [8 pts] → Dave, Alice
  PBI-208: Compiler test suite           [3 pts] → Team
  PBI-209: Concept documentation         [2 pts] → Alice

  Total: 47 points (team velocity: 45-50)

Daily Standup: 09:00 UTC, #standup-channel
Sprint Review: Nov 17 at 15:00 UTC
Retrospective: Nov 17 at 16:30 UTC
```

---

## Kanban vs Scrum

| Aspect | Scrum | Kanban |
|--------|-------|--------|
| Iterations | Fixed-length Sprints | Continuous flow |
| Roles | PO, SM, Dev Team | No prescribed roles |
| WIP limits | Implied by Sprint | Explicit per column |
| Change policy | Sprint boundary | Anytime (if WIP allows) |
| Cadence | Sprint ceremonies | On-demand reviews |
| Best for | New product development | Support/operations/maintenance |

---

## SAFe (Scaled Agile Framework) — Brief Overview

SAFe extends Scrum to large organizations (50+ teams):

- **Team Level**: Scrum teams (2-week sprints)
- **Program Level**: Agile Release Train (ART) — 5-12 teams synchronized in 10-week PI
- **Portfolio Level**: Value streams, strategy alignment, Lean budgeting
- **Enterprise Level**: Full organizational transformation

**PI Planning** (Program Increment Planning):
- 2-day event every 10 weeks bringing ALL teams together
- Align on PI objectives, dependencies, risks
- Each team creates their iteration plan for the next 10 weeks
- Output: Committed PI Objectives, Risk Board, Team PI Plans

---

## XP (Extreme Programming) Practices

| Practice | Description |
|----------|-------------|
| TDD | Write failing test → make it pass → refactor |
| Pair Programming | Two developers at one keyboard |
| Continuous Integration | Integrate to main multiple times per day |
| Collective Code Ownership | Anyone can change any code |
| Refactoring | Continuous improvement of design |
| Simple Design | YAGNI — don't build what you don't need yet |
| Small Releases | Release frequently (weekly or more) |
| On-site Customer | Real customer available to answer questions |

---

## Lean Software Development (7 Principles)

1. **Eliminate Waste**: anything that doesn't add customer value
2. **Amplify Learning**: short iteration cycles, feedback loops
3. **Decide Late**: defer decisions until last responsible moment
4. **Deliver Fast**: small batch sizes, reduce WIP
5. **Empower the Team**: decentralize decision-making
6. **Build Integrity In**: quality from the start, not inspection at end
7. **See the Whole**: optimize the system, not local parts

**Value Stream Mapping**: visualize flow from customer request to delivery,
identify bottlenecks (queues, handoffs, delays) → eliminate non-value-added steps.
