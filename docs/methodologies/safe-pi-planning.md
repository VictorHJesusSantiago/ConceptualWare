# Concept #23 — SAFe (Scaled Agile Framework) & PI Planning

SAFe extends Agile to large organizations with 50+ development teams working on shared products.

---

## SAFe Levels

```
┌────────────────────────────────────────────────────────────────────────┐
│  ENTERPRISE LEVEL                                                      │
│  Lean Portfolio Management, Enterprise Architecture, Value Streams     │
├────────────────────────────────────────────────────────────────────────┤
│  PORTFOLIO LEVEL                                                       │
│  Portfolio Kanban, Epics, Lean Budgeting, Strategic Themes             │
├────────────────────────────────────────────────────────────────────────┤
│  PROGRAM LEVEL (Agile Release Train — ART)                             │
│  PI Objectives, System Demo, Inspect & Adapt                           │
│  Innovation & Planning (IP) Sprint                                     │
├────────────────────────────────────────────────────────────────────────┤
│  TEAM LEVEL                                                            │
│  Scrum/Kanban Teams → 2-week iterations within 5-iteration PI          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Program Increment (PI)

A PI is a timebox (typically 10 weeks = 5 × 2-week iterations + 1 IP sprint).
All teams on the ART deliver to a shared goal during the PI.

### PI Planning (2-day event)

**Day 1 — Vision & Architecture**
- Business owner presents strategic themes
- Product Management presents vision for next PI
- System Architect presents architectural changes
- Teams meet to ask questions, identify risks

**Day 2 — Team Planning**
- Each team creates iteration plans for all 5 iterations
- Teams identify dependencies across teams (marked on the board)
- Risks identified and ROAM'd (Resolved, Owned, Accepted, Mitigated)
- Draft PI Objectives presented to stakeholders

**Outputs**:
- Program Board (cross-team dependency visualization)
- Team PI Objectives with business value scores
- ROAM'd risks
- Confidence vote (Fist-of-Five: 1=not confident → 5=very confident)

---

## ConceptualWare PI Planning Template

```
PI 3 Planning — ConceptualWare
Dates: Nov 4 – Jan 12, 2025 (10 weeks)
ART: Developer Intelligence Platform

Strategic Theme:
  "Complete 100% implementation of all 30 concept categories"

PI Objectives:
  □ [Team Alpha] Complete Category 30 (AI/ML): 50 concepts
    Business Value: 10/10
  □ [Team Beta] Complete Category 22 (Cloud): K8s, Terraform, Ansible
    Business Value: 8/10
  □ [Team Gamma] Complete Category 10 (Compiler): Full pipeline + REPL
    Business Value: 9/10
  □ [Team Delta] Complete Category 23 (Methodologies): Documentation
    Business Value: 7/10

Program Board (Cross-team Dependencies):
  Team Alpha → Team Beta: ML model serving needs K8s deployment manifests
  Team Beta  → Team Gamma: Compiler REPL needs container image
  Team Gamma → Team Alpha: REPL execution needs rate limiting from gateway

Iteration Plan (Team Alpha):
  IT1 (Nov 4-17):  Linear/Logistic Regression, KNN, NaiveBayes
  IT2 (Nov 18-Dec1):Decision Tree, Random Forest, K-Means, PCA
  IT3 (Dec 2-15):  Neural Network (MLP + backprop), LSTM, GRU
  IT4 (Dec 16-29): Transformer attention, LLMs, RAG, embeddings
  IT5 (Dec 30-Jan 12): MLOps, Feature Store, model evaluation, XGBoost
  IP Sprint:        Hardening, performance testing, documentation review

Risks (ROAM'd):
  R1: LSTM implementation complexity higher than estimated
    → Owned by Senior Engineer Dave; split into smaller tasks
  R2: Transformer attention memory-intensive for large sequences
    → Mitigated: limit demo to seq_len ≤ 64
  R3: ML tests run slowly (gradient descent iterations)
    → Resolved: use smaller datasets in tests; mark with @Tag("ml-slow")
```

---

## LeSS (Large-Scale Scrum)

Alternative to SAFe for 2-8 teams:
- Just regular Scrum: same Product Backlog, one Sprint for all teams
- ONE Sprint Review, ONE Retrospective (+ team-level retros)
- Sprint Planning Part 1: all teams together
- Sprint Planning Part 2: each team separately
- Feature teams (not component teams) — end-to-end features per team

**LeSS vs SAFe**:
- LeSS: less process overhead, better for greenfield products, harder to sell to leadership
- SAFe: prescriptive, comprehensive, easier executive adoption, more ceremonies

---

## XP (Extreme Programming) Technical Practices

XP is the technical engine inside Agile:

### Test-Driven Development (TDD)

Red → Green → Refactor cycle:
```java
// 1. RED: write failing test
@Test void computesBinarySearch() {
    assertThat(BinarySearch.search(new int[]{1,3,5,7}, 5)).isEqualTo(2);
}

// 2. GREEN: write minimum code to pass
public static int search(int[] arr, int target) {
    int lo = 0, hi = arr.length - 1;
    while (lo <= hi) {
        int mid = lo + (hi - lo) / 2;
        if (arr[mid] == target) return mid;
        if (arr[mid] < target)  lo = mid + 1;
        else                    hi = mid - 1;
    }
    return -1;
}

// 3. REFACTOR: improve without changing behavior
```

### Continuous Integration

Commit frequently (multiple times per day) to main branch.
CI server (GitHub Actions) runs: compile → test → lint → build → publish.
Broken build = top priority fix. Never go home with red CI.

### Simple Design

YAGNI: "You Aren't Gonna Need It"
- Don't design for future requirements that aren't here yet
- Three rules: tests pass, no duplication, minimal code

### Pair Programming

Two developers at one workstation:
- Driver: writes code
- Navigator: reviews, thinks ahead, catches bugs

Benefits: knowledge sharing, fewer bugs, collective ownership.
Not always practical — mob programming (whole team) as alternative.
