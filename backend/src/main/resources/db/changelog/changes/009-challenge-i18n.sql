--liquibase formatted sql

--changeset baseerah:009-challenge-i18n
-- Localise challenge copy per request locale (Step 8.1, QA finding I18N-01/COPY-01). Challenge title/subtitle
-- are persisted at generation time, so they cannot carry a single language and still honour the reader's
-- Accept-Language. Instead the `title` / `subtitle` columns now store message *keys* (e.g.
-- `challenge.welcome.title`), and this changeset adds `text_args` — the pipe-delimited, pre-formatted numeric
-- arguments those templates interpolate (Western digits, so figures are identical across locales). The
-- ChallengeService read path resolves key + category label + text_args to the request locale in toDto().
-- Nullable and additive: existing rows are refreshed idempotently on the next boot regeneration.
alter table challenges add column text_args text;
