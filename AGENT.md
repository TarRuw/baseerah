# Agent Instructions — Baseerah

You are an AI coding agent working on **Baseerah**, a proactive FinTech early-warning platform (Spring
Boot backend + Flutter frontend + PostgreSQL). You operate under the **MADO methodology** — you follow
structured documentation rather than relying on conversation memory.

## Session Workflow

At the start of every session:

1. **Read this file** to understand your role and rules.
2. **Read `docs/DESIGN.md`** — the design source of truth (architecture, data model, algorithms, API, UI).
3. **Read `.mado/ORCHESTRATION.md`** — phases, global rules, dependencies.
4. **Read `.mado/STATUS.json`** to find the current step.
5. **Read the step file** referenced in `current_step.file`.
6. **Execute the work** described in the step file — only that step.
7. **Verify your work** against the step's acceptance criteria (run it, run tests).
8. **Update `.mado/STATUS.json`** — mark the step `completed`, advance `current_step` to the next `pending`
   step, and **recalculate** `stats` from the phases array (do not increment by hand).
9. **Write a handoff note** at `.mado/handoffs/step-{phase}-{step}.md` if you made decisions or hit issues.
10. **Commit** with a message referencing the step (e.g., "Complete step 2.1: StressScoreCalculator").

## Rules

- **One step per session.** Complete exactly one step, then stop.
- **Follow the step file.** Do not add features, refactor, or make changes outside the current step.
- **Do not assume.** If a step file is unclear or missing info, stop and ask rather than guessing.
- **Validate before completing.** Build passes, tests pass, acceptance criteria met — then mark done.
- **Recalculate stats, don't increment.** Derive `completed_steps` from actual step statuses.
- **Do not modify previous steps' code** unless the current step explicitly says to.
- **Never touch** `data-mocks/`, the BRD PDF, or `design/` — they are read-only inputs.

## When Things Go Wrong

- **Acceptance criteria fail** → mark the step `failed` in STATUS.json, document why in a handoff note, stop.
- **A dependency is missing/broken** → mark `blocked`, note which step should have provided it, flag the user.
- **Step file ambiguous/incomplete** → do not guess; ask the user before writing code.
- **Step too large for one session** → stop and flag it; it must be split into sub-steps.

## Project Context

- **Design spec:** `docs/DESIGN.md` — the authoritative design.
- **Orchestration:** `.mado/ORCHESTRATION.md` — phases, global rules.
- **Status:** `.mado/STATUS.json` — current state and progress.
- **Steps:** `.mado/steps/` — detailed per-step instructions.
- **Inputs (read-only):** `data-mocks/*.json`, `Baseerah_BRD_v1.1_Updated.pdf`, `design/Baseerah.dc.html`.

## Tech Stack

- **Backend:** Spring Boot 3, Java 21, Gradle (Kotlin DSL), Spring Web / Data JPA / Validation / Actuator.
- **DB:** PostgreSQL 16, Liquibase migrations, UUID PKs, `numeric(14,2)` money, `timestamptz`.
- **Frontend:** Flutter 3 / Dart 3, Riverpod state, dio HTTP, `intl` for i18n (Arabic-first, RTL).
- **Forecasting:** `ForecastEngine` interface → `HeuristicForecast` (pure Java). Sidecar swappable later.
- **GenAI:** `GenAiClient` interface → `MockGenAi` (default, deterministic, no key) / `RemoteGenAi` (opt-in).

## Project Conventions

- Layered backend: `controller` (thin) → `service` (logic) → `repository` (Spring Data). DTOs at the edge.
- Package root: `com.baseerah`. Sub-packages by feature (`stress`, `forecast`, `loan`, `genai`, `rescue`,
  `gamification`, `bank`, `common`).
- REST envelope `{ "status":"OK", "data": ... }`; global `@RestControllerAdvice` for errors.
- Flutter: feature-first folders; single `theme/baseerah_theme.dart` holds all design tokens (DESIGN.md §8);
  `l10n/` for `en`/`ar` ARB files; `api/` for the dio client + generated models.
- Tests: JUnit 5 + Spring Boot Test (backend), `flutter_test`/widget tests (frontend). Algorithms
  (score/forecast/loan/underwriting) must have unit tests.
