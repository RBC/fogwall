import { useEffect, useState } from 'react'
import { commentIssue, createIssue, editIssue, fetchIssueProviders, setIssueState } from '../api'
import type { IssueResult } from '../api'
import { useToast } from '../components/Toast'
import { ExtIcon } from '../components/ExtIcon'

type Mode = 'create' | 'comment' | 'edit'

const MODES: { id: Mode; label: string; hint: string }[] = [
  { id: 'create', label: 'New issue', hint: 'Open an issue on a repository you may file against.' },
  { id: 'comment', label: 'Comment', hint: 'Add a comment to an existing issue by its number.' },
  {
    id: 'edit',
    label: 'Edit / Close',
    hint: 'Change the title or body of an existing issue, or close/reopen it.',
  },
]

const inputClass =
  'w-full rounded border border-gray-300 px-3 py-1.5 text-sm focus:border-slate-500 focus:outline-none dark:bg-slate-700 dark:border-slate-600 dark:text-gray-200'
const labelClass = 'block text-xs font-medium text-gray-600 mb-1 dark:text-gray-400'

// Per-viewer recent-values history for the owner/repo/number fields, surfaced as a native <datalist> dropdown so
// repeated filings don't mean retyping. Convenience only — every read/write is guarded, since localStorage can be
// unavailable (private windows, blocked site data) or throw.
const HISTORY_LIMIT = 10
type HistoryField = 'owner' | 'repo' | 'number'

function loadHistory(field: HistoryField): string[] {
  try {
    const raw = localStorage.getItem(`fogwall-issue-${field}-history`)
    return raw ? (JSON.parse(raw) as string[]) : []
  } catch {
    return []
  }
}

function pushHistory(field: HistoryField, value: string): string[] {
  const v = value.trim()
  if (!v) return loadHistory(field)
  const next = [v, ...loadHistory(field).filter((x) => x !== v)].slice(0, HISTORY_LIMIT)
  try {
    localStorage.setItem(`fogwall-issue-${field}-history`, JSON.stringify(next))
  } catch {
    // ignore — history is a convenience, not state we depend on
  }
  return next
}

export function Issues() {
  const toast = useToast()
  const [providers, setProviders] = useState<{ name: string }[]>([])
  const [loading, setLoading] = useState(true)
  const [provider, setProvider] = useState('')
  const [mode, setMode] = useState<Mode>('create')
  const [owner, setOwner] = useState('')
  const [repo, setRepo] = useState('')
  const [number, setNumber] = useState('')
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<IssueResult | null>(null)
  const [history, setHistory] = useState({
    owner: loadHistory('owner'),
    repo: loadHistory('repo'),
    number: loadHistory('number'),
  })

  // Issue numbers are digits only; flag junk input in the number-bearing modes.
  const numberValid = /^\d+$/.test(number.trim())
  const numberError = mode !== 'create' && number.trim() !== '' && !numberValid

  function remember(usedNumber: string) {
    setHistory({
      owner: pushHistory('owner', owner),
      repo: pushHistory('repo', repo),
      number: pushHistory('number', usedNumber),
    })
  }

  useEffect(() => {
    fetchIssueProviders()
      .then((list) => {
        setProviders(list)
        if (list.length > 0) setProvider(list[0].name)
      })
      .catch((err) =>
        toast.error(err instanceof Error ? err.message : 'Failed to load issue providers'),
      )
      .finally(() => setLoading(false))
  }, [toast])

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setSubmitting(true)
    setResult(null)
    try {
      let res: IssueResult
      if (mode === 'create') {
        res = await createIssue({
          provider,
          owner: owner.trim(),
          repo: repo.trim(),
          title: title.trim(),
          body,
        })
      } else if (mode === 'comment') {
        res = await commentIssue({
          provider,
          owner: owner.trim(),
          repo: repo.trim(),
          number: Number(number),
          body,
        })
      } else {
        res = await editIssue({
          provider,
          owner: owner.trim(),
          repo: repo.trim(),
          number: Number(number),
          title: title.trim() || undefined,
          body: body || undefined,
        })
      }
      setResult(res)
      toast.success(
        mode === 'create'
          ? 'Issue created'
          : mode === 'comment'
            ? 'Comment posted'
            : 'Issue updated',
      )
      if (mode === 'create') {
        // Carry the just-created issue forward: keep owner/repo, prefill its number so the Comment and Edit / Close
        // tabs act on it without retyping.
        setNumber(String(res.number))
        remember(String(res.number))
        setTitle('')
        setBody('')
      } else if (mode === 'comment') {
        remember(number)
        setBody('')
      } else {
        remember(number)
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'The issue operation failed')
    } finally {
      setSubmitting(false)
    }
  }

  async function handleSetState(close: boolean) {
    if (!owner.trim() || !repo.trim() || !number) {
      toast.error('Owner, repository, and issue number are required')
      return
    }
    setSubmitting(true)
    setResult(null)
    try {
      const res = await setIssueState({
        provider,
        owner: owner.trim(),
        repo: repo.trim(),
        number: Number(number),
        close,
      })
      setResult(res)
      remember(number)
      toast.success(close ? 'Issue closed' : 'Issue reopened')
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'The issue operation failed')
    } finally {
      setSubmitting(false)
    }
  }

  if (loading) {
    return (
      <div className="max-w-6xl px-6 py-16 text-center text-gray-400 dark:text-gray-500">
        Loading…
      </div>
    )
  }

  return (
    <div className="max-w-6xl px-6 py-8 space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-gray-800 dark:text-gray-200">Issues</h1>
        <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
          File and follow up on issues without a CLI or a personal token. fogwall acts on your
          behalf using the account you linked, and inspects the text before it leaves.
        </p>
      </div>

      {providers.length === 0 ? (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-200">
          No providers are available for issue filing. This needs a provider with issue filing
          enabled by an administrator, and your account linked to it under{' '}
          <a href="/dashboard/profile" className="font-medium underline">
            Profile
          </a>
          .
        </div>
      ) : (
        <form
          onSubmit={handleSubmit}
          className="max-w-2xl space-y-4 rounded-lg border border-gray-200 bg-white p-5 dark:border-slate-700 dark:bg-slate-800"
        >
          {/* Mode selector */}
          <div className="flex gap-1 rounded-lg border border-gray-200 p-1 dark:border-slate-600">
            {MODES.map((m) => (
              <button
                key={m.id}
                type="button"
                onClick={() => {
                  setMode(m.id)
                  setResult(null)
                }}
                className={`flex-1 rounded px-3 py-1.5 text-sm font-medium transition-colors ${
                  mode === m.id
                    ? 'bg-blue-600 text-white'
                    : 'text-gray-600 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-slate-700'
                }`}
              >
                {m.label}
              </button>
            ))}
          </div>
          <p className="text-xs text-gray-400 dark:text-gray-500">
            {MODES.find((m) => m.id === mode)?.hint}
          </p>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>Provider</label>
              <select
                required
                value={provider}
                onChange={(e) => setProvider(e.target.value)}
                className={`${inputClass} disabled:opacity-50`}
              >
                {providers.map((p) => (
                  <option key={p.name} value={p.name}>
                    {p.name}
                  </option>
                ))}
              </select>
            </div>
            {mode !== 'create' && (
              <div>
                <label className={labelClass}>Issue number</label>
                <input
                  required
                  type="text"
                  inputMode="numeric"
                  list="issue-number-history"
                  value={number}
                  onChange={(e) => setNumber(e.target.value)}
                  placeholder="123"
                  aria-invalid={numberError}
                  className={`${inputClass} ${numberError ? 'border-red-400 focus:border-red-500 dark:border-red-500' : ''}`}
                />
                <datalist id="issue-number-history">
                  {history.number.map((n) => (
                    <option key={n} value={n} />
                  ))}
                </datalist>
                {numberError && (
                  <p className="mt-1 text-xs text-red-600 dark:text-red-400">
                    Numbers only — no other characters.
                  </p>
                )}
              </div>
            )}
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className={labelClass}>Owner</label>
              <input
                required
                list="issue-owner-history"
                value={owner}
                onChange={(e) => setOwner(e.target.value)}
                placeholder="octocat"
                className={`${inputClass} font-mono`}
              />
              <datalist id="issue-owner-history">
                {history.owner.map((o) => (
                  <option key={o} value={o} />
                ))}
              </datalist>
            </div>
            <div>
              <label className={labelClass}>Repository</label>
              <input
                required
                list="issue-repo-history"
                value={repo}
                onChange={(e) => setRepo(e.target.value)}
                placeholder="hello-world"
                className={`${inputClass} font-mono`}
              />
              <datalist id="issue-repo-history">
                {history.repo.map((r) => (
                  <option key={r} value={r} />
                ))}
              </datalist>
            </div>
          </div>

          {mode !== 'comment' && (
            <div>
              <label className={labelClass}>
                Title{' '}
                {mode === 'edit' && <span className="text-gray-400">(leave blank to keep)</span>}
              </label>
              <input
                required={mode === 'create'}
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="Something is broken"
                className={inputClass}
              />
            </div>
          )}

          <div>
            <label className={labelClass}>
              {mode === 'comment' ? 'Comment' : 'Body'}{' '}
              {mode === 'edit' && <span className="text-gray-400">(leave blank to keep)</span>}
            </label>
            <textarea
              required={mode !== 'edit'}
              value={body}
              onChange={(e) => setBody(e.target.value)}
              rows={6}
              placeholder={mode === 'comment' ? 'Add your comment…' : 'Describe the issue…'}
              className={`${inputClass} resize-y`}
            />
          </div>

          {result && (
            <div className="rounded border border-green-300 bg-green-50 px-3 py-2 text-sm text-green-800 dark:border-green-700 dark:bg-green-900/30 dark:text-green-200">
              Done — issue #{result.number}
              {result.url && (
                <>
                  {' · '}
                  <a
                    href={result.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1 font-medium underline"
                  >
                    view on the provider
                    <ExtIcon />
                  </a>
                </>
              )}
            </div>
          )}

          <div className="flex items-center justify-end gap-2">
            {mode === 'edit' && (
              <>
                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => handleSetState(true)}
                  className="mr-auto rounded border border-red-300 px-3 py-2 text-sm font-medium text-red-700 hover:bg-red-50 disabled:opacity-50 dark:border-red-700 dark:text-red-400 dark:hover:bg-red-900/20"
                >
                  Close issue
                </button>
                <button
                  type="button"
                  disabled={submitting}
                  onClick={() => handleSetState(false)}
                  className="rounded border border-gray-300 px-3 py-2 text-sm font-medium text-gray-600 hover:bg-gray-50 disabled:opacity-50 dark:border-slate-600 dark:text-gray-300 dark:hover:bg-slate-700"
                >
                  Reopen
                </button>
              </>
            )}
            <button
              type="submit"
              disabled={submitting || (mode !== 'create' && !numberValid)}
              className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50"
            >
              {submitting
                ? 'Working…'
                : mode === 'create'
                  ? 'Create issue'
                  : mode === 'comment'
                    ? 'Post comment'
                    : 'Save changes'}
            </button>
          </div>
        </form>
      )}
    </div>
  )
}
