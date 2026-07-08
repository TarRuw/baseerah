# Baseerah — E2E QA Findings & Fix Backlog

**Date:** 2026-07-07
**Scope:** Full end-to-end UI + functionality review of both shells (consumer app + bank portal) over the live
local backend, driven in Chrome against the Flutter **web** build.
**Method:** Every screen exercised across the seeded personas (`client_001_family`, `client_003_freelancer`,
`client_004_student`) plus the complete bank shell. The live API (`http://localhost:8080/api/v1`) was used as an
oracle to confirm displayed values and to separate front-end from back-end causes.

### Environment caveats (read before triaging)
- **Viewport was locked to desktop 2519×1333.** The test harness (Chrome + extension) ran maximized on a 2560px
  display and could not be resized or device-emulated. This app is **mobile-first**, so layout finding **UI-01** is
  specific to the *desktop-web* target; the phone experience was **not** directly assessed and should be verified
  separately on a real device / emulator.
- **Debug web build.** Tested via `flutter run -d web-server` (DDC/debug). Per-screen loading spinners and render
  stalls observed in the UI are debug-build JIT overhead — **not** backend latency (all APIs measured <10 ms).
  Re-test perf on a `flutter build web` **release** bundle before acting on any perceived-slowness concern.

---

## Summary

| ID | Severity | Area | Title | Status |
|----|----------|------|-------|--------|
| UI-01 | High (desktop web) | Consumer / layout | No responsive max-width; stress gauge fills the viewport | **Fixed** (step 8.2) |
| I18N-01 | Medium | Backend + both shells | Server-provided content strings are English-only in Arabic mode | **Fixed** (step 8.1) |
| UI-02 | Low | Consumer / Home | "1 banks" — missing singular/plural handling | **Fixed** (step 8.3) |
| UI-03 | Low | Consumer / Rescue | Recovery gauge shows *before* score (83), not recovered (91) | **Fixed** (step 8.3) |
| UI-04 | Low | Consumer / Home | "Spending velocity" = 100 renders with an orange bar | **Fixed** (step 8.3) |
| UI-05 | Low | Bank / Applications | Decision not reflected in the applicant queue in-session | **Fixed** (step 8.4) |
| UI-06 | Low | Bank / Portfolio | "Healthy" status only at score 100 (98–99 show "Watch") | **Fixed** (step 8.4) |
| UI-07 | Low | Consumer / general | Language selection not persisted across a full reload | **Fixed** (step 8.3) |
| COPY-01 | Nit | Backend / challenges | Generated copy grammar: "restaurants dining spending" | **Fixed** (step 8.1) |
| DOC-01 | Nit | docs/DEMO.md | Two statements in DEMO.md don't match actual behavior | **Fixed** (step 8.5) |

**Follow-ups (not defects):** FU-01 release-build perf pass — **Done** (step 8.5) · FU-02 real mobile-viewport assessment — **Done** (step 8.5).

> **Resolution — all items closed (Phase 8, verified step 8.5 on 2026-07-08).** Every finding above was
> fixed across steps 8.1–8.4 and re-verified on a `flutter build web` **release** bundle driven in Chrome
> against the live backend, plus the full backend (108 tests) and frontend (28 tests) suites — all green.
> See the per-finding **Status** notes below and the FU-01/FU-02 results in *Follow-ups*.

---

## Detailed findings

### UI-01 — No responsive/max-width layout (stress gauge fills the viewport)
- **Status:** **Fixed (step 8.2).** On wide web the consumer shell now renders in a centered phone-frame
  and the gauge is bounded; the score value + zone badge sit above the fold. Verified on the release build
  at 1400 px and by `responsive_frame_test`. FU-02 confirms the frame is a no-op at phone width.
- **Severity:** High on desktop web · cosmetic if the product is phone-only.
- **Area:** Consumer shell — Home / Radar (stress-score gauge widget).
- **Symptom:** On a wide viewport the semicircular stress-score gauge scales to the full content width
  (~1260 px tall at 2519 px wide). The score value, zone badge, forecast chart, stat cards, and linked-accounts
  list are pushed ~2 full screens below the fold. No mobile "phone frame" appears, although `docs/DEMO.md`
  describes one.
- **Evidence:** Home renders as a giant arc; the `95 / مثالي` score is only reachable after scrolling past the
  whole gauge. Reproduced at 2519 px width (the only width available in the harness).
- **Root cause (suspected):** The gauge sizes from available width (e.g. full-width `AspectRatio`/`CustomPaint`)
  with no upper bound, and the app has no max-width container / responsive breakpoint for web/desktop.
- **Suggested fix:** Wrap the app (or at least the consumer shell) in a centered `max-width` container
  (a phone-frame ~430 px on wide screens), or cap the gauge's max size and switch to a multi-column layout above a
  tablet breakpoint. Confirm intended web target with product first.
- **Acceptance criteria:** On a ≥1280 px-wide browser, the score value and zone badge are visible above the fold,
  and the gauge occupies a bounded, sensible size.

### I18N-01 — Server-provided content strings are English-only in Arabic mode *(highest-value fix)*
- **Status:** **Fixed (step 8.1).** Content fields are now resolved server-side by `Accept-Language` via
  message bundles. Verified live in both locales: loan `verdict` (`ميسور التكلفة` / "Comfortably affordable"),
  Rescue option `label`/`detail` and the confirm line, GenAI `reply` (Arabic answer to an Arabic question),
  challenge copy, and the bank underwriting `verdict` (`يتحمّل الدين` / "Sustains the debt") all switch
  language. Confirmed in the running UI on Simulate and the Bank report.
- **Severity:** Medium (systemic; ~6 distinct visible symptoms, one root cause). Notable for an Arabic-first product.
- **Area:** Backend response payloads → rendered raw by the frontend in both shells.
- **Symptom:** The backend returns identical **English** content strings for `Accept-Language: ar` and `en`, so
  English text appears inside the Arabic (RTL) UI. Confirmed at the API layer.
- **Affected fields / screens:**
  - Loan affordability `verdict` — e.g. `"Comfortably affordable"` (Simulate → Loan Affordability).
  - GenAI chat `reply` — an Arabic question gets an English answer (Simulate → Ask Baseerah).
  - Rescue option `label` + `detail` — `"Murabaha micro-finance"`, `"Pre-approved Sharia-compliant financing…"`.
  - Rescue confirmation line — `"Confirmed MURABAHA bridge of SAR 2800 — projected stress score recovers 83 → 91."`
  - Challenge `title` + `subtitle` — `"Mindful spender"`, `"Cap your restaurants dining spend"`.
  - Bank underwriting `verdict` — `"Sustains the debt"`.
- **Evidence:**
  ```
  POST /clients/{id}/loan-simulate  (Accept-Language: ar)  -> "verdict":"Comfortably affordable"
  POST /clients/{id}/loan-simulate  (Accept-Language: en)  -> "verdict":"Comfortably affordable"   # identical
  POST /clients/{id}/chat           (ar & en)              -> identical English "reply"
  GET  /clients/{id}/rescue                                -> options[].label/detail in English
  ```
  Note: **validation error messages ARE localized** (e.g. "لا يمكن أن يكون منعدم"), so the i18n plumbing exists —
  it just isn't applied to these content fields.
- **Root cause:** Verdict/label/detail/reply/challenge text are produced as hardcoded English in the service layer
  (and in `MockGenAi`) rather than resolved through the message-source / locale used for error messages.
- **Suggested fix:** Localize these fields server-side by `Accept-Language` (message bundles / keyed lookups), or
  return a stable enum/key + let the frontend localize. For chat, have `MockGenAi` (and the remote prompt) produce
  Arabic when the request locale is `ar`.
- **Acceptance criteria:** With the app in Arabic, all six areas above display Arabic copy; switching to English
  shows English. No English strings remain in the Arabic UI for these flows.

### UI-02 — "1 banks" (missing pluralization)
- **Status:** **Fixed (step 8.3).** Badge now reads the correct singular — verified live as
  `مباشر · بنك واحد` (and "Live · 1 bank" in English).
- **Severity:** Low. **Area:** Consumer / Home "Live · N banks" badge.
- **Symptom:** Shows `Live · 1 banks` (EN) and `مباشر · 1 بنوك` (AR) for a single linked bank.
- **Suggested fix:** Use ICU plural / a singular-vs-plural helper (`1 bank` / `بنك واحد` / `N بنوك`).
- **Acceptance criteria:** Correct singular form at count 1 in both languages.

### UI-03 — Rescue recovery gauge shows the *before* score, not the recovered score
- **Status:** **Fixed (step 8.3).** The recovery gauge now rests on the projected post-rescue score (91),
  consistent with its caption. Covered by `recovery_gauge_test` ("rests on the recovered score, not the
  before score").
- **Severity:** Low. **Area:** Consumer / Rescue — "complete" state.
- **Symptom:** After confirming a bridge, the recovery gauge's large number reads **83** (current score) while the
  caption says "…recovers **83 → 91**". For a screen titled "score recovery", the headline number should be the
  projected 91.
- **Suggested fix:** Animate/display the gauge to the projected post-rescue score (91), or clearly label the 83 as
  "current" with 91 as the target.
- **Acceptance criteria:** The recovery visual communicates the improved score without contradicting its caption.

### UI-04 — Spending-velocity stat at max value renders orange
- **Status:** **Fixed (step 8.3).** The colour-threshold mapping for spending velocity was corrected so the
  bar colour matches the metric's intended direction at the extremes.
- **Severity:** Low. **Area:** Consumer / Home stat cards.
- **Symptom:** "Spending velocity = 100" shows an **orange** progress bar, while "Income consistency = 100" is
  green. A top value reading as a caution colour is inconsistent.
- **Suggested fix:** Review the colour-threshold mapping for spending velocity (confirm whether high is good/bad;
  align the bar colour accordingly).
- **Acceptance criteria:** Bar colour matches the metric's intended good/bad direction at the extremes.

### UI-05 — Bank decision not reflected in the applicant queue in-session
- **Status:** **Fixed (step 8.4).** After a decision the queue refetches and the applicant's status chip
  updates in-session — verified live: approving Khalid Al-Otaibi flipped his chip to `موافق عليه` (Approved)
  with no manual reload (a `GET /bank/applicants` fires immediately after the decision POST).
- **Severity:** Low. **Area:** Bank / Applications.
- **Symptom:** After "Approve loan", the confirmation shows in the report pane, but the applicant remains under
  "Pending review" with its original risk badge (no approved/declined status). *(The Portfolio screen does pick up
  the approved facility, so the decision persists — only the queue view is stale.)*
- **Suggested fix:** On decision, update the applicant's list item (status chip / move out of "Pending review"),
  or refetch the queue.
- **Acceptance criteria:** After a decision, the queue reflects the applicant's new status without a manual reload.

### UI-06 — Portfolio "Healthy" status only at score 100
- **Status:** **Fixed (step 8.4).** Portfolio status is now health-banded (Healthy / Watch / At-risk) rather
  than "Healthy" only at 100. Covered by the bank portfolio widget test.
- **Severity:** Low (possible design choice — confirm). **Area:** Bank / Portfolio monitoring table.
- **Symptom:** Facilities at health 98–99 show "Watch"; only 100 shows "Healthy". The Healthy band looks
  unintentionally narrow.
- **Suggested fix:** Review status thresholds (e.g. Healthy ≥ 90, Watch 70–89, At-risk < 70) with product.
- **Acceptance criteria:** Status bands agree with the intended risk policy.

### UI-07 — Language selection not persisted across a full reload
- **Status:** **Fixed (step 8.3).** The chosen locale is now persisted (shared prefs / `localStorage`) and
  restored on boot, so it survives a full reload.
- **Severity:** Low. **Area:** Consumer / general (locale state).
- **Symptom:** Switching to English then doing a full page reload returns to Arabic (default). Locale is held in
  memory only. *(In-app navigation preserves it; only a hard reload resets it.)*
- **Suggested fix:** Persist the chosen locale (e.g. `localStorage` / shared prefs) and restore on boot.
- **Acceptance criteria:** Chosen language survives a full reload.

### COPY-01 — Generated challenge copy grammar
- **Status:** **Fixed (step 8.1).** Challenge copy moved into localized message bundles and the category
  name corrected to `restaurant dining` (was "restaurants dining"), so the subtitle now reads
  "Cap your restaurant dining spend".
- **Severity:** Nit. **Area:** Backend / challenge subtitle generation.
- **Symptom:** "You've already tracked SAR 1431 of **restaurants dining** spending" / "Cap your **restaurants
  dining** spend" — awkward ("restaurant dining").
- **Suggested fix:** Fix category-name interpolation/grammar in the challenge text builder (also covered if
  I18N-01 moves this copy into localized bundles).

### DOC-01 — DEMO.md statements that don't match actual behavior
- **Status:** **Fixed (step 8.5).** DEMO.md §7 now states the server-provided content follows the language
  toggle (true after 8.1); §9 now describes selecting an applicant then clicking the explicit **"Generate
  predictive report"** button (no auto-generation). Drifted persona figures were also refreshed against the
  live API (freelancer shortfall ~SAR 2,943; Murabaha ~SAR 3,000).
- **Severity:** Nit (documentation). **Area:** `docs/DEMO.md`.
- **Symptoms:**
  1. §7 states "because requests carry `Accept-Language`, the API copy follows" the language toggle — untrue for
     the content fields listed in **I18N-01** (only error messages follow the locale today).
  2. §9 implies clicking an applicant auto-generates the report ("empty→generating spinner…then the report"), but
     the UI actually requires clicking an explicit **"Generate predictive report"** button first.
- **Suggested fix:** Update DEMO.md once I18N-01 is resolved (or note the current limitation), and correct the
  bank-report step to mention the button.

---

## Verified NON-issues (do not re-investigate)
- Gauge marker briefly appearing on the red arc → a mid-animation frame; it settles on green for a high score.
- Rescue "You're in the clear" for `client_001_family` → correct (that persona has no predicted deficit).
- Per-screen spinners / occasional screenshot stalls → Flutter **debug**-web JIT; backend responds in <10 ms.
- Persona display name staying Arabic in English mode → proper-noun seed data; acceptable.

## Follow-ups
- **FU-01 — Release-build perf pass — Done (step 8.5).** Built `flutter build web --release`, served the
  bundle, and drove Home → Simulate (Loan + Ask-Baseerah) → Bank (Applications, report generation,
  Approve decision) in Chrome against the live backend. The per-screen loading spinners/stalls seen under
  `flutter run` (debug) were **gone** — every screen and transition rendered without a debug stall.
  Backend analytics stayed far under the 2.5 s NFR: stress-score ≈ 4–5 ms, bank report ≈ 8 ms,
  30-day forecast ≈ 3 ms (all HTTP 200). Confirms the debug-web JIT overhead was the only "slowness".
- **FU-02 — Real mobile-viewport assessment — Done (step 8.5).** The Chrome window would not shrink below
  the desktop minimum (`window.innerWidth` stayed 1542 — same harness limit noted above), so the consumer
  shell was rendered inside a **390×844 iframe** to give Flutter a true phone `MediaQuery`. At phone width
  the mobile-first layout is correct and **full-bleed** — step 8.2's max-width phone-frame is a **no-op**
  (no letterbox, no regression): toolbar, greeting, bounded stress gauge (settles at 95), stat cards,
  30-day forecast, and bottom nav all lay out correctly. Corroborated by `responsive_frame_test`
  ("caps … on a wide surface" + "is a no-op at phone width").

## Suggested fix ordering
1. **I18N-01** — one backend change resolves ~6 visible symptoms and the most jarring Arabic-first defect.
2. **UI-01** — decide desktop-web vs phone-only, then add the max-width/phone-frame constraint if web is a target.
3. Batch the low-severity polish (**UI-02..UI-07**, **COPY-01**) together.
4. **DOC-01** — update docs alongside the I18N-01 fix.
5. Run **FU-01 / FU-02** before sign-off.
