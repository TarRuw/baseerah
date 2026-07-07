--liquibase formatted sql

--changeset baseerah:008-perf-indexes
-- Phase 7 / Step 7.3 — performance hardening (DESIGN.md §9, analytics < 2.5s). Profiling the hot analytics
-- paths (score, forecast, loan, rescue, bank report/portfolio) against all 5 seeded personas found their
-- query patterns already covered by indexes created in 001–006:
--   * transactions(account_id, booking_date)  [001] — the trailing-window scan behind score/forecast/rescue
--                                                       /underwriting (the dominant read).
--   * accounts(client_id)                     [001] — account lookups incl. the batched compliance tokens.
--   * stress_scores(client_id, as_of_date)    [002] — latest-snapshot lookup for the score endpoint.
--   * challenges(client_id)                   [005] — the challenge list.
--   * rewards_ledger(client_id)               [005] — the points-balance aggregate.
--   * loan_applications(client_ref | verdict) [006] — report lookup and the portfolio's underwritten filter.
--
-- The one analytics query lacking index support was the underwriting queue's ordering scan
-- (`select … from loan_applications order by created_at asc`, BankService.queue). Add a matching index so
-- the queue is served from an ordered index scan rather than a sort as the applicant book grows. Small on the
-- seeded data, but it closes the last uncovered read pattern for the < 2.5s budget.
create index idx_loan_applications_created_at on loan_applications (created_at);
