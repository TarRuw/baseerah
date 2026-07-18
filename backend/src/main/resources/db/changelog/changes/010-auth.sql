--liquibase formatted sql

--changeset baseerah:010-auth
-- Phone + OTP authentication identities (DESIGN.md §12, Phase 9). Models WHO can sign in — no passwords are
-- stored; authentication is mobile + mock-OTP (Step 9.2). One row per seeded consumer persona (linked to its
-- clients row) plus one bank officer. UUID PKs; mobiles are E.164 (+9665XXXXXXXX); timestamps timestamptz.

create table app_users (
    id              uuid        primary key default gen_random_uuid(),
    mobile          text        not null,
    display_name    text        not null,
    display_name_ar text        not null,
    role            text        not null,
    client_id       uuid        references clients(id),
    created_at      timestamptz not null default now()
);

-- One identity per mobile (login handle). NULLs are impossible here (mobile is NOT NULL).
create unique index uq_app_users_mobile on app_users (mobile);

-- Resolve a consumer user to its persona quickly; null for bank users.
create index idx_app_users_client_id on app_users (client_id);
