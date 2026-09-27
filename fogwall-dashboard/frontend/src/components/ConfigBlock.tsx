import { useState } from 'react'

/** A labelled, copyable block of text: a command, a config snippet, or a value shown once. */
export function ConfigBlock({ label, config }: { label: string; config: string }) {
  const [copied, setCopied] = useState(false)

  const copy = () => {
    navigator.clipboard.writeText(config).then(
      () => {
        setCopied(true)
        setTimeout(() => setCopied(false), 2000)
      },
      () => {
        /* clipboard blocked (e.g. insecure context) — leave the text selectable to copy by hand */
      },
    )
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-1">
        <span className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">
          {label}
        </span>
        <button
          onClick={copy}
          className="text-xs px-2 py-1 rounded bg-slate-100 hover:bg-slate-200 text-slate-700 dark:bg-slate-700 dark:hover:bg-slate-600 dark:text-slate-200 transition-colors"
          aria-label="Copy configuration to clipboard"
        >
          {copied ? 'Copied ✓' : 'Copy'}
        </button>
      </div>
      <pre className="overflow-x-auto rounded bg-slate-50 border border-gray-200 p-3 text-xs font-mono text-gray-800 dark:bg-slate-900 dark:border-slate-700 dark:text-gray-200">
        {config}
      </pre>
    </div>
  )
}
