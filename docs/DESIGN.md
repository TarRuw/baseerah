# Baseerah — Design Specification (v1.0)

> Source of truth for the Baseerah build. Every MADO step file references this document.
> Derived from `Baseerah_BRD_v1.1_Updated.pdf`, the imported prototype `design/Baseerah.dc.html`,
> and the five Open-Banking mock datasets in `data-mocks/`.

---

## 1. Product summary

**Baseerah (بصيرة) — "Early Warning Financial Radar."** A proactive AI-driven FinTech platform for
the Saudi market. Unlike reactive apps that record past spending, Baseerah predicts liquidity gaps,
cash-flow deficits, and potential defaults **12–15 days before they happen**, and offers concrete
rescue actions. It has two faces sharing one backend:

- **Consumer app** — Financial Stress Score, AI scenario/loan simulation, Smart Rescue Mode, gamified saving.
- **Bank portal** — B2B credit-verification pipeline, portfolio monitoring, risk-policy settings.

**Strategic anchors (Vision 2030):** lift household saving 6% → 10%; cut bank NPLs via pre-emptive
affordability checks; maximise SAMA Open Banking utilisation.

---

## 2. Technology stack (authoritative — overrides the BRD's Python stack)

| Layer | Choice | Notes |
|---|---|---|
| Backend | **Spring Boot 3.x, Java 21** | Stateless REST, layered (Controller → Service → Repository) |
| Build | **Gradle (Kotlin DSL)** | |
| Persistence | **PostgreSQL 16** + **Spring Data JPA** + **Flyway** | Flyway for all schema migrations |
| Frontend | **Flutter 3.x (Dart 3)** | One codebase, two shells (consumer mobile + bank portal web/tablet). RTL + Arabic first |
| State mgmt | **Riverpod** | Also acceptable: Bloc — pick one in Phase 0 and keep it |
| HTTP client | **dio** | |
| Forecasting | **`ForecastEngine` interface + `HeuristicForecast` (pure Java)** | Python Prophet/XGBoost sidecar swappable later behind the same interface |
| GenAI | **`GenAiClient` interface + `MockGenAi` (default) + `RemoteGenAi`** | Provider chosen by `GENAI_PROVIDER` env; mock needs no API key |
| Container (dev) | **docker-compose** | Postgres for local dev |

**Decision record:** The BRD names FastAPI/Prophet/XGBoost/Gemini. The team chose Spring Boot + Flutter
+ Postgres. ML forecasting is reimplemented as a deterministic Java heuristic for the MVP (interface
kept clean for a later Python sidecar). GenAI is abstracted so a deterministic mock drives live demos
and a real remote adapter can be switched on with a key.

---

## 3. Architecture

```
Flutter (consumer shell + bank shell)
        │  REST/JSON, i18n headers (Accept-Language: ar|en)
        ▼
Spring Boot API  ──►  Services:
  Controllers            • StressScoreCalculator      (FR-01/02)
  DTOs / Mappers         • ForecastEngine/Heuristic    (FR-04)
  Exception handler      • LoanCalculator              (FR-05)
                         • GenAiClient (Mock|Remote)   (FR-03)
                         • RescueService               (FR-06/07)
                         • ChallengeService/Rewards     (FR-09/10)
                         • UnderwritingService          (FR-08)
        │  Spring Data JPA
        ▼
PostgreSQL  ◄── MockDataSeeder loads data-mocks/*.json once at boot
```

**Principles:** stateless controllers; business logic in services; entities never leave the service
layer (map to DTOs); one responsibility per class; interfaces for the two swappable engines.

---

## 4. Data & personas

Five seeded clients (`data-mocks/`), each ~6 months of SAMA-style transactions:

| File | client_id | Persona (ar) | Demo role |
|---|---|---|---|
| `client_001_family_6_months_data.json` | client_001_family | الموظف المستقر (أبو عائلة) | Stable salaried family — healthy/optimal score |
| `client_002_tech_bro_6_months_data.json` | client_002_tech | Tech professional, high spend | Warning zone, big data volume |
| `client_003_freelancer_6_months_data.json` | client_003_freelancer | Freelancer, irregular income | Volatile — deficit forecast, rescue demo |
| `client_004_student_6_months_data.json` | client_004_student | Student | Low balance, micro-saving demo |
| `client_005_vip_6_months_data.json` | client_005_vip | VIP / high net worth | Bank cross-sell, portfolio demo |

### 4.1 Open-Banking transaction shape (input)

```json
{ "status":"OK", "timestamp":"...", "data":{
  "type":"transactions", "client_profile":"...",
  "transactions":[{
    "transaction_id":"tx-...", "account_id":"acc-...",
    "credit_debit_indicator":"DEBIT|CREDIT",
    "amount":{"currency":"SAR","amount":337.06},
    "transaction_information":"شراء من بنده فرع الياسمين",
    "booking_date_time":"2026-06-27T00:00:00Z",
    "insights":{"description_cleansed":"...","category":"GROCERIES","category_confidence":0.99},
    "balance":{"type":"CLOSING_AVAILABLE","amount":{"currency":"SAR","amount":107629.32}}
  }]}}
```

Categories observed: `SALARY`, `GROCERIES`, `TRANSPORTATION`, and more across files. Treat the category
set as data-driven (enum-with-fallback), not a fixed list.

### 4.2 Database schema (PostgreSQL, via Flyway)

Introduced progressively per phase. Full target model:

- **clients** — `id (uuid pk)`, `external_id (text unique, e.g. client_001_family)`, `profile_label`, `persona`, `created_at`
- **accounts** — `id (uuid pk)`, `client_id (fk)`, `external_id`, `bank_name`, `display_color`, `currency`, `latest_balance (numeric)`, `tokenized_account_id`
- **transactions** — `id (uuid pk)`, `account_id (fk)`, `external_id`, `direction (DEBIT|CREDIT)`, `amount (numeric)`, `currency`, `raw_description`, `description_cleansed`, `category`, `category_confidence`, `booking_date (timestamptz)`, `closing_balance (numeric)`
- **stress_scores** — `id`, `client_id (fk)`, `as_of_date`, `score (int 0-100)`, `zone (CRITICAL|WARNING|OPTIMAL)`, `spending_velocity`, `income_consistency`, `liability_ratio`, `created_at`
- **forecasts** — `id`, `client_id (fk)`, `horizon_days`, `generated_at`, `deficit_date (nullable)`, `min_projected_balance`, `points (jsonb: [{date,balance}])`
- **loan_applications** *(bank side)* — `id`, `applicant_name`, `initials`, `purpose`, `amount`, `client_ref (nullable fk)`, `stamina_score`, `forecast_dti`, `income_stability`, `default_prob_12mo`, `verdict (OK|WARN|BAD)`, `risk_tier`, `decision (nullable APPROVE|DECLINE)`
- **challenges** — `id`, `client_id (fk)`, `code`, `title`, `subtitle`, `icon`, `target_value`, `reward_points`, `category_trigger`
- **challenge_progress** — `id`, `challenge_id (fk)`, `current_value`, `pct`, `claimed (bool)`, `claimed_at`
- **rewards_ledger** — `id`, `client_id (fk)`, `points_delta`, `reason`, `created_at`
- **risk_policy** *(bank settings, singleton)* — `id`, `stamina_floor (int)`, `auto_decline_threshold (int %)`, `ndmo_residency (bool)`, `tokenization (bool)`, `sama_last_sync`
- **rescue_events** — `id`, `client_id (fk)`, `predicted_shortfall`, `deficit_in_days`, `option_chosen (MURABAHA|LIQUIDATE|null)`, `score_before`, `score_after`, `created_at`

All PKs are UUID. Money is `numeric(14,2)`. Timestamps are `timestamptz`.

---

## 5. Core algorithms

### 5.1 Financial Stress Score (FR-01, FR-02) — `StressScoreCalculator`
Produces an integer **0–100** and a zone. Higher = healthier (matches the gauge: green=optimal).
Computed from three normalised sub-scores over a trailing window (default 90 days):

1. **Spending velocity** — burn rate vs income. `debitSum / creditSum` over the window; lower is better.
2. **Income consistency** — regularity of CREDIT/SALARY inflows (coefficient of variation of monthly income; also cadence detection). Steadier is better.
3. **Liability structure** — recurring obligation load: recurring debits (rent, instalments, subscriptions) as a share of income, plus buffer (closing balance vs monthly expense). Lower obligation share + higher buffer is better.

`score = round(100 * (w1*velocityScore + w2*consistencyScore + w3*liabilityScore))`, default weights
`w1=0.4, w2=0.3, w3=0.3`. **Zones** (match prototype thresholds): `>=70 OPTIMAL (#1D9E63)`,
`40–69 WARNING (#E5A63A/#E0574F orange band)`, `<40 CRITICAL (#E0574F)`. Store a daily snapshot in
`stress_scores`. The exact weighting and normalisation curves are a deliberate design decision left for
the implementing engineer (see Phase 2 step notes).

### 5.2 Forecast engine (FR-04) — `ForecastEngine.project(clientId, horizonDays)`
`HeuristicForecast`:
1. Detect **recurring inflows** (e.g. SALARY) and **recurring outflows** (rent, subscriptions, instalments)
   by grouping on `category` + `description_cleansed` and finding ~monthly cadence.
2. Estimate **daily discretionary burn** = mean of non-recurring debits per day over the window.
3. Project forward day-by-day from the latest `closing_balance`, applying scheduled recurring events on
   their due days and subtracting daily burn.
4. Output: list of `{date, projectedBalance}`, the **first date balance crosses zero** (`deficit_date`),
   and `min_projected_balance`. Horizons: 30-day (home chart) and 3/6/12-month (scenario shift, bank report).
Interface signature is stable so a Python Prophet/XGBoost sidecar can replace `HeuristicForecast`.

### 5.3 Loan affordability (FR-05) — `LoanCalculator` (mirror the prototype exactly)
```
r = rate/100/12
installment = P * r / (1 - (1+r)^-n)     (r==0 → P/n)
essentials & income taken from the client's telemetry (recurring debits, mean income)
surplus = income - essentials
DTI = (essentials + installment) / income
verdict: installment <= 0.50*surplus → "Comfortably affordable" (green)
         installment <= 0.85*surplus → "Strains liquidity"       (orange)
         else                        → "Not affordable"          (red)
score impact: strain = installment/surplus; proj = score - max(0,(strain-0.35)*90), clamp [9,84]
```
Prototype demo constants (family persona): essentials 14,200 / income 18,500 / surplus 4,300. In the real
build these come from the client's transactions, not constants.

### 5.4 Smart Rescue (FR-06, FR-07) — `RescueService`
When a forecast deficit is predicted, raise an alert **15 days before** the expected failure (FR-06).
Offer two bridge options (FR-07): **Murabaha** micro-finance (Sharia-compliant, pre-approved amount),
or **liquidate** safe assets from an investment fund. On confirm, compute recovered score (before/after)
and log a `rescue_events` row. Prototype recovery: 62 → 78 (liquidate) / 62 → 74 (murabaha).

### 5.5 Underwriting (FR-08) — `UnderwritingService`
For a loan applicant, generate a predictive report: **stamina score** (long-term cash-flow endurance),
**forecast DTI**, **income stability**, **12-month default probability**, and a **verdict**:
`OK (stamina>=70 & DTI<=34%)`, `WARN (mixed)`, `BAD (stamina<=48 | DTI>=71%)`. Feeds the bank pipeline
and portfolio NPL metrics.

### 5.6 Gamification (FR-09, FR-10) — `ChallengeService` / `RewardsService`
Generate challenges tailored to the client's spending anomalies (e.g. coffee/food-delivery caps,
micro-saving targets). Track progress; on completion, award **Akhtar Points** (mock loyalty), update the
`rewards_ledger` and the client's risk tier.

---

## 6. REST API surface (representative)

All responses wrapped in an envelope `{ "status":"OK", "data": ... }` mirroring the mock feed. i18n via
`Accept-Language`. Prefixes: consumer `/api/v1/...`, bank `/api/v1/bank/...`.

| Method | Path | Purpose | FR |
|---|---|---|---|
| GET | `/api/v1/clients` | List seeded personas | — |
| GET | `/api/v1/clients/{id}/accounts` | Linked accounts | Home |
| GET | `/api/v1/clients/{id}/transactions` | Transaction history | — |
| GET | `/api/v1/clients/{id}/stress-score` | Score + zone + sub-scores | FR-01/02 |
| GET | `/api/v1/clients/{id}/forecast?horizonDays=30` | Projected balances + deficit date | FR-04 |
| POST | `/api/v1/clients/{id}/loan-simulate` | `{principal,rate,term}` → installment/DTI/verdict/scoreImpact | FR-05 |
| POST | `/api/v1/clients/{id}/chat` | `{message}` → AI reply (mock/remote) | FR-03 |
| POST | `/api/v1/clients/{id}/chat/invoice` | image upload → parsed action | FR-03 |
| GET | `/api/v1/clients/{id}/rescue` | Predicted shortfall + bridge options | FR-06/07 |
| POST | `/api/v1/clients/{id}/rescue/confirm` | `{option}` → recovered score | FR-07 |
| GET | `/api/v1/clients/{id}/challenges` | Active challenges + progress | FR-09 |
| POST | `/api/v1/clients/{id}/challenges/{cid}/claim` | Claim reward | FR-10 |
| GET | `/api/v1/clients/{id}/rewards` | Points balance + tier | FR-10 |
| GET | `/api/v1/bank/applicants` | Underwriting queue | FR-08 |
| POST | `/api/v1/bank/applicants/{id}/report` | Generate predictive report | FR-08 |
| POST | `/api/v1/bank/applicants/{id}/decision` | `{decision}` approve/decline | FR-08 |
| GET | `/api/v1/bank/portfolio` | KPIs + monitoring rows | FR-08 |
| GET/PUT | `/api/v1/bank/risk-policy` | Read/update thresholds & compliance flags | NFR |

---

## 7. Flutter UI — screens (from `design/Baseerah.dc.html`)

Shared shell: top toolbar (logo "بصيرة", Consumer/Bank segmented control, EN/ع language toggle).

**Consumer (mobile phone frame, bottom nav: Home · Simulate · Rescue · Goals):**
1. **Home / Radar** — greeting + avatar, animated **Stress Score gauge** (SVG arc, 3 zones, 1000ms cubic
   ease-out marker), live-multibank badge, pulsing **liquidity deficit warning** card, 30-day forecast
   chart, income-consistency & spending-velocity stat cards, linked-accounts list.
2. **Simulate** — tabs: **Loan Affordability** (3 sliders → live installment/DTI/verdict/score impact) ·
   **Ask Baseerah AI** (chat bubbles, typing dots, suggestion chips, invoice upload).
3. **Rescue** — states: *open* (shortfall banner −SAR in N days, two selectable bridge cards, confirm) /
   *complete* (success, before→after score recovery, run-again).
4. **Goals** — gold reward-points card + risk tier, challenge cards (progress bar, Claim/Claimed/In-progress).

**Bank portal (desktop layout, left sidebar: Applications · Portfolio · Settings):**
5. **Applications** — split pane: applicant list ↔ detail (empty → *generating* spinner "Analyzing 24-month
   telemetry…" → report: verdict panel, stamina box, 3 KPI boxes, 12-mo cashflow chart, Approve/Decline).
6. **Portfolio** — 4 KPI cards (active facilities, avg stamina, NPL rate, at-risk), monitoring table with
   health scores + trend arrows + status badges.
7. **Settings** — SAMA sync status, NDMO residency toggle, tokenization toggle, stamina-floor slider,
   auto-decline-threshold slider (persist to `risk_policy`).

---

## 8. Visual design system (reproduce the prototype)

- **Fonts:** `IBM Plex Sans` (Latin) + `IBM Plex Sans Arabic`. RTL when `ar`.
- **Palette:** primary teal `#0e6b54`→`#0a3d33`; gold `#C4A24C`; alert red `#E0574F`; success green
  `#1D9E63`; warning orange `#E5A63A`; dark text `#13241F`; muted `#6E7F78`; light bg `#F6F4EE`; app dark
  bg `#0c1512`; desktop backdrop `radial-gradient(1200px 700px at 50% -10%,#243530,#161e1a)`.
- **Radius:** phone frame 52/40, cards 20–24, buttons/inputs 9–14, avatars circle.
- **Shadows:** soft `0 2px 6px rgba(0,0,0,.06)`; medium `0 8px 24px -16px rgba(10,61,51,.25)`.
- **Gradients:** teal `135deg #0e6b54→#0a3d33`; gold `135deg #C4A24C→#a8863a`; red-warning `135deg #c0433c→#8f2f2a`.
- **Animations:** `bsr-pulse` (2.4s warning), `bsr-spin` (0.8s loader), `bsr-up` (fade+slide-in),
  `bsr-blink` (1s typing dots), score animation 1000ms cubic ease-out.
- **Formatting helpers:** `fmt(n)=round(n).toLocaleString('en-US')`; `money(n)=fmt(n)+' '+('SAR'|'ر.س')`.

Design tokens must live in one Flutter theme file so the two shells stay consistent.

---

## 9. Non-functional requirements

- **Performance:** forecast/analytics endpoints respond **< 2.5 s**; GenAI **time-to-first-token < 1.0 s**
  (mock is instant; remote adapter should stream).
- **Compliance:** mimic **SAMA Open Banking** tokenization (store `tokenized_account_id`, never expose raw
  account ids to the bank side); honour **NDMO** local-data-residency flag; encryption layer note on bank
  reporting. These are *simulated* per BRD out-of-scope (no live SIMAH/SAMA sync).
- **Scalability:** stateless REST, safe for concurrent live-demo load.
- **Reliability:** GenAI mock mode guarantees the demo works offline / without keys.

---

## 10. Scope & target runtime

**Target runtime (in scope): a single local developer machine.** "Done" means the whole system runs
end-to-end locally — `docker-compose up` for PostgreSQL, `./gradlew bootRun` for the API, `flutter run`
for the app — with the 5 seeded personas and every feature working. Optimise every decision for a
frictionless local run, not for any hosted environment.

**Explicitly out of scope for now** (deferred, not designed against): deployment to any cloud/hosted
environment, CI/CD pipelines, container orchestration (k8s/Helm), reverse proxies/TLS termination,
multi-environment config promotion, secrets managers, and production hardening/observability. These come
in a later, separate scope. Do not add steps or infrastructure for them.

**Out of scope per BRD §3.2:** live SIMAH/SAMA real-time sync; direct cross-bank clearing/settlement.
Everything runs on the seeded mock datasets; SAMA/NDMO/encryption controls are *simulated* locally.

---

## 11. Persona → feature demo map (for the final walkthrough)

- **client_001_family** → healthy Home gauge (optimal), loan simulator baseline.
- **client_003_freelancer** → deficit forecast → **Smart Rescue** headline demo.
- **client_004_student** → micro-saving **Goals** + rewards.
- **client_005_vip** → **Bank portal** applicant + portfolio cross-sell.
- **client_002_tech** → warning-zone score, large-volume performance check (< 2.5 s).
