# Baseerah (بصيرة) — Early Warning Financial Radar

Baseerah is a proactive, AI-driven FinTech platform for the Saudi market. Unlike reactive apps that
merely record past spending, Baseerah predicts liquidity gaps, cash-flow deficits, and potential
defaults **12–15 days before they happen** and offers concrete rescue actions. It presents two faces
over a single backend: a **consumer app** (Financial Stress Score, AI scenario/loan simulation, Smart
Rescue Mode, gamified saving) and a **bank portal** (B2B credit-verification pipeline, portfolio
monitoring, risk-policy settings). Strategic anchors align with Vision 2030 — lifting household saving,
cutting bank NPLs through pre-emptive affordability checks, and maximising SAMA Open Banking utilisation.

> **Design source of truth:** [`docs/DESIGN.md`](docs/DESIGN.md). Read it before working on any step.

## Repository structure

```
baseerah/
├── backend/        Spring Boot 3 API (Java 21, Gradle Kotlin DSL) — package root com.baseerah
├── frontend/       Flutter 3 app (Dart 3) — consumer + bank shells, RTL/Arabic-first
├── docs/           DESIGN.md, the authoritative design specification
├── data-mocks/     SAMA Open-Banking mock datasets (5 personas) — read-only input
├── design/         Imported prototype (Baseerah.dc.html) — read-only input
└── .mado/          MADO orchestration: phases, per-step files, and STATUS.json
```

## Tech stack

Spring Boot 3 / Java 21 / Gradle (Kotlin DSL) · PostgreSQL 16 + Spring Data JPA + Liquibase ·
Flutter 3 / Dart 3 with **Riverpod** state management + **dio** HTTP client · pure-Java heuristic
forecasting (Python sidecar swappable later behind the same interface) · pluggable GenAI
(`MockGenAi` default, `RemoteGenAi` optional). See [`docs/DESIGN.md`](docs/DESIGN.md) §2 for the full
table and the decision record.

### State-management decision

**Riverpod is the chosen Flutter state-management approach** and is locked for the whole project.
Bloc was the considered alternative; per DESIGN.md §2 the team picked one in Phase 0 and keeps it.

## Prerequisites

- **JDK 21**
- **Docker** + **docker-compose**
- **Flutter 3.x SDK** (includes **Dart 3**)

## Running the backend

Both commands run from `backend/` (that's where `docker-compose.yml` and the Gradle wrapper live):

```bash
cd backend
docker compose up -d          # start PostgreSQL 16 for local dev
./gradlew bootRun             # start the API on http://localhost:8080
```

Verify it's up: `curl http://localhost:8080/actuator/health` → `{"status":"UP"}`.

## Running the app

The exact commands are finalized in Step 0.3, which populates `frontend/`:

```bash
cd frontend
flutter pub get
flutter run
```
