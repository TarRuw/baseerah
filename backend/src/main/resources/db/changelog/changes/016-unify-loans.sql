--liquibase formatted sql

--changeset baseerah:016-unify-loans
-- Phase 12 (Unified Loan Pipeline), Step 12.1. Collapse the two overlapping loan concepts into ONE
-- request-with-proposals model. Today the bank processes loans two ways: the FR-08 underwriting queue
-- (`loan_applications` — invented external applicants, approve/decline, no pricing) and the financing RFP
-- (`financing_requests` → `financing_proposals` — a consumer raises a request, banks price it, the consumer
-- accepts, the bank disburses). A rescue request is just a loan application with a special purpose, so this
-- migration evolves `financing_requests` into the single loan-request model (it now also carries a
-- purpose/origin and the per-request underwriting report) and RETIRES `loan_applications`.
--
-- Numbering: two 015-* files already exist (015-declared-expenses, 015-financing-disbursement) — both run
-- via distinct changeset ids — so the unification migration is 016.

-- 1. Purpose + origin of the request (the FR-08 applicant "purpose" folds onto the request here).
--    origin is RESCUE for every existing row (they all originated from Smart Rescue); DIRECT is reserved for
--    the optional "apply for financing" entry point (Step 12.7). Default keeps existing rows valid.
alter table financing_requests add column purpose text          null;
alter table financing_requests add column origin  text not null default 'RESCUE';

-- 2. The underwriting report, per REQUEST (risk is per-applicant, not per priced proposal). All nullable —
--    a request sits un-underwritten until a banker generates the report (Step 12.2). Mirrors the columns the
--    retired loan_applications carried; ratios are stored as percentages (e.g. 34.00 = 34%).
alter table financing_requests add column stamina_score     int           null;
alter table financing_requests add column forecast_dti      numeric(14,2) null;
alter table financing_requests add column income_stability  numeric(14,2) null;
alter table financing_requests add column default_prob_12mo numeric(14,2) null;
alter table financing_requests add column verdict           text          null;
alter table financing_requests add column risk_tier         text          null;

-- CHECK the new enum-text columns (NULL passes under SQL three-valued logic — the un-underwritten state).
alter table financing_requests add constraint chk_financing_requests_origin
    check (origin  in ('RESCUE', 'DIRECT'));
alter table financing_requests add constraint chk_financing_requests_verdict
    check (verdict in ('OK', 'WARN', 'BAD'));

-- 3. Backfill a sensible purpose for the rows that predate this column (every existing request is a
--    deficit-cover rescue).
update financing_requests set purpose = 'Cover predicted deficit' where purpose is null;

-- 4. Widen the request status domain to add DECLINED (a bank-rejected request) alongside the existing
--    lifecycle. The financing_proposals CHECK already carries DECLINED (015-financing-disbursement).
alter table financing_requests drop constraint chk_financing_requests_status;
alter table financing_requests add  constraint chk_financing_requests_status
    check (status in ('OPEN', 'ACCEPTED', 'ACTIVE', 'CANCELLED', 'DECLINED'));

-- 5. Retire the FR-08 flat approve/decline queue — superseded by the unified request-with-proposals model.
--    Nothing references loan_applications by FK, so the table (and its idx_loan_applications_* indexes) drops
--    cleanly. risk_policy stays (it still overlays the per-request underwriting verdict).
drop table loan_applications;
