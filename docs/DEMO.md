# Baseerah — Hackathon Demo Script & Persona Walkthrough

A repeatable, presenter-ready walkthrough of both shells (consumer app + bank portal) over the one
local backend. Follow it verbatim against a fresh checkout after completing
[the getting-started steps in the root README](../README.md). Design authority: [`DESIGN.md`](DESIGN.md).

> **The numbers in this script are computed live from the seeded `data-mocks/` transactions** (Global
> Rule: nothing is hardcoded). Exact figures may shift if the mock data changes; the *behaviour* is
> stable. Values below were captured from a live run on the seeded dataset.

---

## How the demo is driven

- **One account per persona — no in-app switcher.** The consumer app represents a single seeded
  account. Choose which one at launch with
  `flutter run --dart-define=BASEERAH_CLIENT=<externalId>` (see the map below). A plain `flutter run`
  (no flag) runs as the first seeded client, `client_001_family`. An unknown id fails loud with the
  list of valid ids, so you never silently demo the wrong persona.
- **Two shells, one toolbar.** The top toolbar carries the logo (بصيرة), a **Consumer / Bank**
  segmented control, and an **EN / ع** language toggle. The consumer shell has a bottom nav
  (**Home · Simulate · Rescue · Goals**); the bank shell has a left sidebar
  (**Applications · Portfolio · Settings**). The bank shell has its **own applicant list**, so bank
  personas are selected in-screen — no launch flag needed for the bank walkthrough.
- **Offline by default.** `GENAI_PROVIDER=mock` (the default) makes Ask-Baseerah answer without any
  API key, so the whole demo runs offline. See the README for upgrading to a real streamed reply.

---

## Persona → feature demo map (DESIGN §11)

| Persona (`externalId`) | Launch as | Screen | The "wow" moment |
|---|---|---|---|
| `client_001_family` — الموظف المستقر (أبو عائلة) | `--dart-define=BASEERAH_CLIENT=client_001_family` (or default) | **Home / Radar** + **Simulate** | Healthy **OPTIMAL** gauge (score ~95) and a "Comfortably affordable" loan baseline. |
| `client_003_freelancer` — رائد الأعمال المستقل | `--dart-define=BASEERAH_CLIENT=client_003_freelancer` | **Rescue** | **Smart Rescue** headline: a projected shortfall (~SAR 2,943) with two Sharia-compliant bridge options → confirm → **before→after score recovery** (e.g. 83 → 91). |
| `client_004_student` — الطالب الجامعي | `--dart-define=BASEERAH_CLIENT=client_004_student` | **Goals** | Gamified micro-saving: challenge cards with progress, **Claim** flow, Akhtar-points card + risk tier. |
| `client_002_tech_bro` — الشاب العازب (عاشق القهوة) | `--dart-define=BASEERAH_CLIENT=client_002_tech_bro` | **Home / Radar** | **Performance** showcase: the highest-transaction-volume persona; analytics still return in **well under 2.5 s** (measured single-digit ms). |
| `client_005_vip` — العميل عالي الملاءة المالية | Bank shell (segmented control) → pick in Applications list | **Bank portal** | Predictive underwriting report + portfolio cross-sell view. |

> **Two honest notes the seeded data forces** (verified against the live API):
> - **`client_002_tech_bro` computes in the OPTIMAL band (~92), not a warning zone.** DESIGN §11
>   describes it as "warning-zone", but the score is derived from the seeded transactions and lands
>   OPTIMAL. Its genuine headline is the **large-volume performance** check, so demo it there.
> - **`client_003_freelancer`'s deficit is a long-horizon projection, not a pulsing 12–15-day alert.**
>   The Rescue endpoint reports `hasDeficit: true`, a real shortfall, and two options, but
>   `alertRaised: false` (the deficit is years out on the seeded data), so the banner shows the
>   shortfall and options without the pulsing-alert animation. The rescue **mechanics** — two bridge
>   options and a real before→after score recovery — are the wow moment.

---

## Consumer shell — numbered click-through

Launch as the persona you want to show (see the map). Steps 1–4 assume `client_001_family`; the
persona-specific moments (5, 6) call out which account to launch as.

1. **Open the app.** It boots **Arabic-first (RTL)**. The mobile phone frame shows the top toolbar
   and the bottom nav (Home · Simulate · Rescue · Goals). You land on **Home**.
2. **Home / Radar — the stress-score gauge.** Point at the **animated Stress Score gauge** (SVG arc,
   three zones, ~1000 ms cubic ease-out marker sweep). On `client_001_family` it settles in the green
   **OPTIMAL** zone (~95). Note the live-multibank badge, the **30-day forecast chart**, and the
   income-consistency / spending-velocity stat cards, then the linked-accounts list below.
3. **Simulate → Loan Affordability.** Tap **Simulate**, stay on the **Loan Affordability** tab. Drag
   the three sliders (principal, rate, term). The installment, **DTI**, colour-coded **verdict**, and
   projected-score impact update **live** — e.g. SAR 50,000 / 5.5% / 36 mo → ~SAR 1,510/mo, DTI ~9%,
   **"Comfortably affordable"** (green).
4. **Simulate → Ask Baseerah.** Switch to the **Ask Baseerah AI** tab. Type a question
   (e.g. *"هل يمكنني شراء سيارة؟"* — "can I buy a car?"). Watch the typing dots, then a grounded reply
   that references the persona's own score and surplus. (Mock provider — instant, offline. With
   `GENAI_PROVIDER=remote` + a key, this streams token-by-token instead.) Try a suggestion chip and the
   invoice-upload affordance.
5. **Rescue — the Smart Rescue headline.** *Relaunch as
   `--dart-define=BASEERAH_CLIENT=client_003_freelancer`* and tap **Rescue**. The screen is in its
   **open** state: a **shortfall banner** (~SAR 2,943), two selectable **bridge cards** —
   **Murabaha micro-finance** (Sharia-compliant, ~SAR 3,000 over 3 months) and **Liquidate fund
   assets** (no financing cost). Select one, tap **Confirm**, and the screen flips to the **complete**
   state: a success card with the **before→after score recovery** (e.g. **83 → 91**) and a run-again
   action.
6. **Goals — gamified saving.** *Relaunch as
   `--dart-define=BASEERAH_CLIENT=client_004_student`* and tap **Goals**. Show the gold **reward-points
   card** + risk tier (starts at 0 points / BRONZE), then the challenge cards with progress bars. Tap
   **Claim** on a completed challenge to run the claim flow (Claim → Claimed).
7. **Language / RTL toggle.** Tap **EN / ع** in the toolbar. The whole app flips between Arabic (RTL)
   and English (LTR) — layout mirrors, numerals and currency reformat, and — because every request
   carries `Accept-Language` — the **server-provided content** follows too: loan **verdicts**, **Rescue**
   option labels/details and the confirmation line, challenge copy, and the **Ask-Baseerah** reply all
   switch language (localized server-side in step 8.1; verified live). Only proper-noun seed data such as
   persona display names stays fixed. The chosen language now also survives a full page reload (step 8.3).

---

## Bank portal — numbered click-through

8. **Switch shells.** In the top toolbar, tap **Bank** on the Consumer/Bank segmented control. The
   layout becomes a desktop frame with the left sidebar (Applications · Portfolio · Settings).
9. **Applications — generate a predictive report.** On **Applications**, the left pane lists the
   underwriting queue (with risk badges). Click an applicant (this is the real in-screen picker — use
   it to feature `client_005_vip`). The right pane shows the applicant header with a **"Generate
   predictive report"** button — selecting an applicant does *not* auto-generate. Click that button; the
   pane switches to the **generating** spinner ("Analyzing 24-month telemetry…"), then the **report**: a
   **verdict panel**, a **financial-stamina** box, three **KPI** boxes, and a **12-month cash-flow
   chart**. (Report generation returns in ~10 ms.)
10. **Decision.** Click **Approve** (or **Decline**) — the decision is recorded and reflected in the
    applicant's status.
11. **Portfolio — monitoring.** Open **Portfolio**. Show the four **KPI cards** (active facilities,
    avg stamina, NPL rate, at-risk) and the monitoring table with per-row health scores, trend arrows,
    and status badges.
12. **Settings — compliance controls (Step 7.1).** Open **Settings**. Show:
    - **SAMA sync status** (last-sync timestamp).
    - **NDMO residency toggle** — ON (default) stamps bank payloads `dataResidency = "KSA"` and gates
      export **closed**; OFF → `"UNRESTRICTED"` and export allowed. The change takes effect on the very
      next request.
    - **Tokenization toggle** — ON exposes only `tokenized_account_id` to the bank view. OFF **hides**
      the tokenized reference (empty list) — it does **not** reveal raw account numbers; Baseerah never
      exposes raw account ids to the bank side.
    - **Stamina-floor** and **auto-decline-threshold** sliders — raising the floor or lowering the DTI
      ceiling **auto-declines** more applicants (verdict forced to BAD / tier C). Nudge a slider, return
      to Applications, regenerate a borderline report, and show the verdict change. (Defaults:
      stamina floor 50, auto-decline 71.)

---

## Optional: verify a persona from the command line

Every persona's data is reachable directly, useful for a quick sanity check before presenting (base
URL `http://localhost:8080/api/v1`, IDs from `GET /clients`):

```bash
curl -s http://localhost:8080/api/v1/clients                       # list the 5 seeded personas + ids
curl -s http://localhost:8080/api/v1/clients/<id>/stress-score     # score + zone + sub-scores
curl -s http://localhost:8080/api/v1/clients/<id>/rescue           # shortfall + bridge options
curl -s http://localhost:8080/api/v1/bank/applicants               # underwriting queue
```

See [`DESIGN.md`](DESIGN.md) §6 for the full API surface.
