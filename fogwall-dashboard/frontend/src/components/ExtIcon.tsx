// The classic box-with-arrow external-link glyph — a clearer "opens off-site" cue than a bare ↗,
// which reads as decoration rather than a control.
export function ExtIcon() {
  return (
    <svg
      className="inline-block h-3.5 w-3.5 shrink-0"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={2.2}
      aria-hidden="true"
    >
      <path d="M14 5h5v5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M19 5l-8 8" strokeLinecap="round" />
      <path
        d="M18 14v4a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}
