--liquibase formatted sql

--changeset baseerah:003-forecasts
-- Cash-flow forecast snapshots (DESIGN.md §4.2, §5.2). One row per generated projection for a client;
-- points holds the full day-by-day series as jsonb ([{date,balance}, ...]). deficit_date is null when the
-- balance never crosses zero over the horizon. Type/PK/FK conventions match 001-core-schema and
-- 002-stress-scores (uuid PKs, numeric(14,2) money, timestamptz).

create table forecasts (
    id                    uuid          primary key default gen_random_uuid(),
    client_id             uuid          not null references clients(id),
    horizon_days          int           not null,
    generated_at          timestamptz   not null default now(),
    deficit_date          date          null,
    min_projected_balance numeric(14,2) not null,
    points                jsonb         not null
);

create index idx_forecasts_client_generated on forecasts (client_id, generated_at desc);
