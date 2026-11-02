export interface ParsedRepoUrl {
  host: string
  owner: string
  repo: string
  number?: string
}

// Reads a pasted web URL (https://host/owner/repo, .../issues/12, .../-/issues/12, .../pull/3), a clone URL
// (…/repo.git) or an scp-style SSH remote (git@host:owner/repo.git). GitLab-style nested groups stay in `owner`.
export function parseRepoUrl(input: string): ParsedRepoUrl | null {
  const raw = input.trim()
  if (!raw) return null
  let host: string
  let path: string
  const scp = /^[\w.-]+@([^:/\s]+):(.+)$/.exec(raw)
  if (scp) {
    host = scp[1]
    path = scp[2]
  } else {
    try {
      const u = new URL(/^[a-z][a-z0-9+.-]*:\/\//i.test(raw) ? raw : `https://${raw}`)
      host = u.hostname
      path = u.pathname
    } catch {
      return null
    }
  }
  let segs = path.split(/[?#]/)[0].split('/').filter(Boolean)
  let number: string | undefined
  const dash = segs.indexOf('-')
  if (dash >= 0) segs = [...segs.slice(0, dash), ...segs.slice(dash + 1)]
  const marker = segs.findIndex((x) => ['issues', 'pull', 'pulls', 'merge_requests'].includes(x))
  if (marker >= 0) {
    if (/^\d+$/.test(segs[marker + 1] ?? '')) number = segs[marker + 1]
    segs = segs.slice(0, marker)
  }
  if (segs.length < 2 || !host) return null
  segs[segs.length - 1] = segs[segs.length - 1].replace(/\.git$/, '')
  return {
    host: host.toLowerCase(),
    owner: segs.slice(0, -1).join('/'),
    repo: segs[segs.length - 1],
    number,
  }
}
