--liquibase formatted sql

--changeset baseerah:001-core-schema
-- Core relational schema: clients, accounts, transactions (DESIGN.md §4.2).
-- UUID PKs via gen_random_uuid() (pgcrypto, built into PG 13+); money numeric(14,2); timestamps timestamptz.

create extension if not exists pgcrypto;

create table clients (
    id           uuid        primary key default gen_random_uuid(),
    external_id  text        not null unique,
    profile_label text,
    persona      text,
    created_at   timestamptz not null default now()
);

create table accounts (
    id                   uuid          primary key default gen_random_uuid(),
    client_id            uuid          not null references clients(id),
    external_id          text,
    bank_name            text,
    display_color        text,
    currency             text,
    latest_balance       numeric(14,2),
    tokenized_account_id text
);

create table transactions (
    id                  uuid          primary key default gen_random_uuid(),
    account_id          uuid          not null references accounts(id),
    external_id         text,
    direction           text          not null,
    amount              numeric(14,2) not null,
    currency            text,
    raw_description     text,
    description_cleansed text,
    category            text,
    category_confidence numeric,
    booking_date        timestamptz   not null,
    closing_balance     numeric(14,2)
);

-- Indexes
create index idx_accounts_client_id on accounts (client_id);
create index idx_transactions_account_id_booking_date on transactions (account_id, booking_date);

-- Unique external_id supports idempotent seeding (Step 1.3 ON CONFLICT). Mock tx ids are stable & unique;
-- PostgreSQL treats NULLs as distinct so this is null-safe without a partial predicate.
create unique index uq_transactions_external_id on transactions (external_id);
