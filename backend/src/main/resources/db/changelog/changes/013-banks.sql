--liquibase formatted sql

--changeset baseerah:013-banks
--preconditions onFail:MARK_RAN onError:HALT
--precondition-sql-check expectedResult:0 SELECT count(*) FROM information_schema.tables WHERE table_name = 'banks'
-- The banks an account can be held at: the stable code the API exposes, the display name the SAMA feed
-- uses, its Arabic name, the frontend asset slug for the bank's mark, and its brand colour.
--
-- Keyed by `name` rather than by a foreign key on accounts.bank_code, deliberately:
--   * 011 has already run on seeded environments, so adding a column accounts is seeded with would mean
--     regenerating it and moving its checksum again;
--   * StressedPersonaSeeder creates client_006_gig's account at runtime, after migrations — a backfill here
--     could never reach it, so that account would be the one with no logo;
--   * every bank_name in the data comes from one fixed list (generate_seed_sql.BANKS, mirrored by that
--     seeder), so the name is a reliable key in practice.
-- An account whose bank_name is not listed here simply resolves to no bank row, and the UI falls back to a
-- monogram — a new bank is a missing logo, never a broken accounts list.
--
-- brand_color is the dominant saturated colour of each mark. It is reference data for now: the account
-- cards still use accounts.display_color, which is seeded independently.

create table banks (
    id          uuid primary key default gen_random_uuid(),
    code        text not null,
    name        text not null,
    name_ar     text not null,
    logo_slug   text not null,
    brand_color text not null
);

alter table banks add constraint banks_code_unique unique (code);
-- Unique because it is the join key from accounts.bank_name.
alter table banks add constraint banks_name_unique unique (name);

insert into banks (code, name, name_ar, logo_slug, brand_color) values
    ('RAJHI',   'Al Rajhi Bank',             'مصرف الراجحي',            'al_rajhi', '#2010F0'),
    ('SNB',     'Saudi National Bank',       'البنك الأهلي السعودي',     'snb',      '#004030'),
    ('RIYAD',   'Riyad Bank',                'بنك الرياض',              'riyad',    '#202060'),
    ('BSF',     'Banque Saudi Fransi',       'البنك السعودي الفرنسي',    'bsf',      '#002020'),
    ('ANB',     'Arab National Bank',        'البنك العربي الوطني',      'anb',      '#0070D0'),
    ('ALINMA',  'Alinma Bank',               'مصرف الإنماء',            'alinma',   '#002030'),
    ('ALBILAD', 'Bank Albilad',              'بنك البلاد',              'albilad',  '#E01030'),
    ('SAIB',    'The Saudi Investment Bank', 'البنك السعودي للاستثمار',  'saib',     '#101010');

-- The accounts list joins on bank_name for every row it renders.
create index idx_accounts_bank_name on accounts (bank_name);
