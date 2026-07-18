--liquibase formatted sql

--changeset baseerah:007-tokenize-accounts
-- Phase 7 / Step 7.1 — guarantee SAMA account tokenization end-to-end (DESIGN.md §9). The 001 schema left
-- `accounts.tokenized_account_id` nullable and the seed data populates it, but the compliance
-- posture demands the token exist for *every* account structurally, not just by seed convention: the bank
-- side must never have to fall back to a raw account id because a token was missing.
--
-- Backfill first (defensive — a no-op when the token is already present), then make the column NOT NULL so a
-- tokenless account can never be inserted. The backfill token is byte-for-byte identical to the seed
-- tokenization (011-seed-mock-data.sql): SHA-256 of the raw account id, hex-encoded, first 24 hex chars, upper-cased,
-- 'TKN-' prefixed. pgcrypto (digest/encode) is already enabled by 001-core-schema. coalesce(external_id,
-- id::text) keeps the token deterministic even for an account with no external id.
update accounts
   set tokenized_account_id =
       'TKN-' || upper(substring(encode(digest(coalesce(external_id, id::text), 'sha256'), 'hex') from 1 for 24))
 where tokenized_account_id is null;

alter table accounts alter column tokenized_account_id set not null;
