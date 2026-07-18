--liquibase formatted sql

--changeset baseerah:015-declared-expenses
-- Declared periodic expenses (Phase 11 / GitLab backend#1) — the first user-authored financial data in the
-- product. A recurring outflow the SAMA Open-Banking feed cannot see (cash rent, family support إعالة,
-- private tuition), so for these users the feed-only picture is structurally optimistic. The row hangs off
-- clients, NOT accounts (cash has no account), and is read additively by the calculators in Step 11.3.
-- Follows the 004-rescue-events / 014-financing conventions (uuid PKs via gen_random_uuid(), numeric(14,2)
-- money, timestamptz, CHECK-constrained enum text columns):
--
--   * label       — the user's own words, Arabic-first (e.g. 'إيجار الشقة').
--   * category    — resolvable via Category.resolve(); expense-only enforcement lands in Step 11.2. Stays
--                   text (not an FK): Category is a deliberate app-level view over the data-driven feed.
--   * cadence     — MONTHLY only for now (weekly/quarterly deferred); pinned by a CHECK.
--   * day_of_month— the recurrence day (1..31); no start/end dates — `active` soft-delete suffices.
--   * active      — soft-delete so historical stress-score snapshots stay reproducible (forward-only history).
--
-- `direction` is intentionally absent: expenses only, no declared-income counterpart.

create table declared_expenses (
    id           uuid          primary key default gen_random_uuid(),
    client_id    uuid          not null references clients(id),
    label        text          not null,
    category     text          not null,
    amount       numeric(14,2) not null,
    currency     text          not null default 'SAR',
    cadence      text          not null default 'MONTHLY',
    day_of_month int           not null,
    active       boolean       not null default true,
    created_at   timestamptz   not null default now(),
    updated_at   timestamptz   not null default now(),
    constraint chk_declared_expenses_amount       check (amount > 0),
    constraint chk_declared_expenses_cadence      check (cadence = 'MONTHLY'),
    constraint chk_declared_expenses_day_of_month check (day_of_month between 1 and 31)
);

-- Step 11.3's read path is "all active declared expenses for this client".
create index idx_declared_expenses_client_active on declared_expenses (client_id, active);
