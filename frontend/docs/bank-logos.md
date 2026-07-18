# Bank marks — provenance and status

The marks live in `assets/banks/`, one PNG per `banks.logo_slug` (see `013-banks.sql`). The accounts list
renders these to identify which bank each linked account belongs to.

This file is kept **outside** `assets/banks/` on purpose: everything in that directory is bundled into the
web build and served publicly, and these notes are internal. Keep that directory to image files only.

## These are third-party trademarks

Every file here is the trademark of the bank it identifies. None is Baseerah's to license, and none was
supplied by its owner. They are used **nominatively** — to identify the real bank that actually holds the
user's account, which is what an account-aggregation UI is for — not as decoration and not in any way that
suggests those banks endorse or operate Baseerah.

Before this ships to real users, confirm the position with whoever owns that call. The safest routes are a
brand-kit licence from each bank (they issue these to licensed aggregators) or the monogram fallback that
`BankLogo` already renders when an asset is missing — deleting a file here is enough to fall back to it,
with no code change.

## Where each came from

Fetched from each bank's own public website on 2026-07-16. Several banks only expose their mark as a
favicon; where that was the only source, the file is an upscale of a small original (flat vector-style
marks survive this well, which is why they still look crisp).

| slug | source | original |
|---|---|---|
| `al_rajhi` | `alrajhibank.com.sa/favicon-32x32.ico` | 32×32 favicon |
| `snb` | `alahli.com/.../snb-web/light/shared/assets/logo.svg` | SVG, rendered at 256 |
| `riyad` | `riyadbank.com/o/rb-public-portal-theme/images/favicon.ico` | 32×32 favicon |
| `bsf` | `alfransi.com.sa/images/favicon.png` | 32×37 favicon |
| `anb` | `anb.com.sa/o/anb-theme/images/favicon.ico` | 48×48 favicon (their logo URL is bot-walled) |
| `alinma` | Alinma's site icon | 48×48 (their published SVG is the white-on-dark variant — invisible on the card) |
| `albilad` | `bankalbilad.com/SiteAssets/favicon.ico` | 32×32 favicon |
| `saib` | `saib.com.sa/sites/default/files/2025-01/saib_logo.jpg` | 600×316; cropped to the emblem — the full lockup is emblem + wordmark and the wordmark is illegible at 36pt |

Wikipedia was rejected as a source: it carries only 2 of the 8, both tagged **non-free / fair use** scoped
to their own article, which would not cover redistribution here.

## How they were processed

Trimmed to their ink bounding box, centred on a transparent square, resized to 128×128 (LANCZOS). 128 is
~3× the 36pt display size, so they stay sharp on a 3× screen. `banks.brand_color` was derived from the
dominant saturated pixel of each mark.

## Replacing one with an official asset

Drop the new file in under the same slug, sized 128×128 with a transparent background and the mark trimmed
to the edges. No code change — `BankLogo` resolves `assets/banks/<logo_slug>.png` from the API's `bankCode`.
