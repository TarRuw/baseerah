--liquibase formatted sql

--changeset baseerah:014-financing
-- Bank financing request-for-proposal (RFP) flow for Smart Rescue. When a consumer faces a predicted
-- deficit they raise a financing request and fan it out to the banks they already hold accounts with; each
-- targeted bank replies (in the Bank Portal) with its own profit rate + term. Two tables model this,
-- following the 006-bank / 004-rescue-events conventions (uuid PKs via gen_random_uuid(), numeric(14,2)
-- money/ratios, timestamptz, CHECK-constrained enum text columns):
--
--   * financing_requests  — one row per consumer RFP: the shortfall `amount` to cover, the `deficit_in_days`
--     lead time captured at creation (for the audited rescue_events row on choose), and a lifecycle status.
--   * financing_proposals — one row per targeted bank per request: the free-text `bank_name` (there is no
--     bank entity — a bank is only an accounts.bank_name string), a lifecycle status, and the reply the bank
--     operator types in (`rate` %, `term_months`, offered `amount`). rate/term/replied_at stay null while
--     PENDING; `chosen` flips true on the one proposal the consumer accepts.

create table financing_requests (
    id              uuid          primary key default gen_random_uuid(),
    client_id       uuid          not null references clients(id),
    amount          numeric(14,2) not null,
    deficit_in_days int           not null,
    status          text          not null default 'OPEN',
    created_at      timestamptz   not null default now(),
    constraint chk_financing_requests_status check (status in ('OPEN', 'CHOSEN', 'CANCELLED'))
);

create index idx_financing_requests_client_id on financing_requests (client_id);

create table financing_proposals (
    id           uuid          primary key default gen_random_uuid(),
    request_id   uuid          not null references financing_requests(id),
    bank_name    text          not null,
    status       text          not null default 'PENDING',
    rate         numeric(14,2) null,
    term_months  int           null,
    amount       numeric(14,2) not null,
    chosen       boolean       not null default false,
    replied_at   timestamptz   null,
    created_at   timestamptz   not null default now(),
    constraint chk_financing_proposals_status check (status in ('PENDING', 'REPLIED', 'DECLINED'))
);

create index idx_financing_proposals_request_id on financing_proposals (request_id);
create index idx_financing_proposals_status     on financing_proposals (status);
