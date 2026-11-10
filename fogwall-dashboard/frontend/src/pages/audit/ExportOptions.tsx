import type { ExportFormat } from './params'

export function FormatPicker({
  value,
  onChange,
}: {
  value: ExportFormat
  onChange: (format: ExportFormat) => void
}) {
  return (
    <div
      role="group"
      aria-label="Format"
      className="flex overflow-hidden rounded border border-gray-300 dark:border-slate-600"
    >
      {(['csv', 'json'] as const).map((f) => (
        <button
          key={f}
          type="button"
          onClick={() => onChange(f)}
          aria-pressed={value === f}
          className={
            'border-r border-gray-300 px-3 py-1 text-sm last:border-r-0 dark:border-slate-600 ' +
            (value === f
              ? 'bg-gray-900 text-white dark:bg-slate-100 dark:text-gray-900'
              : 'bg-white text-gray-600 hover:bg-gray-50 dark:bg-slate-800 dark:text-gray-300 dark:hover:bg-gray-700')
          }
        >
          {f === 'csv' ? 'CSV' : 'JSON Lines'}
        </button>
      ))}
    </div>
  )
}

/** Checkboxes over a type's CSV columns. JSON writes full records, so it has no columns to choose. */
export function ColumnPicker({
  all,
  chosen,
  onChange,
  label,
}: {
  all: string[]
  chosen: string[]
  onChange: (columns: string[]) => void
  label: string
}) {
  const toggle = (column: string) =>
    onChange(chosen.includes(column) ? chosen.filter((c) => c !== column) : [...chosen, column])
  return (
    <fieldset className="space-y-1.5">
      <legend className="flex w-full items-center gap-3 text-xs font-medium text-gray-500 dark:text-gray-400">
        <span>{label}</span>
        <button
          type="button"
          onClick={() => onChange(all)}
          className="text-blue-600 hover:underline dark:text-blue-400"
        >
          All
        </button>
        <button
          type="button"
          onClick={() => onChange([])}
          className="text-blue-600 hover:underline dark:text-blue-400"
        >
          None
        </button>
      </legend>
      <div className="flex flex-wrap gap-x-4 gap-y-1">
        {all.map((column) => (
          <label
            key={column}
            className="flex items-center gap-1.5 font-mono text-xs text-gray-700 dark:text-gray-300"
          >
            <input
              type="checkbox"
              checked={chosen.includes(column)}
              onChange={() => toggle(column)}
            />
            {column}
          </label>
        ))}
      </div>
    </fieldset>
  )
}
