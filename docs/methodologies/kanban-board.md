# Concept #23 — Kanban: Board Definition & Policies

Kanban is a lean method for managing and improving work across human systems.
It visualizes work, limits work-in-progress (WIP), and maximizes flow.

---

## ConceptualWare Kanban Board

```
╔═══════════════╦══════════════╦══════════════╦══════════════╦═════════════╗
║  BACKLOG      ║  ANALYSIS    ║  DEV         ║  REVIEW      ║  DONE       ║
║  (∞)          ║  WIP: 3      ║  WIP: 5      ║  WIP: 3      ║  (∞)        ║
╠═══════════════╬══════════════╬══════════════╬══════════════╬═════════════╣
║ [CONCEPT-401] ║ [CONCEPT-201]║ [CONCEPT-301]║ [CONCEPT-101]║ [CONCEPT-01]║
║ SVM           ║ Transformer  ║ LSTM         ║ Red-Black    ║ Binary      ║
║ Implementation║ Attention    ║ Gate logic   ║ Tree         ║ Search      ║
║ Priority: HIGH║ 3d remaining ║ 2d remaining ║ In review    ║ ✓ Done      ║
╠═══════════════╬══════════════╬══════════════╬══════════════╬═════════════╣
║ [CONCEPT-402] ║ [CONCEPT-202]║ [CONCEPT-302]║ [CONCEPT-102]║ [CONCEPT-02]║
║ XGBoost       ║ BPE          ║ RAG Pipeline ║ Splay Tree   ║ Quick Sort  ║
║ Gradient boost║ Tokenization ║              ║              ║ ✓ Done      ║
╠═══════════════╬══════════════╬══════════════╬══════════════╬═════════════╣
║ [CONCEPT-403] ║              ║ [CONCEPT-303]║              ║ [CONCEPT-03]║
║ Diffusion     ║              ║ Feature Store║              ║ Merge Sort  ║
║ Models        ║              ║              ║              ║ ✓ Done      ║
╚═══════════════╩══════════════╩══════════════╩══════════════╩═════════════╝
```

---

## Columns and Policies

### Backlog
- **Policy**: All known work items; prioritized by PO; no WIP limit
- **Ready criteria** (pull into Analysis):
  - Has clear description and acceptance criteria
  - Dependencies identified
  - Not blocked by anything

### Analysis (WIP: 3)
- **Policy**: Understanding the concept, designing the implementation approach
- **Activities**: Research algorithm, write pseudocode, define test cases
- **Exit criteria**: Implementation approach documented, complexity analyzed

### Development (WIP: 5)
- **Policy**: Writing code and unit tests
- **Activities**: Implement concept, write tests, write explanation
- **Exit criteria**: All tests pass, DoD met for this column

### Review (WIP: 3)
- **Policy**: Code review by at least 2 team members
- **Activities**: Review correctness, test coverage, documentation quality
- **Exit criteria**: 2 approvals, no blocking comments

### Done
- **Policy**: Item meets Definition of Done (DoD)

---

## WIP Limits

WIP limits prevent context-switching overload and expose bottlenecks.

| Column | WIP Limit | Reason |
|--------|-----------|--------|
| Analysis | 3 | Limits concurrent research; deep focus |
| Development | 5 | Team size = 5 developers |
| Review | 3 | Reviews pile up → reduce batch size |

**When WIP limit reached**: STOP starting new work. HELP finish in-progress items.
"Stop starting, start finishing."

---

## Metrics

### Lead Time
Time from "added to backlog" to "done".
- Target: < 5 days for P2 concepts
- Measured: per item, tracked in Jira/Linear

### Cycle Time
Time from "started" (Analysis) to "done".
- Target: < 3 days per concept
- Used to forecast delivery dates (SLAs)

### Throughput
Items completed per week.
- Current: ~8 concepts/week
- Target: 10 concepts/week

### Flow Efficiency
Active time / total lead time (eliminating wait time).
- Current: ~40% (60% wait time in queues)
- Improvement: reduce batch sizes, fix Review bottleneck

### Cumulative Flow Diagram (CFD)
Area chart showing items in each column over time.
Widening Review band → bottleneck detected → adjust WIP limits.

---

## Kanban vs Scrum Quick Reference

| Practice | Scrum | Kanban |
|----------|-------|--------|
| Cadence | Fixed sprints (1-4 weeks) | Continuous flow |
| WIP limits | Implied by sprint capacity | Explicit column limits |
| Planning | Sprint planning every sprint | On-demand, JIT |
| Retrospectives | Every sprint | Optional/on demand |
| Change | Next sprint boundary | Anytime (if WIP allows) |
| Best for | Feature development | Support, maintenance, ops |
| Roles | PO, SM, Dev Team | No required roles |
| Burn-down | Sprint burn-down chart | CFD, throughput charts |

---

## Scrumban

Hybrid: Sprint cadence + Kanban WIP limits + pull-based workflow.
Used when teams need predictability (Scrum) but also need to handle
incoming requests without waiting for next sprint (Kanban).

ConceptualWare uses Scrumban:
- 2-week sprint cadence for concept implementation
- Kanban board with WIP limits for daily work management
- On-demand pull from backlog when capacity allows
