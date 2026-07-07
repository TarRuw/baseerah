# Project Orchestration — Baseerah

## Overview

**Project:** Baseerah (بصيرة) — Early Warning System for Financial Health
**Description:** A proactive AI-driven FinTech platform for Saudi Arabia that predicts liquidity gaps and
cash-flow deficits 12–15 days ahead, offers rescue actions to consumers, and gives banks a predictive
credit-verification pipeline. Two shells (consumer app + bank portal) over one backend.
**Tech Stack:** Spring Boot 3 / Java 21 / Gradle · PostgreSQL 16 + Spring Data JPA + Liquibase · Flutter 3 /
Dart 3 (Riverpod, dio) · pure-Java heuristic forecasting (Python sidecar swappable later) · pluggable
GenAI (`MockGenAi` default, `RemoteGenAi` optional).
**Started:** 2026-07-02

> Full design detail lives in [`docs/DESIGN.md`](../docs/DESIGN.md). Read it before any step.

## Goals

**Primary goal — a fully functional system on a single local machine.** Success = a fresh checkout runs
end-to-end locally (`docker-compose up` → `./gradlew bootRun` → `flutter run`) with all 5 seeded personas
and every feature working. Preparing other/hosted environments (deploy, CI/CD, orchestration, prod
hardening) is **explicitly out of scope for now** — see DESIGN.md §10. Do not add steps for it.

1. A running Spring Boot API that seeds the 5 `data-mocks/` clients into Postgres and serves every
   endpoint in DESIGN.md §6, with analytics responses under 2.5 s — all on localhost.
2. A Flutter app faithfully reproducing `design/Baseerah.dc.html` — all 7 screens, RTL/Arabic + EN,
   dark-green/gold theme — driven entirely by the local API.
3. Full FR coverage: Stress Score (FR-01/02), GenAI + Loan Sim (FR-03/04/05), Smart Rescue (FR-06/07),
   B2B credit verification (FR-08), gamified saving (FR-09/10), plus NFR compliance/perf — verified locally.

## Phases

### Phase 0: Foundations
**Purpose:** Repo layout, Spring Boot + Postgres + Liquibase skeleton, Flutter skeleton with theme/i18n/RTL,
shared API envelope & error handling. **Steps:** 4 — see `steps/step-00-*.md`

### Phase 1: Domain & Data
**Purpose:** Schema, JPA entities/repositories, seed all 5 mock clients, core client/account/transaction
DTOs and endpoints. **Steps:** 4 — see `steps/step-01-*.md`

### Phase 2: Financial Stress Score (FR-01/02)
**Purpose:** StressScoreCalculator, score endpoint, animated Home/Radar screen. **Steps:** 3 — see `steps/step-02-*.md`

### Phase 3: Forecast & Loan Simulation (FR-03/04/05)
**Purpose:** ForecastEngine + HeuristicForecast, LoanCalculator, GenAiClient (mock) + chat, Simulate
screen. **Steps:** 5 — see `steps/step-03-*.md`

### Phase 4: Smart Rescue & Cross-sell (FR-06/07)
**Purpose:** Deficit alert, RescueService bridge options, Rescue screen. **Steps:** 3 — see `steps/step-04-*.md`

### Phase 5: Gamified Micro-Saving (FR-09/10)
**Purpose:** ChallengeService, RewardsService (Akhtar Points, risk tiers), Goals screen. **Steps:** 3 — see `steps/step-05-*.md`

### Phase 6: Bank Portal (FR-08)
**Purpose:** UnderwritingService, applicant pipeline, portfolio monitoring, settings/risk policy screens.
**Steps:** 4 — see `steps/step-06-*.md`

### Phase 7: Compliance, GenAI & Polish (NFR)
**Purpose:** SAMA tokenization + NDMO flags, RemoteGenAi adapter, performance hardening + integration
tests, demo script. **Steps:** 4 — see `steps/step-07-*.md`

### Phase 8: QA Remediation
**Purpose:** Fix the issues found in the live end-to-end review (see `docs/QA_E2E_FINDINGS.md`): localize
server-provided content strings by `Accept-Language`, add a responsive max-width/phone-frame for wide web,
polish the consumer shell and bank portal, and correct/verify docs on a release build. **Steps:** 5 — see
`steps/step-08-*.md`

## Global Rules

- **All schema changes go through Liquibase.** No `ddl-auto` in prod profiles (`validate` only).
  **Changelog convention:** a master changelog at `backend/src/main/resources/db/changelog/db.changelog-master.yaml`
  uses `includeAll` over `db/changelog/changes/`; each change is a **Liquibase formatted-SQL** file named
  `NNN-kebab-name.sql` (zero-padded, e.g. `001-core-schema.sql`) beginning with `--liquibase formatted sql`
  and one `--changeset baseerah:NNN-kebab-name`. Files load in filename order — no master edit needed to add
  one. Changesets are immutable once applied (Liquibase checksums them); fix mistakes with a new changeset.
  Liquibase tracks state in `databasechangelog` / `databasechangeloglock`.
- **All primary keys are UUID.** Money is `numeric(14,2)`; timestamps are `timestamptz`.
- **Business logic lives in services, never controllers.** Entities never leave the service layer — map
  to DTOs. Controllers are thin.
- **Everything is computed from seeded transactions,** not hardcoded. No magic numbers copied from the
  prototype JS into production code (they were demo stand-ins).
- **The two swappable engines stay behind their interfaces:** `ForecastEngine`, `GenAiClient`. Never call
  a concrete implementation directly from a controller.
- **Flutter design tokens live in one theme file.** Both shells consume it. RTL-correct for Arabic.
- **API envelope:** `{ "status":"OK", "data": ... }`; errors `{ "status":"ERROR", "error":{...} }`.
- **Follow existing patterns; document any new architectural decision** in the step's handoff note.
- **Do not modify the `data-mocks/` files, the BRD, or the imported `design/` files.**

## Phase Dependencies

- Phase 1 requires Phase 0 (skeleton + DB must exist).
- Phase 2 requires Phase 1 (entities + seeded data before scoring).
- Phases 3, 4, 5 each require Phase 2 (they build on score + seeded telemetry); they are otherwise
  independent of each other and may be executed in any order.
- Phase 6 requires Phase 1 (telemetry) and reuses Phase 3's `ForecastEngine`.
- Phase 7 requires Phases 2–6 (hardens and polishes existing features).
- Phase 8 requires Phases 2–7 (remediates issues in already-built features); source of truth for its scope is
  `docs/QA_E2E_FINDINGS.md`. Within Phase 8, step 8.5 (verify/close-out) requires steps 8.1–8.4.
- Every UI step requires its phase's backend endpoint step to be complete first.

## Additional Context

- Prototype: `design/Baseerah.dc.html` (imported from an external design export). Structured inventory of screens,
  components, data bindings, and the full visual system is captured in `docs/DESIGN.md` §7–§8.
- Mock data: `data-mocks/*.json` — SAMA Open-Banking transaction payloads, 5 personas, ~6 months each.
  Shape and persona→demo mapping in `docs/DESIGN.md` §4 and §11.
- The BRD (`Baseerah_BRD_v1.1_Updated.pdf`) is the requirements authority; where it names a Python stack,
  DESIGN.md §2's decision record governs (Spring Boot + Flutter + Postgres).
