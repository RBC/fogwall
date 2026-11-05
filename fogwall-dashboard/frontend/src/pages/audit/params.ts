export type ExportFormat = 'csv' | 'json'

/** The format of an export, and for CSV the columns it writes, in their default order. */
export interface ExportChoice {
  format: ExportFormat
  columns: string[]
}

export type Values = Record<string, string>

/** A datetime-local value as the ISO-8601 instant the API takes. */
function toInstant(local: string): string {
  return new Date(local).toISOString()
}

/** The query for a set of filter values: blanks dropped, the time window converted to instants. */
export function filterParams(values: Values): URLSearchParams {
  const params = new URLSearchParams()
  for (const [name, value] of Object.entries(values)) {
    if (!value) continue
    params.set(name, name === 'from' || name === 'to' ? toInstant(value) : value)
  }
  return params
}

/** The columns parameter for a choice: absent when every column is written, which is the export's default. */
export function columnsParam(choice: ExportChoice, all: string[]): string | null {
  if (choice.format !== 'csv' || choice.columns.length === all.length) return null
  return all.filter((c) => choice.columns.includes(c)).join(',')
}

/** Starts a download of an export: the response is a zip attachment, so the page stays where it is. */
export function download(path: string, params: URLSearchParams) {
  const link = document.createElement('a')
  link.href = `${path}?${params}`
  link.rel = 'noopener'
  document.body.appendChild(link)
  link.click()
  link.remove()
}
