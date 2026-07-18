--liquibase formatted sql

--changeset baseerah:015-financing-disbursement
-- Closes the financing loop past acceptance (two-sided disbursement lifecycle). A consumer no longer just
-- "chooses" an offer and stops; they ACCEPT it (committing to the terms), the bank then DISBURSES it (after a
-- final affordability check), and the facility becomes ACTIVE with a repayment schedule.
--
-- New lifecycle:
--   financing_proposals.status: PENDING → REPLIED → ACCEPTED → DISBURSED   (or DECLINED at either bank stage)
--   financing_requests.status:  OPEN → ACCEPTED → ACTIVE                   (or CANCELLED)
--
-- Two new columns on the proposal capture the disbursement facts the loan engine can't re-derive:
--   disbursed_at        — when the bank funded the facility
--   first_payment_date  — the first repayment due date (next month from disbursement)
-- The monthly instalment / total / term are stable functions of amount+rate+term, so they stay derived by the
-- loan engine rather than stored.

alter table financing_proposals add column disbursed_at       timestamptz null;
alter table financing_proposals add column first_payment_date date        null;

-- Migrate the status domains. ORDER MATTERS on a populated DB: the old value ('CHOSEN') is NOT in the new
-- domain and the new value ('ACTIVE'/'DISBURSED') is NOT in the old domain, so no single CHECK admits both.
-- The only safe sequence is: DROP the old CHECK → normalise the rows in the unconstrained window → ADD the
-- new CHECK (which now passes, since no legacy value remains). Doing the UPDATE first fails against the old
-- CHECK; adding the new CHECK first fails against the legacy 'CHOSEN' row. It only slipped through on an empty
-- DB, where the UPDATEs match zero rows and the new CHECK has nothing to reject.

-- financing_requests: OPEN/CHOSEN/CANCELLED → OPEN/ACCEPTED/ACTIVE/CANCELLED
alter table financing_requests  drop constraint chk_financing_requests_status;
update financing_requests  set status = 'ACTIVE' where status = 'CHOSEN';
alter table financing_requests  add  constraint chk_financing_requests_status
    check (status in ('OPEN', 'ACCEPTED', 'ACTIVE', 'CANCELLED'));

-- financing_proposals: PENDING/REPLIED/DECLINED → PENDING/REPLIED/ACCEPTED/DISBURSED/DECLINED
alter table financing_proposals drop constraint chk_financing_proposals_status;
update financing_proposals set status = 'DISBURSED' where chosen = true and status = 'REPLIED';
alter table financing_proposals add  constraint chk_financing_proposals_status
    check (status in ('PENDING', 'REPLIED', 'ACCEPTED', 'DISBURSED', 'DECLINED'));
