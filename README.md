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

On first boot the backend runs Liquibase migrations and **seeds the 5 mock personas** from
`data-mocks/*.json` into Postgres (idempotent — re-running `bootRun` is a cheap no-op).
Confirm with `curl http://localhost:8080/api/v1/clients` → 5 clients.

## Running the app

Requires the Flutter 3.x / Dart 3 SDK (see Prerequisites). With the backend running:

```bash
cd frontend
flutter pub get   # also generates localizations (l10n)
flutter run       # pick a device: chrome (web), an emulator, or desktop
```

The app is **Arabic-first (RTL)** by default; the toolbar language toggle flips to English (LTR).
It talks to the backend at `http://localhost:8080/api/v1`. On an Android emulator, override the host:

```bash
flutter run --dart-define=BASEERAH_API_BASE_URL=http://10.0.2.2:8080/api/v1
```

### Choosing which persona (account) the consumer app runs as

The consumer app represents **one seeded account** (there is no in-app persona switcher — the demo
model is one account per persona). A plain `flutter run` runs as the first seeded client
(`client_001_family`). To run as a different persona, pass its `externalId` at launch:

```bash
flutter run --dart-define=BASEERAH_CLIENT=client_003_freelancer
```

Valid ids come from `GET /api/v1/clients`: `client_001_family`, `client_002_tech_bro`,
`client_003_freelancer`, `client_004_student`, `client_005_vip`. An unknown id fails loud with the
list of valid ids. The **bank portal** (reached via the Consumer/Bank toolbar toggle) has its own
in-screen applicant list, so it needs no flag. See [`docs/DEMO.md`](docs/DEMO.md) for the full
persona → feature walkthrough.

## Configuration (environment variables)

Everything runs with sensible localhost defaults — **no env vars are required** for the standard
offline demo. Override as needed:

| Variable | Default | Purpose |
|---|---|---|
| `GENAI_PROVIDER` | `mock` | `mock` = deterministic, **offline, no key** (default). `remote` = real streamed AI replies (needs a key; falls back to mock if the key is blank). |
| `GENAI_API_KEY` | *(blank)* | Provider API key. Only consulted when `GENAI_PROVIDER=remote`. Blank ⇒ keyless fallback to mock, so the demo always runs offline. |
| `GENAI_MODEL` | `claude-opus-4-8` | Model id used by the remote provider. |
| `GENAI_BASE_URL` | `https://api.anthropic.com` | Remote provider base URL. |
| `GENAI_MAX_TOKENS` / `GENAI_VERSION` | `1024` / `2023-06-01` | Remote request tuning. |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/baseerah` | DB connection (matches `backend/docker-compose.yml`). |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | `baseerah` / `baseerah` | DB credentials. |

Example — enable a real streamed AI reply:

```bash
GENAI_PROVIDER=remote GENAI_API_KEY=sk-... ./gradlew bootRun
```

Leave `GENAI_PROVIDER` unset (or `mock`) for the standard offline demo.

## Demo

For a presenter-ready, persona-by-persona walkthrough of both shells (consumer app + bank portal),
including the language/RTL toggle and the compliance Settings screen, see
**[`docs/DEMO.md`](docs/DEMO.md)**. The authoritative design spec is [`docs/DESIGN.md`](docs/DESIGN.md).
