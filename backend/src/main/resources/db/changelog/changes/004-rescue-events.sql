--liquibase formatted sql

--changeset baseerah:004-rescue-events
-- Smart Rescue audit log (DESIGN.md §4.2, §5.4). One row is appended each time a client confirms a bridge
-- option, capturing the shortfall that triggered the rescue, its lead time, the option chosen, and the
-- before/after stress score so the recovery is auditable. Type/PK/FK conventions match 001-core-schema /
-- 002-stress-scores / 003-forecasts (uuid PKs, numeric(14,2) money, timestamptz). option_chosen is
-- nullable but, when present, constrained to the two supported bridge types — a NULL passes the CHECK
-- (SQL three-valued logic), leaving room for a logged assessment that was never confirmed.

create table rescue_events (
    id                  uuid          primary key default gen_random_uuid(),
    client_id           uuid          not null references clients(id),
    predicted_shortfall numeric(14,2) not null,
    deficit_in_days     int           not null,
    option_chosen       text          null,
    score_before        int           not null,
    score_after         int           not null,
    created_at          timestamptz   not null default now(),
    constraint chk_rescue_option_chosen check (option_chosen in ('MURABAHA', 'LIQUIDATE'))
);

create index idx_rescue_events_client on rescue_events (client_id);
