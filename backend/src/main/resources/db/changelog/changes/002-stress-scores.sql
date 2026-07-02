--liquibase formatted sql

--changeset baseerah:002-stress-scores
-- Financial Stress Score daily snapshots (DESIGN.md §4.2, §5.1). One row per client per day; the
-- snapshot writer upserts on (client_id, as_of_date). score is the 0-100 healthiness index; the three
-- sub-scores are stored on the same 0-100 scale (higher = healthier) for the radar chart in Step 2.3.
-- All FK/PK/type conventions match 001-core-schema (uuid PKs, numeric(14,2) money, timestamptz).

create table stress_scores (
    id                 uuid          primary key default gen_random_uuid(),
    client_id          uuid          not null references clients(id),
    as_of_date         date          not null,
    score              int           not null,
    zone               text          not null,
    spending_velocity  numeric(14,2) not null,
    income_consistency numeric(14,2) not null,
    liability_ratio    numeric(14,2) not null,
    created_at         timestamptz   not null default now(),
    constraint uq_stress_scores_client_as_of unique (client_id, as_of_date)
);

create index idx_stress_scores_client_as_of on stress_scores (client_id, as_of_date desc);
