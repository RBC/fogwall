# Technical Audit — fogwall dashboard frontend

Target: `fogwall-dashboard/frontend` Method: code-level audit across five dimensions. Contrast computed from Tailwind
4's OKLCH token values, semantics and labels verified in source, bundle sizes measured from `dist/`. Detector run over
`src/`.

Written by hand because `audit` has no storage verb of its own — `critique-storage` covers the critique half only. The
companion critique snapshot is in `.impeccable/critique/`.

## Audit Health Score — 13/20 (acceptable, significant work needed)

| #   | Dimension                | Score | Key finding                                                                                                  |
| --- | ------------------------ | ----- | ------------------------------------------------------------------------------------------------------------ |
| 1   | Accessibility            | 1     | 36 form labels not associated with their control; `text-gray-400` fails contrast at 2.60:1 across 131 usages |
| 2   | Performance              | 3     | No code-splitting: diff2html ships to every page; two pages poll every 10s regardless of tab visibility      |
| 3   | Responsive               | 2     | 8 responsive prefixes in 16 pages; built for desktop, degrades rather than adapts                            |
| 4   | Theming                  | 3     | Dark mode coverage complete; no token layer and two neutral families in play                                 |
| 5   | Implementation integrity | 4     | One detector finding in the whole app; the system is coherent and product-specific                           |

Issues: 3 P1, 5 P2, 3 P3. No P0 — nothing blocks task completion.

## Implementation integrity — PASS

The bundled detector found exactly one issue across sixteen pages: a `border-l-4` side-tab accent at `Setup.tsx:190`.
Rendered evidence contradicts the finding — it marks proportional informational callouts — so it is a false positive in
context, though it does violate the project's own hairline rule. No gradient text, no glass cards, no purple, no Inter,
no decorative shadow. Hand-drawn icon paths and domain vocabulary mean this interface could not be dropped onto another
product unchanged.

## Findings

Issue links are to the existing backlog; "unfiled" means no existing issue covers it.

### [P1] A-01 Form labels are not associated with their controls — #593

36 instances: `Repos.tsx` (12), `Operations.tsx`, `UserDetail.tsx`, `Groups.tsx`, others. A `<label>` sits visually
above a `<select>` with no `htmlFor`, no wrapping, and no `aria-label` on the control, so a screen reader announces
"combo box" with no name. Verified on `Repos.tsx:284` and `Operations.tsx:423`; neither file contains a single
`aria-label`. WCAG 4.1.2 (A), 3.3.2 (A). Thirteen labels already do this correctly — the pattern exists and did not
propagate. Fix: `id` on each control, `htmlFor` on its label.

### [P1] A-02 text-gray-400 fails contrast at 2.60:1 across 131 usages — #593

Application-wide; heaviest in `Repos.tsx` (23), `UserDetail.tsx` (17), `PushDetail.tsx` (16). Not decorative: it carries
help text under form fields, empty states, rule ordering metadata, loading text, and close-button glyphs. At 13.5px it
needs 4.5:1. WCAG 1.4.3 (AA). Fix: promote to `text-gray-500` (4.84:1), already used 74 times.

### [P1] A-03 dark:text-gray-500 fails contrast at 3.03:1 across 123 usages — #593

The dark-mode counterpart of A-02, on `slate-800`. Clears only the large-text threshold; the text is 13.5px. WCAG 1.4.3
(AA). Fix: `dark:text-gray-400` (5.63:1), already used 91 times.

### [P2] A-04 Approve button fails contrast at 3.22:1 — #293

`PushDetail.tsx:1132`, white on `green-600` at 15.75px medium. The adjacent Reject button passes at 4.76:1 on `red-600`,
so the pair looks balanced but is half-compliant. The most consequential control in the product. WCAG 1.4.3 (AA). Fix:
`green-700` clears 4.5:1 and keeps the hue; `green-800` becomes hover.

### [P2] A-05 Focus indicator is a 1px border change on 12 fields — #593

12 inputs use `focus:border-slate-500` with no ring; 7 elsewhere correctly use `focus:ring-2`. Every
`focus:outline-none` does supply some replacement — the defect is that twelve replaced it with something thinner and
fainter than the native outline. WCAG 2.4.11 Focus Appearance (AA, new in 2.2). Fix: standardise on the 2px Gate Blue
ring DESIGN.md specifies.

### [P2] A-06 Sidebar group label and disabled destinations at 3.07:1 — #593

`Sidebar.tsx`: the uppercase "Admin" label at 10.5px is the smallest and lowest-contrast text in the product.
`slate-500` on `slate-800`. Greyed not-yet-live destinations are `<div>`s rather than disabled controls, so the
disabled-control exemption does not apply cleanly. WCAG 1.4.3 (AA). Fix: `slate-400` (5.58:1); mark the greyed
destinations `aria-disabled`.

### [P2] A-07 No code-splitting — the diff renderer ships to every page — unfiled

`PushDetail.tsx:4-6`, `PushDiff.tsx:1-2`; zero `lazy(` or `Suspense` in the app. 752 KB JS / 213 KB gzipped in one
chunk, so a reviewer landing on Overview downloads the entire diff2html renderer and its stylesheet before paint. Fix:
`React.lazy` on the two diff routes with the diff2html CSS imported inside them.

### [P2] A-08 Polling continues in hidden tabs — #251

`PushList.tsx:265` and `ScmApiActionList.tsx:81` run `setInterval(…, 10_000)`; zero `visibilitychange` handling. Every
open tab refetches every ten seconds whether or not anyone is looking — 8,640 needless queries for a tab left open
overnight, against a product principle that the default path stays cheap. The React Query migration in #251 fixes this
via `refetchOnWindowFocus`.

### [P3] A-09 Responsive behaviour is 8 prefixes across 16 pages — unfiled

5 `sm:` and 3 `lg:`, almost all on Overview's tile grid. Tables are correctly wrapped in `overflow-x-auto` (11 wrappers
for 8 tables), so narrow viewports scroll rather than break, but the 252px rail plus a `px-6` gutter with no breakpoint
means a phone gets a desktop layout to pan around. A scope choice more than a defect, given all four audiences are at
desks. If mobile enters scope, the rail collapsing below `sm` is the whole fix.

### [P3] A-10 One icon button and one image lack accessible names — #593

`Repos.tsx:103` (icon-only button, no `aria-label`); `PushDetail.tsx:744` (`<img>`, no `alt`). Isolated misses: two of
three icon buttons and eight of eleven images are labelled correctly, and the other two favicons correctly use `alt=""`.
WCAG 1.1.1 (A).

### [P3] A-11 Side-tab accent border — #293

`Setup.tsx:190`, `border-l-4`. The only detector finding in the application. See the integrity verdict above: likely a
false positive in context, but it is the one place the UI reaches for a pattern the rest of the system does not use.

## Systemic patterns

- **Two failing greys, two passing greys, all four already in the codebase.** Not a palette problem — `gray-500` and
  `dark:gray-400` pass and are widely used. Two adjacent steps got used interchangeably for the same job. A token layer
  would have made this one decision instead of 254.
- **The label pattern is right 13 times and absent 36 times.** Same shape: the correct approach exists and did not
  propagate.
- **No token layer at all.** `index.css` is fifteen lines with no `@theme` block, so every colour decision is re-made at
  each call site. Root cause under both findings above, and the reason the gray/slate split drifted unnoticed.

## What is working

- Every status badge passes contrast comfortably — 5.49:1 to 7.25:1 across all eight push states and the grant
  vocabulary. The part of the system carrying the most meaning is the part that is most correct.
- Dark mode coverage is complete: zero `bg-white` class strings without a `dark:` counterpart.
- Table overflow handled properly — 11 `overflow-x-auto` wrappers for 8 tables.
- Pagination capped at 25 with a `+1` lookahead for the has-more check. No unbounded list rendering, no virtualization
  debt.
- The one animation in the product is gated behind `motion-safe`.

## Recommended order

1. `/impeccable harden` — the accessibility batch: label association (36), the two grey promotions (254 usages,
   mechanical), Approve button to green-700, focus rings on 12 fields, sidebar group label. Moves Accessibility from 1
   to a likely 3-4 and is where nearly all the score sits. Most of it lands inside #593.
2. `/impeccable optimize` — route-split the two diff pages (A-07), pause polling in hidden tabs (A-08, via #251).
3. `/impeccable adapt` — only if mobile enters scope; otherwise record the desktop-only stance and close it.
4. `/impeccable polish` — the `border-l-4` and any drift left after the above.
