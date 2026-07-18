# Baseerah — بصيرة

### The Early-Warning Financial Radar

**Baseerah sees the cash-flow crunch coming — 12 to 15 days before it hits — and tells you what to do about it.**

A proactive, AI-driven FinTech platform for the Saudi market. Where ordinary money apps look
*backward* and report what you already spent, Baseerah looks *forward*: it predicts liquidity gaps,
cash-flow deficits, and potential defaults before they happen, then hands the user a concrete plan to
avoid them.

---

## The problem

Most personal-finance and banking tools are rear-view mirrors. They categorize last month's spending
and draw a nice chart — but by the time a shortfall shows up in the statement, the damage is done: a
missed obligation, an overdraft, a late loan payment, a hit to creditworthiness.

- **Consumers** find out they're in trouble *after* it happens, with no time to react.
- **Banks** approve financing on backward-looking snapshots and absorb the cost when borrowers quietly
  drift toward default — driving up non-performing loans (NPLs).
- **Rich Open-Banking data exists**, but it's used to *describe* the past, not to *warn* about the future.

## The solution

Baseerah turns SAMA Open-Banking transaction data into a **forward-looking early-warning system**. One
backend powers two experiences:

### 📱 For consumers — see trouble before it arrives
- **Financial Stress Score** — a single, explainable health gauge computed from real cash-flow signals
  (income consistency, spending health, obligation load, liquidity buffer).
- **Deficit forecasting** — projects the balance forward and flags a shortfall **12–15 days out**, while
  there's still time to act.
- **Smart Rescue Mode** — when a gap is coming, Baseerah proposes concrete bridge actions instead of just
  raising an alarm.
- **AI scenarios & loan simulation** — "What if I take this loan / this expense?" answered against the
  user's actual finances.
- **Gamified saving** — challenges and rewards that turn small, consistent habits into measurable gains.

### 🏦 For banks — lend on where the borrower is heading, not where they've been
- **Predictive credit verification** — an underwriting pipeline that scores affordability from forward-
  looking cash-flow health, not just a static snapshot.
- **Portfolio monitoring** — track the health of disbursed facilities and surface risk early.
- **Configurable risk policy** — bank-wide thresholds and controls that drive automated screening.

---

## Why it matters — aligned with Vision 2030

- **Lift household saving** from ~6% toward 10% by making financial foresight effortless.
- **Cut bank NPLs** through pre-emptive, affordability-first lending decisions.
- **Maximize SAMA Open Banking utilization** — putting the data to work as prevention, not just reporting.

---

## Built for production

A modern, cleanly-layered stack designed to scale and to stay honest about the future:

| Layer | Technology |
|---|---|
| **Backend** | Spring Boot 3 · Java 21 · layered REST API, stateless |
| **Data** | PostgreSQL 16 · Spring Data JPA · Liquibase migrations |
| **Frontend** | Flutter 3 · Dart 3 · one codebase, two shells · **Arabic-first, RTL** |
| **Forecasting** | Deterministic Java engine behind a swappable interface (ML sidecar-ready) |
| **GenAI** | Pluggable provider — offline deterministic mock by default, real model opt-in |
| **Delivery** | Docker images · Kubernetes manifests · GitOps (GitLab CI + ArgoCD) |

**Security & operability:** phone + OTP authentication with JWT sessions, role-based access (consumer vs.
bank), all secrets injected from the environment, and a container image that carries no seeded personal
data.

---

## Repository layout

```
production/
├── backend/       Spring Boot 3 API — production configuration, secrets externalized
├── frontend/      Flutter 3 app — consumer + bank shells, build-time API URL
├── data-mocks/    Persona test/demo dataset (for staging/QA — never production)
├── PRODUCTION.md  Deployment guide: required env vars, secrets, and what was hardened
└── README.md      You are here
```

## Get started

- **Deploy it:** see **[`PRODUCTION.md`](PRODUCTION.md)** for the required environment variables,
  Kubernetes secret wiring, and the frontend build inputs.
- **Populate a test environment:** see **[`data-mocks/README.md`](data-mocks/README.md)** for the demo
  personas and how to load them.

---

<p align="center"><em>Baseerah — don't just track your money. See where it's going.</em></p>
