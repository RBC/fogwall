---
name: fogwall Dashboard
description: The console where a held push is inspected, decided, and released.
colors:
  rail: "oklch(27.9% 0.041 260.031)"
  rail-edge: "oklch(20.8% 0.042 265.755)"
  rail-hover: "oklch(37.2% 0.044 257.287)"
  accent: "oklch(54.6% 0.245 262.881)"
  accent-pressed: "oklch(48.8% 0.243 264.376)"
  affirm: "oklch(62.7% 0.194 149.214)"
  refuse: "oklch(57.7% 0.245 27.325)"
  surface: "#ffffff"
  surface-sunken: "oklch(98.5% 0.002 247.839)"
  surface-dark: "oklch(27.9% 0.041 260.031)"
  surface-dark-raised: "oklch(37.2% 0.044 257.287)"
  hairline: "oklch(92.8% 0.006 264.531)"
  hairline-dark: "oklch(37.2% 0.044 257.287)"
  ink: "oklch(27.8% 0.033 256.848)"
  ink-muted: "oklch(55.1% 0.027 264.364)"
  ink-faint: "oklch(70.7% 0.022 261.325)"
  held-bg: "oklch(96.2% 0.059 95.617)"
  held-ink: "oklch(47.3% 0.137 46.201)"
  cleared-bg: "oklch(96.2% 0.044 156.743)"
  cleared-ink: "oklch(44.8% 0.119 151.328)"
  delivered-bg: "oklch(93.2% 0.032 255.585)"
  delivered-ink: "oklch(42.4% 0.199 265.638)"
  turned-back-bg: "oklch(93.6% 0.032 17.717)"
  turned-back-ink: "oklch(44.4% 0.177 26.899)"
  refused-bg: "oklch(95.4% 0.038 75.164)"
  refused-ink: "oklch(47% 0.157 37.304)"
  abandoned-bg: "oklch(96.7% 0.003 264.542)"
  abandoned-ink: "oklch(44.6% 0.03 256.802)"
  at-gate-bg: "oklch(96.8% 0.007 247.896)"
  at-gate-ink: "oklch(44.6% 0.043 257.281)"
  broke-bg: "oklch(88.5% 0.062 18.334)"
  broke-ink: "oklch(39.6% 0.141 25.723)"
typography:
  display:
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', 'Noto Sans', Arial, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: "2rem"
    letterSpacing: "normal"
  headline:
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', 'Noto Sans', Arial, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 600
    lineHeight: "1.75rem"
  title:
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', 'Noto Sans', Arial, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 600
    lineHeight: "1.25rem"
    letterSpacing: "0.025em"
  body:
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', 'Noto Sans', Arial, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: "1.25rem"
  label:
    fontFamily:
      "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', 'Noto Sans', Arial, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 600
    lineHeight: "1rem"
  evidence:
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace"
    fontSize: "0.75rem"
    fontWeight: 400
    lineHeight: "1rem"
rounded:
  default: "0.25rem"
  md: "0.375rem"
  lg: "0.5rem"
  full: "9999px"
spacing:
  hairline-gap: "0.125rem"
  tight: "0.5rem"
  snug: "0.875rem"
  base: "1rem"
  gutter: "1.5rem"
components:
  nav-item:
    textColor: "{colors.ink-faint}"
    rounded: "{rounded.lg}"
    padding: "0.375rem 0.625rem"
    typography: "{typography.body}"
  nav-item-active:
    backgroundColor: "{colors.accent}"
    textColor: "#ffffff"
    rounded: "{rounded.lg}"
    padding: "0.375rem 0.625rem"
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "#ffffff"
    rounded: "{rounded.default}"
    padding: "0.25rem 0.75rem"
    typography: "{typography.body}"
  button-primary-hover:
    backgroundColor: "{colors.accent-pressed}"
    textColor: "#ffffff"
  button-approve:
    backgroundColor: "{colors.affirm}"
    textColor: "#ffffff"
    rounded: "{rounded.default}"
    padding: "0.5rem 1rem"
  button-reject:
    backgroundColor: "{colors.refuse}"
    textColor: "#ffffff"
    rounded: "{rounded.default}"
    padding: "0.5rem 1rem"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "oklch(44.6% 0.03 256.802)"
    rounded: "{rounded.default}"
    padding: "0.5rem 1rem"
  card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.lg}"
    padding: "1rem 1.5rem"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.default}"
    padding: "0.375rem 0.75rem"
    typography: "{typography.body}"
  badge-status:
    rounded: "{rounded.full}"
    padding: "0.125rem 0.5rem"
    typography: "{typography.label}"
  table-header:
    backgroundColor: "{colors.surface-sunken}"
    textColor: "{colors.ink-muted}"
    typography: "{typography.label}"
    padding: "0.5rem 1rem"
---

# Design System: fogwall Dashboard

## Overview

**Creative North Star: "The Checkpoint"**

A push arrives, is held, inspected, stamped, and released. Everything in this interface serves that sequence. The chrome
is deliberately quiet — a dark rail on the left, white cards on a pale field, hairline borders — so that the one thing
carrying saturated color is the thing carrying state: the status of a held push. Color is not decoration here; it is the
stamp.

The system is function-over-fashion by explicit intent, because the decisions made through it have consequences. An
approver is reading a diff to decide whether code leaves the building; an auditor is reconstructing why that decision
was made. Both need density, exact values, and labels that mean what they say. Neither needs personality. The craft in
this system shows in alignment, consistency of the status vocabulary, and the discipline of a two-size type scale — not
in effects, gradients, or motion.

Density is high and intentional. 474 of 503 type usages in the application are the two smallest sizes, and the root font
size is set to 18px rather than the browser's 16px — so the whole rem scale renders 12.5% larger than Tailwind's
defaults suggest, buying legibility back at high information density. A reader scans this interface; they do not read
it.

**Key Characteristics:**

- Neutral chrome, saturated status — color appears where state is
- Two type sizes carry almost everything; a third size means "this is a page" or "this is a number"
- Flat surfaces separated by 1px hairlines; shadow means "floating above the page"
- Monospace is reserved for machine-produced strings a human may need to copy
- Single measure: every page is the same width with the same gutter
- No token layer — the system lives in repeated utility strings and this document

## Colors

Cool, low-chroma neutrals for everything structural; a full-saturation hue only where a record's state or an action's
consequence must be unmistakable.

### Primary

- **Gate Blue** (`{colors.accent}`, Tailwind blue-600): fogwall's one accent. The active navigation item, primary action
  buttons, and links. Used sparingly and deliberately — it is the product's accent, so it may carry a non-interactive
  emphasis when that emphasis is genuinely earned, but it never becomes a background wash.
- **Gate Blue Pressed** (`{colors.accent-pressed}`, blue-700): hover and active state for anything Gate Blue.

### Secondary

- **Consequence Green** (`{colors.affirm}`, green-600) and **Consequence Red** (`{colors.refuse}`, red-600): reserved
  for the two irreversible decisions — approve and reject. These two colors appear on solid-filled buttons and nowhere
  else at full saturation; seeing them means a decision is available right now.

### Tertiary — the status vocabulary

Eight states, each a pale tint with a dark ink of the same hue and a mid-tone border. These are the loudest colors in
the product, and they are the point of it.

- **Held** (`{colors.held-bg}` / `{colors.held-ink}`, amber): PENDING — waiting on a human.
- **Cleared** (`{colors.cleared-bg}` / `{colors.cleared-ink}`, green): APPROVED.
- **Delivered** (`{colors.delivered-bg}` / `{colors.delivered-ink}`, blue): FORWARDED — it reached upstream.
- **Turned Back** (`{colors.turned-back-bg}` / `{colors.turned-back-ink}`, red): REJECTED.
- **Refused** (`{colors.refused-bg}` / `{colors.refused-ink}`, orange): DENIED — refused at the gate rather than by a
  reviewer.
- **Abandoned** (`{colors.abandoned-bg}` / `{colors.abandoned-ink}`, gray): CANCELED.
- **At the Gate** (`{colors.at-gate-bg}` / `{colors.at-gate-ink}`, slate): RECEIVED — arrived, not yet judged.
- **Broke** (`{colors.broke-bg}` / `{colors.broke-ink}`, deep red): ERROR — the only status with a heavier tint than the
  rest, because it is the only one that means the system itself failed.

A parallel tint vocabulary marks permission grants (sky, emerald, teal, amber, fuchsia, violet, rose, orange) and path
match types (purple, orange). These are categorical labels, not states: same shape, same weight, lower stakes.

### Neutral

- **Rail** (`{colors.rail}`, slate-800) with **Rail Edge** (`{colors.rail-edge}`, slate-900): the persistent left
  navigation, dark in both themes. The rail is the one element that never changes with the theme — it is the frame the
  product sits in.
- **Surface** (`{colors.surface}`, white) and **Sunken** (`{colors.surface-sunken}`, gray-50): card faces and table
  headers.
- **Hairline** (`{colors.hairline}`, gray-200): the 1px border doing the work shadows normally do.
- **Ink** (`{colors.ink}`, gray-800) / **Ink Muted** (`{colors.ink-muted}`, gray-500) / **Ink Faint**
  (`{colors.ink-faint}`, gray-400): primary text, secondary labels, and de-emphasized metadata.

**Open decision — two neutral families.** The implementation runs `gray-*` in light mode and `slate-*` in dark, with
crossovers in both directions (`bg-slate-100` on light surfaces, `dark:bg-gray-800` on dark ones). This is recorded as
drift, not resolved: no rule declares a winner yet. Until one does, prefer the family already used by the surrounding
component rather than introducing a third pattern.

### Named Rules

**The Stamp Rule.** Saturated color belongs to state and consequence. If a color is not reporting a status, marking a
grant, or offering a decision, it should be a neutral.

**The Rail Is Constant Rule.** The navigation rail is dark in both themes. Never theme it to match the content area.

## Typography

**Body Font:** system UI stack (`-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, `Roboto`, …) **Evidence Font:**
system monospace stack (`ui-monospace`, `SFMono-Regular`, `Menlo`, `Consolas`, …)

**Character:** No web fonts are loaded, by design. The interface speaks in the operating system's own voice — which is
the correct register for infrastructure a developer keeps open next to a terminal, and which costs nothing to load on a
page that sits inline with their workflow.

**The 18px root.** `html { font-size: 18px }` means every rem value renders 12.5% larger than Tailwind's nominal scale.
The values below are the source values; the rendered size follows in parentheses.

### Hierarchy

- **Display** (600, 1.5rem → 27px): page titles only. One per page.
- **Headline** (600, 1.125rem → 20.25px): section headings inside a page.
- **Title** (600, 0.875rem → 15.75px, often uppercase with 0.025em tracking): group labels above a set of rows or
  fields.
- **Body** (400, 0.875rem → 15.75px): the default. Table cells, descriptions, form values.
- **Label** (600, 0.75rem → 13.5px): badges, table headers, metadata, timestamps.
- **Evidence** (400 mono, 0.75rem → 13.5px): commit SHAs, ref names, repository paths, pattern strings, record IDs,
  error output.

### Named Rules

**The Two Sizes Rule.** Body and Label carry the interface — 474 of 503 type usages. A larger size is a claim that
something is a page title or a number worth stopping at. Reach past the two sizes only for those.

**The Mono Means Machine Rule.** Monospace marks a string the machine produced and a human may need to copy or compare
exactly. It is never used for emphasis, and prose is never set in it.

## Layout

A fixed dark rail plus a single scrolling column. The rail is 14rem (252px) expanded, 4rem (72px) collapsed, sticky
full-height, with the collapse toggle living in the top bar rather than the rail itself. The top bar is a 60px sticky
header carrying breadcrumbs over a translucent backdrop blur, so the trail stays visible while a long push record
scrolls beneath it.

Every page body is `max-w-6xl` with a `1.5rem` (27px) gutter — 1296px at this root size. Vertical rhythm is `py-6` for
standard pages and `py-8` for sparser ones, with `space-y-4` between blocks and `gap-3.5` (15.75px) between grid tiles.

Responsive behavior is minimal and honest about it: 5 `sm:` and 3 `lg:` prefixes exist in the entire application, almost
all of them on Overview's tile grid. The dashboard is built for a desktop viewport. A narrow viewport gets a single
column by default rather than a designed small-screen layout.

### Named Rules

**The Single Measure Rule.** Every page is `max-w-6xl` with a `px-6` gutter. There is no wide page, no narrow page, and
no full-bleed page. A reader moving between Pushes and Users should not feel the column shift under them.

## Elevation & Depth

Flat by default. Surfaces sit in the page plane and are separated by 1px hairline borders, not by shadow. Depth is
reserved to mean one specific thing: this element has left the page and is floating above it.

Interactive surfaces may take a subtle shadow on hover to signal that they are actionable — a deliberate extension of
the current implementation, which today changes only background color on hover.

### Shadow Vocabulary

- **Resting** (`box-shadow: 0 1px 3px 0 rgb(0 0 0 / 0.1), 0 1px 2px -1px rgb(0 0 0 / 0.1)`): the base `shadow` on a
  small number of summary cards. Use sparingly; the bordered card is the default.
- **Hover lift** (`box-shadow: 0 1px 2px 0 rgb(0 0 0 / 0.05)`): actionable rows and cards on hover, paired with the
  existing background shift.
- **Floating** (`box-shadow: 0 10px 15px -3px rgb(0 0 0 / 0.1), 0 4px 6px -4px rgb(0 0 0 / 0.1)`): toasts.
- **Modal** (`box-shadow: 0 20px 25px -5px rgb(0 0 0 / 0.1), 0 8px 10px -6px rgb(0 0 0 / 0.1)`): dialogs only.

### Named Rules

**The Float-Only Rule.** Shadow at rest on an in-page surface is wrong. If it is not floating above the page or
responding to a hover, it is bordered and flat.

## Shapes

Rectilinear and plain. Three radii do all the work, and the choice between them is semantic rather than aesthetic:

- **`0.25rem` (4.5px)** — the default. Buttons, inputs, small chips, code blocks. Anything inline with text.
- **`0.5rem` (9px)** — cards, panels, table containers, navigation items. Anything that is a region.
- **Full** — status badges and permission badges only. The pill shape is what makes a stamp read as a stamp at label
  size.

Borders are always 1px. There are no decorative dividers, no rules with color, and no clipping or masking anywhere in
the system.

### Named Rules

**The Three Radii Rule.** 4.5px for things in the text flow, 9px for regions, full for stamps. A fourth radius is a
mistake.

## Components

### Buttons

- **Shape:** 4.5px radius (`rounded`), no border on filled variants.
- **Primary:** Gate Blue fill, white text, `0.25rem 0.75rem` padding at Body size. Hover shifts to Gate Blue Pressed
  over a `transition-colors`.
- **Approve / Reject:** Consequence Green / Consequence Red fill, white text, `0.5rem 1rem` padding, Label weight.
  Disabled drops to a gray fill with `not-allowed` cursor and 40% opacity — deliberately unmistakable, because these are
  the consequential controls.
- **Secondary:** white surface, gray-300 border, muted ink, hover to the sunken surface.
- **Focus:** Gate Blue focus ring, 2px, offset from the control. Every `focus:outline-none` in the code does supply a
  replacement, but 12 of them replace it with a 1px border-colour change alone, which is too thin and too low-contrast
  to satisfy WCAG 2.2 Focus Appearance. The 2px ring is the system's answer; the border-only fields have not caught up
  to it.

### Confirmation, undo, and consequence

The audience is impatient developers doing repetitive work (see PRODUCT.md). A dialog in front of every action costs
each of them a click to guard against the rare mistake, and teaches everyone to dismiss dialogs without reading — which
removes the protection exactly where it was needed. Reversibility decides the treatment:

- **Reversible** (grant or revoke a role, add or remove an identity, delete a group or rule): apply immediately, then
  offer the reversal in the success toast. No dialog. Zero cost on the common path, full recovery on the mistaken one.
- **Reversible but invisible in effect** (removing an SCM identity silently breaks a push tomorrow): apply immediately,
  and state the consequence in the toast rather than the prompt — "Removed github:fixture-dev — dev can no longer push
  to 3 repos. Undo."
- **Irreversible** (approve, which forwards upstream; reject, which notifies the developer): interrupt. There is no undo
  to offer, so this is where ceremony is spent. Spending it here is only affordable because it is not spent everywhere
  else.

Every mutation gets feedback naming what changed, whichever branch it took. Silence after an action is never correct.

### Named Rules

**The Undo Beats Confirm Rule.** If the action can be reversed, reverse it on request instead of asking permission for
it. A confirmation dialog is reserved for what cannot be taken back.

**The Keyboard Is Not An Enhancement Rule.** The primary users live in a terminal and work this interface repetitively.
Every repeated loop — the approval decision above all — is fully operable from the keyboard, with visible focus at each
step and a submit shortcut on the form that ends it.

**The Read-Only Is A Mode Rule.** A viewer without authority to act is never shown a disabled version of the acting
interface. Render what they can do and state what they cannot; a greyed-out form reads as breakage, not as permission.

### Badges

- **Status:** full-radius pill, Label typography, `0.125rem 0.5rem` padding, tint background with same-hue dark ink and
  a mid-tone 1px border. Dark mode uses a 30% alpha of the deep hue with a lighter ink.
- **Permission / grant:** same geometry, no border, lower-saturation tint. The missing border is what separates a
  category from a state.

### Cards / Containers

- **Corner:** 9px radius.
- **Background:** white on light, `surface-dark` (slate-800) on dark.
- **Border:** 1px hairline, always.
- **Shadow:** none at rest — see Elevation.
- **Padding:** `1rem 1.5rem` for summary cards, `1rem`–`1.25rem` for dense panels.

### Inputs / Fields

- **Style:** white surface, gray-300 1px border, 4.5px radius, `0.375rem 0.75rem` padding at Body size.
- **Focus:** 12 fields shift only the 1px border to slate-500; 7 add a 2px ring. The ring is correct, the border-only
  treatment is the gap.
- **Disabled:** sunken background, faint ink.

### Navigation

- **Rail:** slate-800 with a slate-900 right edge. Items are 9px-radius rows at Body size with 18px stroked icons,
  `text-slate-200` at rest, `bg-slate-700` on hover, and **Gate Blue fill with white text when active**. Admin
  destinations sit under an uppercase `10.5px` slate-500 group label; collapsed, that label becomes a 1px divider.
  Not-yet-live destinations render in place, greyed, with a "Not available yet" tooltip rather than being hidden.
- **Breadcrumbs:** sticky translucent bar with backdrop blur; long record IDs truncate to an 8-character prefix plus
  ellipsis.

### Tables

- **Header:** sunken background, Label typography, uppercase with `0.025em` tracking, muted ink, 1px bottom hairline.
- **Rows:** divided by `divide-y` hairlines rather than borders on cells; hover shifts the row background.

### Toasts

Fixed top-right, 20rem wide, tinted by kind (red / green / blue) with a matching border, 9px radius, Floating shadow,
and a 150ms fade-in gated behind `motion-safe`. The only animation in the system.

## Do's and Don'ts

### Do:

- **Do** let status carry the color. A new record type gets a badge in the existing vocabulary before it gets a new hue.
- **Do** set machine-produced strings — SHAs, refs, paths, patterns, IDs — in the Evidence mono face at Label size.
- **Do** keep every page at `max-w-6xl` with a `px-6` gutter.
- **Do** separate surfaces with 1px hairlines and keep them flat at rest.
- **Do** state exact values in the interface where a reader may need to act on them: full timestamps, full refs, real
  counts.
- **Do** give every interactive element a visible focus indicator — a Gate Blue ring is the system's answer. WCAG 2.2 AA
  is a binding product requirement.
- **Do** match the neutral family already in use in the component you are editing, until the gray/slate question is
  settled.

### Don't:

- **Don't** let a 1px border-colour change be the whole focus indicator. Replacing the native outline is fine; replacing
  it with something thinner and fainter than the outline is not.
- **Don't** introduce a saturated color that isn't reporting state, marking a grant, or offering a decision.
- **Don't** reach past Body and Label type sizes unless the element is a page title or a number worth stopping at.
- **Don't** put a shadow on an in-page surface at rest.
- **Don't** add motion beyond color transitions and the toast fade. There is no entrance animation, no skeleton shimmer,
  and no scroll-triggered effect in this product, and that is a decision rather than an omission.
- **Don't** load a web font. The system UI stack is the intended voice.
- **Don't** theme the navigation rail. It is dark in both modes.
- **Don't** add decorative illustration, gradient, or glassmorphism. This interface is used to make consequential
  decisions under scrutiny; visual ambition that doesn't serve the decision is a cost, not a feature.
- **Don't** put a confirmation dialog in front of a reversible action. Apply it and offer Undo in the toast. A prompt
  everyone dismisses by reflex protects nothing and costs every user a click.
- **Don't** ship a control that only works with a pointer. The approval loop in particular is operable end to end from
  the keyboard.
- **Don't** grey out an interface for someone who will never be allowed to use it. State the absence of authority
  instead.
