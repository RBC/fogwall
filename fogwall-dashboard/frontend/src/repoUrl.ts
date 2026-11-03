export interface ParsedRepoUrl {
  host: string
  owner: string
  repo: string
  /** Issue number, from an issue link only. */
  number?: string
  /** The link pointed at a pull/merge request, whose number is not an issue number. */
  changeRequest: boolean
}

// Path segments that start a repository sub-page on GitHub, GitLab (after `/-/`) or Forgejo. Everything before the
// first one is the repository path.
const SUB_PAGES = new Set([
  'issues',
  'pull',
  'pulls',
  'merge_requests',
  'tree',
  'blob',
  'src',
  'commit',
  'commits',
  'compare',
  'releases',
  'tags',
  'branches',
  'wiki',
  'wikis',
  'actions',
  'settings',
  'labels',
  'milestones',
])
const CHANGE_REQUESTS = new Set(['pull', 'pulls', 'merge_requests'])
// GitLab links a project's issues as work items too; both share the issue number sequence.
const ISSUE_PAGES = new Set(['issues', 'work_items'])

// Reads a pasted web URL (https://host/owner/repo, .../issues/12, .../-/issues/12, .../-/work_items/12, .../pull/3), a clone URL
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
  let changeRequest = false
  const dash = segs.indexOf('-')
  // A sub-page segment only counts after owner/repo, so an owner or repo named e.g. "issues" still parses.
  const marker = dash >= 0 ? dash : segs.findIndex((x, i) => i >= 2 && SUB_PAGES.has(x))
  if (marker >= 0) {
    const page = dash >= 0 ? segs[dash + 1] : segs[marker]
    const next = dash >= 0 ? segs[dash + 2] : segs[marker + 1]
    if (ISSUE_PAGES.has(page ?? '') && /^\d+$/.test(next ?? '')) number = next
    changeRequest = CHANGE_REQUESTS.has(page ?? '')
    segs = segs.slice(0, marker)
  }
  if (segs.length < 2 || !host) return null
  segs[segs.length - 1] = segs[segs.length - 1].replace(/\.git$/, '')
  return {
    host: host.toLowerCase(),
    owner: segs.slice(0, -1).join('/'),
    repo: segs[segs.length - 1],
    number,
    changeRequest,
  }
}
