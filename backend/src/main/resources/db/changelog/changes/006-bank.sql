--liquibase formatted sql

--changeset baseerah:006-bank
-- Bank Portal / B2B credit verification (DESIGN.md §4.2, §5.5, FR-08). Two tables back the bank side:
-- `loan_applications` is the underwriting queue — one row per applicant, holding both the request
-- (name/initials/purpose/amount, optional client_ref → real telemetry) and the predictive report the
-- UnderwritingService stamps back onto it (stamina, forecast DTI, income stability, 12-mo default
-- probability, verdict, risk tier) plus the eventual human decision. `risk_policy` is a bank-wide settings
-- singleton feeding the Step 6.2 risk-policy endpoints and the Step 6.4 Settings screen. Type/PK/FK
-- conventions match 001-core-schema / 004-rescue-events (uuid PKs via gen_random_uuid(), numeric(14,2)
-- money/ratios, timestamptz).
--
-- Report fields are nullable: a freshly-seeded synthetic applicant may sit in the queue un-underwritten
-- (verdict NULL) until a report is generated; `decision` stays NULL until a banker approves/declines in
-- Step 6.2. verdict/decision are stored as text constrained to their enum domains (NULL passes the CHECK
-- under SQL three-valued logic — leaving room for the un-scored / undecided states). `seed_key` is a stable
-- idempotency handle: the BankApplicantSeeder writes one per seeded applicant and skips any key already
-- present, so reboots never duplicate the queue (mirrors the challenges (client_id, code) key in 005).

create table loan_applications (
    id                uuid          primary key default gen_random_uuid(),
    seed_key          text          null,
    applicant_name    text          not null,
    initials          text          null,
    purpose           text          null,
    amount            numeric(14,2) not null,
    client_ref        uuid          null references clients(id),
    stamina_score     int           null,
    forecast_dti      numeric(14,2) null,
    income_stability  numeric(14,2) null,
    default_prob_12mo numeric(14,2) null,
    verdict           text          null,
    risk_tier         text          null,
    decision          text          null,
    created_at        timestamptz   not null default now(),
    constraint uq_loan_applications_seed_key unique (seed_key),
    constraint chk_loan_applications_verdict  check (verdict  in ('OK', 'WARN', 'BAD')),
    constraint chk_loan_applications_decision check (decision in ('APPROVE', 'DECLINE'))
);

create index idx_loan_applications_client_ref on loan_applications (client_ref);
create index idx_loan_applications_verdict    on loan_applications (verdict);

-- Bank-wide risk policy, enforced as a singleton: the `singleton` guard column is fixed true and made
-- unique, so at most one row can ever exist (a second insert collides on uq_risk_policy_singleton, and the
-- CHECK forbids sneaking in a distinct value). The settings screen therefore always reads exactly one live
-- policy. The column is intentionally unmapped by the JPA entity — under ddl-auto=validate, extra DB columns
-- are allowed; it exists purely as the single-row guard.
create table risk_policy (
    id                     uuid        primary key default gen_random_uuid(),
    singleton              boolean     not null default true,
    stamina_floor          int         not null,
    auto_decline_threshold int         not null,
    ndmo_residency         boolean     not null,
    tokenization           boolean     not null,
    sama_last_sync         timestamptz null,
    constraint uq_risk_policy_singleton  unique (singleton),
    constraint chk_risk_policy_singleton check (singleton is true)
);

-- The one default policy row. stamina_floor / auto_decline_threshold echo the §5.5 verdict guardrails
-- (a BAD verdict is stamina <= 48 OR DTI >= 71%), so the policy defaults sit at the edge of the auto-decline
-- band; NDMO residency and SAMA tokenization default on (Phase-7 compliance posture), with an initial sync
-- stamp so the Settings screen shows a live "last synced" value from first boot.
insert into risk_policy (stamina_floor, auto_decline_threshold, ndmo_residency, tokenization, sama_last_sync)
values (50, 71, true, true, now());
