# Baseerah — Test/Demo Data Mocks

The demo/persona seed data that populates a Baseerah database for **testing and demos**. This is the
same category of data that was deliberately **removed from the production backend's boot-time seeding**
(see `../PRODUCTION.md`) — it lives here so a test or staging environment can be populated on demand,
without shipping it inside the production image.

> **Do not load this into a real production database.** These are fabricated personas with fixed,
> publicly-known demo login mobiles and a fixed mock OTP.

## Provenance

Pulled from the live in-cluster database on the Baseerah demo cluster:

- Namespace/pod: `default/baseerah-postgres-*`, database `baseerah_db`
- Extracted with `pg_dump --data-only --column-inserts --disable-triggers` (SQL) and
  `json_agg(row_to_json(...))` per table (JSON).
- Snapshot taken 2026-07-18. Transaction window: **2025-08-01 → 2026-07-16** (1685 rows).

Only demo/persona data is included. System reference data already seeded by the Liquibase migrations
(`banks`, `risk_policy`) and Liquibase bookkeeping (`databasechangelog*`) are **excluded** so this bundle
layers cleanly on top of a migrated schema.

## Contents

| File | Rows | Notes |
|---|---|---|
| `seed-data.sql` | — | Single re-runnable, FK-safe, data-only INSERT script for all tables below. |
| `json/clients.json` | 6 | 5 SAMA personas + 1 gig-worker distress persona (`client_006_gig`). |
| `json/accounts.json` | 8 | Current + savings accounts across the personas. |
| `json/transactions.json` | 1685 | Full transaction history feeding the scoring engines. |
| `json/app_users.json` | 6 | Demo login identities (see table below). No password hashes — auth is mock OTP. |
| `json/stress_scores.json` | 5 | Computed Financial Stress snapshots. |
| `json/challenges.json` | 15 | Per-client gamification challenges. |
| `json/challenge_progress.json` | 15 | Progress rows for the above. |
| `json/rewards_ledger.json` | 3 | Claimed rewards. |
| `json/rescue_events.json` | 7 | Smart Rescue events. |
| `json/declared_expenses.json` | 2 | User-declared periodic expenses. |
| `json/financing_requests.json` | 10 | Unified loan pipeline requests across all stages. |
| `json/financing_proposals.json` | 11 | Bank proposals on the requests. |

The `json/` files are readable fixtures (UTF-8, Arabic preserved); `seed-data.sql` is the loadable seed.

## Demo login identities

Every identity signs in with the **fixed mock OTP** (`BASEERAH_AUTH_MOCK_OTP`, e.g. `123456` in dev).

| Role | Mobile | Name | Persona |
|---|---|---|---|
| CONSUMER | +966501000001 | Abdullah Al-Qahtani | `client_001_family` — stable salaried (family) |
| CONSUMER | +966501000002 | Faisal Al-Otaibi | `client_002_tech_bro` — single young spender |
| CONSUMER | +966501000003 | Noura Al-Harbi | `client_003_freelancer` — independent entrepreneur |
| CONSUMER | +966501000004 | Sara Al-Dosari | `client_004_student` — university student |
| CONSUMER | +966501000005 | Khalid Al-Rashid | `client_005_vip` — high-net-worth |
| BANK | +966509000001 | Mona Al-Zahrani | bank officer (no linked persona) |

## Loading into a test database

The schema and system reference data come from the backend's Liquibase migrations; this bundle adds the
persona data on top:

```sh
# 1. Bring up the schema + system data (run the backend once, or apply Liquibase).
# 2. Load the persona/demo data:
psql "$DATABASE_URL" -f seed-data.sql

# e.g. against the in-cluster DB:
kubectl exec -i <postgres-pod> -- psql -U postgres -d baseerah_db < seed-data.sql
```

`seed-data.sql` uses `--disable-triggers` (requires a superuser/owner connection) so foreign-key order is
not a concern. It is data-only and **not** idempotent — load it into a database whose persona tables are
empty, or truncate those tables first.

## Regenerating

Re-run the extraction against the live DB (uses the Baseerah kubeconfig):

```sh
export KUBECONFIG=~/.kube/baseerah.yaml
TABLES="clients accounts transactions app_users stress_scores challenges challenge_progress \
        rewards_ledger rescue_events declared_expenses financing_requests financing_proposals"
TFLAGS=$(printf ' -t public.%s' $TABLES)
kubectl exec <postgres-pod> -- bash -c \
  "PGPASSWORD=\$PGPASSWORD pg_dump -U postgres -d baseerah_db --data-only --column-inserts \
   --no-owner --no-privileges --disable-triggers $TFLAGS" > seed-data.sql
```
