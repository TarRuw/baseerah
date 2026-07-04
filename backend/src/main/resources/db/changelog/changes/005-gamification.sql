--liquibase formatted sql

--changeset baseerah:005-gamification
-- Gamified micro-saving (DESIGN.md §4.2, §5.6, FR-09/10). Three tables back the ChallengeService /
-- RewardsService: `challenges` are the anomaly-tailored goals generated per client, `challenge_progress`
-- tracks one client's advance toward each goal (one row per challenge), and `rewards_ledger` is the
-- append-only Akhtar-Points journal whose running sum is the client's balance. Type/PK/FK conventions match
-- 001-core-schema / 004-rescue-events (uuid PKs via gen_random_uuid(), numeric(14,2) "challenge units",
-- timestamptz). Two idempotency keys make boot re-seeding a no-op: challenges are unique per
-- (client_id, code) so re-generation upserts instead of duplicating, and challenge_progress is unique per
-- challenge_id so a challenge has exactly one progress row.
--
-- Unlike the stress_scores / rescue_events aggregates (NO ACTION FK), gamification rows have no life without
-- their client, so their FKs are ON DELETE CASCADE: removing a client removes its challenges (and,
-- transitively, their progress) and its ledger — keeping the client lifecycle self-cleaning.

create table challenges (
    id               uuid          primary key default gen_random_uuid(),
    client_id        uuid          not null references clients(id) on delete cascade,
    code             text          not null,
    title            text          not null,
    subtitle         text,
    icon             text,
    target_value     numeric(14,2) not null,
    reward_points    int           not null,
    category_trigger text,
    constraint uq_challenges_client_code unique (client_id, code)
);

create index idx_challenges_client on challenges (client_id);

create table challenge_progress (
    id            uuid          primary key default gen_random_uuid(),
    challenge_id  uuid          not null references challenges(id) on delete cascade,
    current_value numeric(14,2) not null default 0,
    pct           int           not null default 0,
    claimed       boolean       not null default false,
    claimed_at    timestamptz   null,
    constraint uq_challenge_progress_challenge unique (challenge_id),
    constraint chk_challenge_progress_pct check (pct between 0 and 100)
);

create table rewards_ledger (
    id           uuid        primary key default gen_random_uuid(),
    client_id    uuid        not null references clients(id) on delete cascade,
    points_delta int         not null,
    reason       text,
    created_at   timestamptz not null default now()
);

create index idx_rewards_ledger_client on rewards_ledger (client_id);
