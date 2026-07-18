# Baseerah — Production Deployment Copy

A production-oriented copy of the Baseerah backend and frontend. It contains only deployable
code and configuration: **no hardcoded secrets** (all externalized to environment variables) and
**no demo/user data seeding** (only necessary system reference data is seeded).

This copy was derived from the development tree with the changes listed below. The original repo is
unchanged.

---

## What was removed

### Secrets (now environment-variable references)

| Where | Before | After |
|---|---|---|
| `backend/src/main/resources/application.yml` | hardcoded `spring.datasource` url/username/password (`baseerah`/`baseerah`) | `${SPRING_DATASOURCE_URL}` / `${SPRING_DATASOURCE_USERNAME}` / `${SPRING_DATASOURCE_PASSWORD}` (no defaults — fail fast) |
| `application.yml` + `AuthProperties.java` | dev-only `jwt-secret` and `mock-otp` defaults baked in | `${BASEERAH_AUTH_JWT_SECRET}` / `${BASEERAH_AUTH_MOCK_OTP}`, in-code defaults removed |
| `backend/deployment.yaml` | plaintext DB password + JWT secret in `env:` | `secretKeyRef` into the `baseerah-backend-secrets` Secret |
| `backend/.gitlab-ci.yml`, `frontend/.gitlab-ci.yml` | hardcoded API URL `http://49.12.74.205:...` | `${BASEERAH_API_BASE_URL}` CI/CD variable |
| `frontend/deployment.yaml` | hardcoded demo API IP | build-time build-arg (set in CI) |
| `frontend/lib/api/api_client.dart` | default `baseUrl` = demo server IP | no default; must be supplied via `--dart-define=BASEERAH_API_BASE_URL` |
| `frontend` login screen | "Demo: enter the test code…" OTP hint | removed |

The GenAI remote API key was already env-driven (`${GENAI_API_KEY:}`) and is unchanged.

### User / demo data seeding (deleted)

- `config/seed/AppUserSeeder.java` — fabricated 6 demo login identities (5 personas + a bank officer).
- `config/seed/StressedPersonaSeeder.java` — fabricated a demo persona with account + transactions.
- `config/seed/LoanRequestSeeder.java` — fabricated demo loan requests.
- `config/seed/dto/*` — mock-JSON parsing records, only used by the seeders above.
- `config/SeedProperties.java` + the `baseerah.seed.*` config block.
- Liquibase changesets `011-seed-mock-data.sql` (5 personas, 7 accounts, 1658 transactions) and
  `012-second-accounts.sql` (savings accounts + transactions).
- The `db-seed/` generator scripts + `requirements.txt`, and the `src/test/` tree (test scaffolding
  that exercises the removed demo data; not a deployment artifact — the Dockerfile already builds with
  `-x test`).

### System function data seeding (kept — necessary)

- `013-banks.sql` — the bank registry (codes, names, logos) the accounts list joins against.
- `006-bank.sql` `risk_policy` singleton — the bank-wide underwriting policy row.
- `config/seed/ChallengeSeeder.java` — derives each **real** client's gamification challenges from
  their own transactions at boot. Idempotent; a no-op when there are no clients. No fabricated data.
- i18n message bundles (`messages*.properties`) and category/tier definitions (domain code).

---

## Required environment variables (backend)

Set these in the deployment environment (see `backend/deployment.yaml` for the Kubernetes wiring):

| Variable | Required | Notes |
|---|---|---|
| `SPRING_DATASOURCE_URL` | yes | e.g. `jdbc:postgresql://host:5432/baseerah_db` |
| `SPRING_DATASOURCE_USERNAME` | yes | from Secret `baseerah-backend-secrets/datasource-username` |
| `SPRING_DATASOURCE_PASSWORD` | yes | from Secret `baseerah-backend-secrets/datasource-password` |
| `BASEERAH_AUTH_JWT_SECRET` | yes | HS256 key, ≥ 32 bytes |
| `BASEERAH_AUTH_MOCK_OTP` | yes | see OTP note below |
| `BASEERAH_CORS_ALLOWED_ORIGIN_PATTERNS` | yes | comma-separated frontend origin(s) |
| `SPRING_PROFILES_ACTIVE` | no | defaults to `prod` |
| `BASEERAH_AUTH_JWT_TTL` | no | defaults to `PT12H` |
| `BASEERAH_AS_OF_DATE` | no | defaults to `system` (live wall clock) |
| `GENAI_PROVIDER` / `GENAI_API_KEY` / … | no | defaults to the offline `mock` provider |

Create the Secret before deploying, e.g.:

```sh
kubectl create secret generic baseerah-backend-secrets \
  --from-literal=datasource-username=... \
  --from-literal=datasource-password=... \
  --from-literal=jwt-secret=... \
  --from-literal=auth-mock-otp=...
```

## Required build inputs (frontend)

The API base URL is compiled into the web bundle at build time:

```sh
flutter build web --release --dart-define=BASEERAH_API_BASE_URL=https://api.example.com/api/v1
# or via Docker:
docker build --build-arg BASEERAH_API_BASE_URL=https://api.example.com/api/v1 .
```

---

## Known limitations (not secrets/seeding — flagged for real production)

- **Authentication is still a mock.** `OtpService` has no real SMS/OTP gateway; it accepts a single
  configured code and requires a provisioned user. Replace it with a real provider adapter, and add a
  real user-provisioning flow (this copy seeds **no** login identities), before going live.
- **Android release** is signed with the debug key (`signingConfig = signingConfigs.debug`). Configure
  a real release signing config for store distribution.
- Placeholders marked `REPLACE_WITH_...` in the deployment manifests must be filled in (or left to CI).
