import { type FormEvent, useCallback, useEffect, useState } from 'react'
import { fetchRecordPage } from '../../api'
import { ColumnPicker, FormatPicker } from './ExportOptions'
import {
  columnsParam,
  download,
  type ExportChoice,
  type ExportFormat,
  filterParams,
  type Values,
} from './params'
import type { FilterDef, ListedType } from './recordTypes'
import { BUTTON, INPUT, PRIMARY_BUTTON } from './styles'
import type { Suggestions } from './useSuggestions'

const PAGE_SIZE = 50

export function ErrorBanner({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <div
      role="alert"
      className="rounded border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-300"
    >
      {message}
    </div>
  )
}

function optionLabel(value: string): string {
  return value.charAt(0) + value.slice(1).toLowerCase().replace(/_/g, ' ')
}

/** A fixed set as a select; current names as a select (strict) or as suggestions on a text field; otherwise text. */
function FilterField({
  def,
  listId,
  suggestions,
  value,
  onChange,
}: {
  def: FilterDef
  listId: string
  suggestions: Suggestions
  value: string
  onChange: (value: string) => void
}) {
  const known = def.suggest ? suggestions[def.suggest] : []
  const choices = def.options ?? (def.strict ? known : null)
  return (
    <label className="block text-xs font-medium text-gray-500 dark:text-gray-400">
      {def.label}
      {choices ? (
        <select
          value={value}
          onChange={(e) => onChange(e.target.value)}
          className={INPUT + ' mt-1'}
        >
          <option value="">Any</option>
          {choices.map((o) => (
            <option key={o} value={o}>
              {def.options ? optionLabel(o) : o}
            </option>
          ))}
        </select>
      ) : (
        <>
          <input
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder={def.placeholder}
            list={known.length > 0 ? listId : undefined}
            className={INPUT + ' mt-1'}
          />
          {known.length > 0 && (
            <datalist id={listId}>
              {known.map((o) => (
                <option key={o} value={o} />
              ))}
            </datalist>
          )}
        </>
      )}
    </label>
  )
}

const WINDOW = [
  { name: 'from', label: 'From' },
  { name: 'to', label: 'To (exclusive)' },
]

export function TimeWindow({
  values,
  onChange,
}: {
  values: Values
  onChange: (name: string, value: string) => void
}) {
  return (
    <>
      {WINDOW.map((def) => (
        <label
          key={def.name}
          className="block text-xs font-medium text-gray-500 dark:text-gray-400"
        >
          {def.label}
          <input
            type="datetime-local"
            value={values[def.name] ?? ''}
            onChange={(e) => onChange(def.name, e.target.value)}
            className={INPUT + ' mt-1'}
          />
        </label>
      ))}
    </>
  )
}

/** Format and columns for an export of one type, and the button that starts it. */
function ExportControls({
  columns,
  onExport,
}: {
  columns: string[]
  onExport: (choice: ExportChoice) => void
}) {
  const [format, setFormat] = useState<ExportFormat>('csv')
  // Null until the user picks: every column, whenever the column list arrives.
  const [picked, setPicked] = useState<string[] | null>(null)
  const [open, setOpen] = useState(false)
  const chosen = picked ?? columns
  const ready = format === 'json' || chosen.length > 0
  return (
    <div className="space-y-3 rounded-lg border border-gray-200 bg-white p-4 dark:border-slate-700 dark:bg-slate-800">
      <div className="flex flex-wrap items-center gap-3">
        <FormatPicker value={format} onChange={setFormat} />
        {format === 'csv' && columns.length > 0 && (
          <button type="button" onClick={() => setOpen((o) => !o)} className={BUTTON}>
            Columns ({chosen.length} of {columns.length})
          </button>
        )}
        <button
          type="button"
          disabled={!ready}
          onClick={() => onExport({ format, columns: chosen })}
          className={PRIMARY_BUTTON}
        >
          Download
        </button>
      </div>
      {open && format === 'csv' && (
        <ColumnPicker label="CSV columns" all={columns} chosen={chosen} onChange={setPicked} />
      )}
    </div>
  )
}

/**
 * One record type: its filters, a page of records, and an export of what the filters select. History pages by cursor
 * in time order; current state pages by offset and shows how many rows the filters select.
 */
export function RecordListing<T>({
  type,
  columns,
  suggestions,
  initial = {},
}: {
  type: ListedType<T>
  columns: string[]
  suggestions: Suggestions
  /** Filters the listing opens with, as a link into it carries them. */
  initial?: Values
}) {
  const [draft, setDraft] = useState<Values>(initial)
  const [applied, setApplied] = useState<Values>(initial)
  const [newestFirst, setNewestFirst] = useState(true)
  // cursors[i] is the `after` that reads page i of a history; page 0 reads from the start.
  const [cursors, setCursors] = useState<(string | null)[]>([null])
  const [page, setPage] = useState(0)
  const [rows, setRows] = useState<T[]>([])
  const [nextCursor, setNextCursor] = useState<string | null>(null)
  const [total, setTotal] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showExport, setShowExport] = useState(false)

  const query = useCallback(() => {
    const params = filterParams(applied)
    if (type.history) params.set('newestFirst', String(newestFirst))
    return params
  }, [applied, newestFirst, type.history])

  useEffect(() => {
    let live = true
    const params = query()
    params.set('limit', String(PAGE_SIZE))
    if (type.history) {
      const after = cursors[page]
      if (after) params.set('after', after)
    } else {
      params.set('offset', String(page * PAGE_SIZE))
    }
    void Promise.resolve()
      .then(() => {
        if (!live) return
        setLoading(true)
        return fetchRecordPage<T>(type.path, params)
      })
      .then((result) => {
        if (!live || !result) return
        setRows(result.rows)
        setNextCursor(result.nextCursor)
        setTotal(result.total)
        setError(null)
      })
      .catch((e: Error) => {
        if (!live) return
        setRows([])
        setNextCursor(null)
        setTotal(null)
        setError(e.message)
      })
      .finally(() => live && setLoading(false))
    return () => {
      live = false
    }
  }, [type.path, type.history, query, cursors, page])

  const hasNext = type.history
    ? nextCursor !== null
    : total !== null && (page + 1) * PAGE_SIZE < total

  function restart() {
    setCursors([null])
    setPage(0)
  }

  function apply(e: FormEvent) {
    e.preventDefault()
    setApplied(draft)
    restart()
  }

  function clear() {
    setDraft({})
    setApplied({})
    restart()
  }

  function next() {
    if (!hasNext) return
    if (type.history) setCursors((c) => [...c.slice(0, page + 1), nextCursor])
    setPage((p) => p + 1)
  }

  async function exportShown(choice: ExportChoice) {
    const params = query()
    // A refused filter would otherwise download the error; read one record with the same filters first.
    const probe = new URLSearchParams(params)
    probe.set('limit', '1')
    try {
      await fetchRecordPage(type.path, probe)
    } catch (e) {
      setError((e as Error).message)
      return
    }
    params.set('format', choice.format)
    const cols = columnsParam(choice, columns)
    if (cols) params.set('columns', cols)
    download(`${type.path}/export`, params)
  }

  const set = (name: string, value: string) => setDraft((d) => ({ ...d, [name]: value }))

  return (
    <div className="space-y-4">
      <form
        onSubmit={apply}
        aria-label={`${type.label} filters`}
        className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5"
      >
        {type.filters.map((def) => (
          <FilterField
            key={def.name}
            def={def}
            listId={`${type.name}-${def.name}`}
            suggestions={suggestions}
            value={draft[def.name] ?? ''}
            onChange={(v) => set(def.name, v)}
          />
        ))}
        {type.history && <TimeWindow values={draft} onChange={set} />}
        {type.hint && (
          <p className="col-span-full text-xs text-gray-500 dark:text-gray-400">{type.hint}</p>
        )}
        <div className="col-span-full flex flex-wrap items-center gap-2">
          <button type="submit" className={PRIMARY_BUTTON}>
            Apply filters
          </button>
          <button type="button" onClick={clear} className={BUTTON}>
            Clear
          </button>
          {type.history && (
            <button
              type="button"
              onClick={() => {
                setNewestFirst((v) => !v)
                restart()
              }}
              className={BUTTON}
            >
              {newestFirst ? '↓ Newest first' : '↑ Oldest first'}
            </button>
          )}
          {total !== null && (
            <span className="text-sm text-gray-500 dark:text-gray-400">
              {total} {total === 1 ? 'row' : 'rows'}
            </span>
          )}
          <button
            type="button"
            onClick={() => setShowExport((v) => !v)}
            aria-expanded={showExport}
            className={BUTTON + ' ml-auto'}
          >
            Export these records
          </button>
        </div>
      </form>

      {showExport && <ExportControls columns={columns} onExport={exportShown} />}

      <ErrorBanner message={error} />

      <div className="overflow-x-auto rounded-lg border border-gray-200 bg-white shadow-sm dark:border-slate-700 dark:bg-slate-800">
        <table className="min-w-full text-sm">
          <thead className="border-b border-gray-200 bg-gray-50 text-left text-xs font-medium uppercase tracking-wide text-gray-500 dark:border-slate-700 dark:bg-slate-900 dark:text-gray-400">
            <tr>
              {type.columns.map((c) => (
                <th key={c.header} scope="col" className="px-4 py-2 whitespace-nowrap">
                  {c.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 text-gray-800 dark:divide-slate-700 dark:text-gray-200">
            {rows.map((row) => (
              <tr key={type.rowKey(row)}>
                {type.columns.map((c) => (
                  <td key={c.header} className="px-4 py-2 align-top">
                    {c.cell(row)}
                  </td>
                ))}
              </tr>
            ))}
            {rows.length === 0 && !loading && !error && (
              <tr>
                <td
                  colSpan={type.columns.length}
                  className="px-4 py-12 text-center text-gray-400 dark:text-gray-500"
                >
                  No {type.label.toLowerCase()} match these filters.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {(page > 0 || hasNext) && (
        <div className="flex items-center justify-center gap-4">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className={BUTTON}
          >
            ← Previous
          </button>
          <span className="text-sm text-gray-500 dark:text-gray-400">Page {page + 1}</span>
          <button onClick={next} disabled={!hasNext} className={BUTTON}>
            Next →
          </button>
        </div>
      )}
    </div>
  )
}
