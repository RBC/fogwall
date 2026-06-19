import { Diff2HtmlUI } from 'diff2html/lib/ui/js/diff2html-ui-slim'
import 'diff2html/bundles/css/diff2html.min.css'
import { useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { fetchDiff, fetchPush } from '../api'

function diffStats(raw: string): { files: number; insertions: number; deletions: number } {
  const lines = raw.split('\n')
  const files = lines.filter((l) => l.startsWith('diff --git')).length
  const insertions = lines.filter((l) => l.startsWith('+')).length
  const deletions = lines.filter((l) => l.startsWith('-')).length
  return { files, insertions, deletions }
}

function ToggleButton({
  active,
  onClick,
  children,
}: {
  active: boolean
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      className={`px-2.5 py-1 rounded text-xs transition-colors ${
        active
          ? 'bg-slate-500 text-white'
          : 'bg-slate-700 text-slate-300 hover:bg-slate-600 hover:text-white'
      }`}
    >
      {children}
    </button>
  )
}

export function PushDiff() {
  const { id } = useParams<{ id: string }>()
  const diffRef = useRef<HTMLDivElement>(null)

  const [repoName, setRepoName] = useState<string | null>(null)
  const [stats, setStats] = useState<{
    files: number
    insertions: number
    deletions: number
  } | null>(null)
  const [loading, setLoading] = useState(true)
  const [rendering, setRendering] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [diffContent, setDiffContent] = useState<string | null>(null)

  // Render options — large diffs default to line-by-line with highlight off for fast initial load
  const [highlight, setHighlight] = useState(false)
  const [sideBySide, setSideBySide] = useState(false)

  // Fetch phase
  useEffect(() => {
    if (!id) return
    Promise.all([fetchPush(id), fetchDiff(id)])
      .then(([record, diff]) => {
        setRepoName(record.repoName ?? null)
        if (!diff.content) {
          setError('No diff available for this push.')
          return
        }
        setStats(diffStats(diff.content))
        setDiffContent(diff.content)
      })
      .catch((e) => setError(e instanceof Error ? e.message : 'Failed to load diff'))
      .finally(() => setLoading(false))
  }, [id])

  // Render phase — re-runs whenever content or display options change
  useEffect(() => {
    if (!diffContent || !diffRef.current) return
    setRendering(true)
    // Yield to the browser so the "Rendering…" indicator appears before the heavy work starts
    const timer = setTimeout(() => {
      if (!diffRef.current) return
      try {
        const ui = new Diff2HtmlUI(diffRef.current, diffContent, {
          drawFileList: true,
          matching: 'none',
          outputFormat: sideBySide ? 'side-by-side' : 'line-by-line',
          highlight,
        })
        ui.draw()
        if (highlight) ui.highlightCode()
      } catch {
        if (diffRef.current) {
          diffRef.current.innerHTML =
            '<pre class="text-xs text-gray-700 whitespace-pre-wrap overflow-x-auto p-4">' +
            diffContent.replace(/</g, '&lt;') +
            '</pre>'
        }
      } finally {
        setRendering(false)
      }
    }, 16)
    return () => clearTimeout(timer)
  }, [diffContent, highlight, sideBySide])

  return (
    <div className="min-h-screen flex flex-col bg-white">
      {/* Header */}
      <div className="bg-slate-800 text-white px-6 py-3 flex items-center gap-4 text-sm shrink-0 flex-wrap">
        <Link
          to={`/push/${id}`}
          className="text-slate-300 hover:text-white transition-colors flex items-center gap-1"
        >
          ← Back to push record
        </Link>
        {repoName && (
          <>
            <span className="text-slate-600">|</span>
            <span className="font-mono text-slate-300">{repoName}</span>
          </>
        )}
        {stats && (
          <>
            <span className="text-slate-600">|</span>
            <span className="text-slate-400">
              {stats.files} file{stats.files !== 1 ? 's' : ''}
            </span>
            <span className="text-green-400">+{stats.insertions}</span>
            <span className="text-red-400">-{stats.deletions}</span>
          </>
        )}

        {/* Render options — only shown once diff is loaded */}
        {diffContent && (
          <div className="ml-auto flex items-center gap-2">
            {rendering && <span className="text-xs text-slate-400 italic">Rendering…</span>}
            <ToggleButton active={sideBySide} onClick={() => setSideBySide((v) => !v)}>
              Side-by-side
            </ToggleButton>
            <ToggleButton active={highlight} onClick={() => setHighlight((v) => !v)}>
              Syntax highlight
            </ToggleButton>
          </div>
        )}
      </div>

      {/* Diff body */}
      <div className="flex-1 overflow-auto">
        {loading && (
          <div className="flex items-center justify-center h-64 text-gray-400 text-sm">
            Loading diff…
          </div>
        )}
        {error && (
          <div className="flex items-center justify-center h-64 text-red-500 text-sm">{error}</div>
        )}
        {!loading && !error && (
          <div className="p-4">
            <div ref={diffRef} className="text-sm" />
          </div>
        )}
      </div>
    </div>
  )
}
