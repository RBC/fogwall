import { useEffect, useRef, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Diff2HtmlUI } from 'diff2html/lib/ui/js/diff2html-ui-slim'
import { ColorSchemeType } from 'diff2html/lib/types'
import 'diff2html/bundles/css/diff2html.min.css'
import { approvePush, cancelPush, fetchDiff, fetchProviders, fetchPush, rejectPush } from '../api'
import { StatusBadge } from '../components/StatusBadge'
import type {
  AttestationLink,
  AttestationQuestion,
  CurrentUser,
  Provider,
  PushRecord,
  Step,
} from '../types'

// Steps that are infrastructure/pre-processing, not user-visible validation checks
const NON_VALIDATION_STEPS = new Set([
  'inspection',
  'diff',
  'diff:default-branch',
  'ForceGitClientFilter',
  'ParseGitRequestFilter',
  'EnrichPushCommitsFilter',
  'AllowApprovedPushFilter',
  'PushStoreAuditFilter',
  'AuditLogFilter',
  'ValidationSummaryFilter',
])

const STEP_DISPLAY_NAMES: Record<string, string> = {
  checkAuthorEmails: 'Author emails',
  AuthorEmail: 'Author emails',
  CheckAuthorEmailsFilter: 'Author emails',
  checkCommitMessages: 'Commit messages',
  CommitMessage: 'Commit messages',
  CheckCommitMessagesFilter: 'Commit messages',
  CheckEmptyBranchHook: 'Empty branch',
  CheckEmptyBranchFilter: 'Empty branch',
  CheckHiddenCommitsHook: 'Hidden commits',
  CheckHiddenCommitsFilter: 'Hidden commits',
  scanDiff: 'Diff scan',
  DiffContent: 'Diff scan',
  ScanDiffFilter: 'Diff scan',
  GpgSignatureFilter: 'GPG signatures',
  GpgSignatureHook: 'GPG signatures',
  scanSecrets: 'Secret scanning',
  SecretScanningFilter: 'Secret scanning',
  identityVerification: 'Identity verification',
  IdentityVerificationFilter: 'Identity verification',
  IdentityVerificationHook: 'Identity verification',
  CheckUserPushPermissionFilter: 'Push permissions',
  CheckUserPushPermissionHook: 'Push permissions',
  checkUrlRules: 'URL allow rules',
  UrlRuleAggregateFilter: 'URL allow rules',
  RepositoryUrlRuleHook: 'URL allow rules',
}

function IdentityBadge({ record }: { record: PushRecord }) {
  if (record.resolvedUser) {
    const idStep = (record.steps ?? []).find((s) => s.stepName === 'identityVerification')
    const hasEmailWarning = idStep?.status === 'WARN'
    if (hasEmailWarning) {
      return (
        <span
          className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-medium text-amber-700 dark:bg-amber-900/30 dark:text-amber-300"
          title={idStep?.content ?? undefined}
        >
          ⚠ identity resolved, email unregistered
        </span>
      )
    }
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-green-100 px-2 py-0.5 text-xs font-medium text-green-700 dark:bg-green-900/30 dark:text-green-300">
        ✓ identity resolved
      </span>
    )
  }
  if (record.user) {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-500 dark:bg-slate-700 dark:text-gray-400">
        identity unresolved
      </span>
    )
  }
  return null
}

function stepDisplayName(name: string): string {
  return (
    STEP_DISPLAY_NAMES[name] ??
    name
      .replace(/Filter$|Hook$/, '')
      .replace(/([A-Z])/g, ' $1')
      .trim()
      .replace(/^./, (c) => c.toUpperCase())
  )
}

function formatTime(ts: string | number | undefined) {
  if (!ts) return ''
  try {
    return new Date(ts).toLocaleString()
  } catch {
    return String(ts)
  }
}

/**
 * Fallback heuristic used only when the backend-computed `commitUrl` is unavailable (e.g. provider no longer
 * resolvable). Returns undefined for anything other than an http(s) URL — upstreamUrl can be an ssh:// transport URL
 * (SSH-forwarded pushes) and must never be rendered as a clickable link.
 */
function upstreamCommitUrl(upstreamUrl: string, sha: string): string | undefined {
  if (!/^https?:\/\//.test(upstreamUrl)) return undefined
  const base = upstreamUrl.replace(/\/$/, '').replace(/\.git$/, '')
  if (/gitlab\./.test(base)) return `${base}/-/commit/${sha}`
  return `${base}/commit/${sha}`
}

const FORWARDING_STEPS = new Set(['ForwardingPostReceiveHook', 'forward', 'ForwardingHook'])

function PushTimeline({ record }: { record: PushRecord }) {
  type TimelineEvent = {
    icon: string
    label: string
    detail?: string
    link?: string
    time?: string | number
    color: string
  }

  const events: TimelineEvent[] = []

  events.push({
    icon: '↓',
    label: 'Push received',
    time: record.timestamp,
    color: 'text-gray-500 dark:text-gray-400',
  })

  const visibleSteps = (record.steps ?? []).filter((s) => !NON_VALIDATION_STEPS.has(s.stepName))
  if (visibleSteps.length > 0) {
    const failed = visibleSteps.filter((s) => s.status === 'FAIL' || s.status === 'BLOCKED')
    const lastStep = [...visibleSteps].sort((a, b) => a.stepOrder - b.stepOrder).at(-1)
    events.push({
      icon: failed.length > 0 ? '✗' : '✓',
      label:
        failed.length > 0
          ? `Validation failed (${failed.length} issue${failed.length > 1 ? 's' : ''})`
          : `Validation passed (${visibleSteps.length} check${visibleSteps.length > 1 ? 's' : ''})`,
      detail: failed.map((s) => stepDisplayName(s.stepName)).join(', ') || undefined,
      time: lastStep?.timestamp,
      color:
        failed.length > 0 ? 'text-red-500 dark:text-red-400' : 'text-green-500 dark:text-green-400',
    })
  }

  const wasBlocked = record.status === 'PENDING' || record.attestation != null
  if (wasBlocked) {
    events.push({
      icon: '⏸',
      label: 'Pending review',
      detail: record.blockedMessage ?? undefined,
      time: record.timestamp,
      color: 'text-amber-500 dark:text-amber-400',
    })
  }

  if (record.attestation) {
    const att = record.attestation
    const typeLabel =
      att.type === 'APPROVAL' ? 'Approved' : att.type === 'REJECTION' ? 'Rejected' : 'Canceled'
    const overrideNote = att.selfApproval
      ? ' [admin self-approval override]'
      : att.type === 'APPROVAL' && att.reviewerUsername === record.resolvedUser
        ? ' [self certified]'
        : ''
    const answerLines =
      att.answers && Object.keys(att.answers).length > 0
        ? Object.entries(att.answers)
            .map(([k, v]) => `${k}: ${v}`)
            .join('\n')
        : undefined
    const detail = [att.reason, answerLines].filter(Boolean).join('\n') || undefined
    events.push({
      icon: att.type === 'APPROVAL' ? '✓' : att.type === 'REJECTION' ? '✗' : '○',
      label: `${typeLabel} by ${att.reviewerUsername}${att.reviewerEmail ? ` (${att.reviewerEmail})` : ''}${overrideNote}`,
      detail,
      time: att.timestamp,
      color:
        att.type === 'APPROVAL'
          ? 'text-green-600 dark:text-green-400'
          : att.type === 'REJECTION'
            ? 'text-red-600 dark:text-red-400'
            : 'text-gray-400 dark:text-gray-500',
    })
  }

  const forwardStep = (record.steps ?? []).find((s) => FORWARDING_STEPS.has(s.stepName))
  if (record.status === 'FORWARDED') {
    const commitLink =
      record.commitUrl ??
      (record.upstreamUrl && record.commitTo
        ? upstreamCommitUrl(record.upstreamUrl, record.commitTo)
        : undefined)
    events.push({
      icon: '→',
      label: 'Forwarded to upstream',
      detail: record.upstreamUrl ?? undefined,
      link: commitLink,
      time: forwardStep?.timestamp,
      color: 'text-blue-500 dark:text-blue-400',
    })
  }

  if (record.status === 'ERROR') {
    events.push({
      icon: '!',
      label: 'Error',
      detail: record.errorMessage ?? undefined,
      time: record.timestamp,
      color: 'text-red-600 dark:text-red-400',
    })
  }

  return (
    <div className="bg-white rounded-lg shadow border border-gray-200 px-6 py-4 dark:bg-slate-800 dark:border-slate-700">
      <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-4 dark:text-gray-400">
        Timeline
      </h2>
      <ol className="relative border-l border-gray-200 ml-2 space-y-4 dark:border-slate-700">
        {events.map((ev, i) => (
          <li key={i} className="ml-5">
            <span
              className={`absolute -left-2.5 flex h-5 w-5 items-center justify-center rounded-full bg-white border border-gray-200 text-xs font-bold dark:bg-slate-800 dark:border-slate-700 ${ev.color}`}
            >
              {ev.icon}
            </span>
            <div className="text-sm text-gray-800 font-medium leading-snug dark:text-gray-200">
              {ev.label}
            </div>
            {ev.detail && (
              <div className="text-xs text-gray-500 mt-0.5 font-mono dark:text-gray-400">
                {ev.detail}
              </div>
            )}
            {ev.link && (
              <a
                href={ev.link}
                target="_blank"
                rel="noopener noreferrer"
                className="text-xs text-blue-600 hover:underline mt-0.5 block dark:text-blue-400"
              >
                View commit ↗
              </a>
            )}
            {ev.time && (
              <div className="text-xs text-gray-400 mt-0.5 dark:text-gray-500">
                {formatTime(ev.time)}
              </div>
            )}
          </li>
        ))}
      </ol>
    </div>
  )
}

function AttestationQuestionField({
  question,
  value,
  disabled,
  onChange,
}: {
  question: AttestationQuestion
  value: string
  disabled: boolean
  onChange: (val: string) => void
}) {
  const labelEl = (
    <span className="text-sm text-gray-700 dark:text-gray-300">
      {question.label}
      {question.required && <span className="ml-1 text-red-500">*</span>}
    </span>
  )

  const linksEl = question.links && question.links.length > 0 && (
    <div className="flex flex-wrap gap-x-3 gap-y-0.5 mt-0.5 ml-6">
      {question.links.map((link: AttestationLink) => (
        <a
          key={link.url}
          href={link.url}
          target="_blank"
          rel="noopener noreferrer"
          className="text-xs text-blue-500 hover:underline dark:text-blue-400"
        >
          {link.text} ↗
        </a>
      ))}
    </div>
  )

  if (question.type === 'checkbox') {
    return (
      <div>
        <label className="flex items-start gap-2 cursor-pointer">
          <input
            type="checkbox"
            checked={value === 'true'}
            disabled={disabled}
            onChange={(e) => onChange(e.target.checked ? 'true' : 'false')}
            className="mt-0.5 h-4 w-4 rounded border-gray-300 text-blue-600 focus:ring-blue-500 disabled:opacity-50 dark:border-slate-600"
          />
          {labelEl}
        </label>
        {linksEl}
      </div>
    )
  }

  if (question.type === 'dropdown') {
    return (
      <div className="flex flex-col gap-1">
        {labelEl}
        <select
          value={value}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value)}
          className="border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50 disabled:text-gray-400 dark:bg-slate-700 dark:border-slate-600 dark:text-gray-200 dark:disabled:bg-gray-800 dark:disabled:text-gray-500"
        >
          <option value="">— select —</option>
          {(question.options ?? []).map((opt) => (
            <option key={opt} value={opt}>
              {opt}
            </option>
          ))}
        </select>
        {linksEl}
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-1">
      {labelEl}
      <input
        type="text"
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
        placeholder=""
        className="border border-gray-300 rounded px-2 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50 disabled:text-gray-400 dark:bg-slate-700 dark:border-slate-600 dark:text-gray-200 dark:disabled:bg-gray-800 dark:disabled:text-gray-500"
      />
      {linksEl}
    </div>
  )
}

function RePushGuidance({ record }: { record: PushRecord }) {
  const branch = record.branch?.replace('refs/heads/', '') ?? '<branch>'

  if (record.status === 'APPROVED') {
    return (
      <div className="bg-blue-50 border border-blue-200 rounded-lg px-6 py-4 dark:bg-blue-900/20 dark:border-blue-700">
        <div className="text-sm font-semibold text-blue-800 mb-1 dark:text-blue-300">
          Push approved — re-push to forward
        </div>
        <p className="text-sm text-blue-700 mb-3 dark:text-blue-400">
          This push has been approved. Push the same commits again to forward them upstream.
        </p>
        <pre className="text-xs bg-white border border-blue-200 rounded px-3 py-2 font-mono text-blue-900 select-all dark:bg-slate-900 dark:border-blue-700 dark:text-blue-300">
          git push origin {branch}
        </pre>
      </div>
    )
  }

  const att = record.attestation
  const reviewer = att?.reviewerUsername ?? 'a reviewer'
  const reason = att?.reason

  if (record.status === 'CANCELED') {
    return (
      <div className="bg-gray-50 border border-gray-200 rounded-lg px-6 py-4 dark:bg-slate-800 dark:border-slate-700">
        <div className="text-sm font-semibold text-gray-700 mb-1 dark:text-gray-300">
          Push canceled
        </div>
        <p className="text-sm text-gray-600 mb-3 dark:text-gray-400">
          <strong>{reviewer}</strong> canceled this push before review
          {reason ? (
            <>
              : <em>"{reason}"</em>
            </>
          ) : (
            '.'
          )}{' '}
          The commits themselves are fine — re-push when ready.
        </p>
        <pre className="text-xs bg-white border border-gray-200 rounded px-3 py-2 font-mono text-gray-800 select-all dark:bg-slate-900 dark:border-slate-700 dark:text-gray-300">
          git push origin {branch}
        </pre>
      </div>
    )
  }

  const isReviewerRejected = att && att.type === 'REJECTION'

  if (isReviewerRejected) {
    return (
      <div className="bg-red-50 border border-red-200 rounded-lg px-6 py-4 dark:bg-red-900/20 dark:border-red-700">
        <div className="text-sm font-semibold text-red-800 mb-1 dark:text-red-300">
          Push rejected — action required
        </div>
        <p className="text-sm text-red-700 mb-1 dark:text-red-400">
          <strong>{reviewer}</strong> rejected this push
          {reason ? (
            <>
              : <em>"{reason}"</em>
            </>
          ) : (
            '.'
          )}
        </p>
        <p className="text-sm text-red-700 mb-3 dark:text-red-400">
          Address the feedback, amend your commits, and push again.
        </p>
        <pre className="text-xs bg-white border border-red-200 rounded px-3 py-2 font-mono text-red-900 select-all whitespace-pre-wrap dark:bg-slate-900 dark:border-red-700 dark:text-red-300">{`# Amend the last commit and re-push
git commit --amend
git push origin ${branch}

# Or delete the remote branch
git push origin :${branch}`}</pre>
      </div>
    )
  }

  return (
    <div className="bg-red-50 border border-red-200 rounded-lg px-6 py-4 dark:bg-red-900/20 dark:border-red-700">
      <div className="text-sm font-semibold text-red-800 mb-1 dark:text-red-300">
        Push blocked by validation — action required
      </div>
      <p className="text-sm text-red-700 mb-3 dark:text-red-400">
        Your push was blocked by an automated check. Review the validation results above, fix the
        issues in your commits, and re-push.
      </p>
      <pre className="text-xs bg-white border border-red-200 rounded px-3 py-2 font-mono text-red-900 select-all whitespace-pre-wrap dark:bg-slate-900 dark:border-red-700 dark:text-red-300">{`# Fix the issues, amend the last commit, and re-push
git commit --amend
git push origin ${branch}

# Or delete the remote branch
git push origin :${branch}`}</pre>
    </div>
  )
}

interface PushDetailProps {
  currentUser: CurrentUser | null
  dark?: boolean
}

const DIFF_INLINE_THRESHOLD = 1000

export function PushDetail({ currentUser, dark = false }: PushDetailProps) {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const diffRef = useRef<HTMLDivElement>(null)

  const [record, setRecord] = useState<PushRecord | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [reviewReason, setReviewReason] = useState('')
  const [saving, setSaving] = useState(false)
  const [actionError, setActionError] = useState('')
  const [openSteps, setOpenSteps] = useState<Record<string, boolean>>({})
  const [canceling, setCanceling] = useState(false)
  const [attestationQuestions, setAttestationQuestions] = useState<AttestationQuestion[]>([])
  const [attestationAnswers, setAttestationAnswers] = useState<Record<string, string>>({})
  const [adminOverrideEnabled, setAdminOverrideEnabled] = useState(false)

  const [diffContent, setDiffContent] = useState<string | null>(null)
  const [diffLoading, setDiffLoading] = useState(false)
  const [diffLines, setDiffLines] = useState(0)
  const [diffRendering, setDiffRendering] = useState(false)
  const [diffHighlight, setDiffHighlight] = useState(true)
  const [diffSideBySide, setDiffSideBySide] = useState(true)

  const COMMITS_PAGE = 20
  const [showAllCommits, setShowAllCommits] = useState(false)

  async function load(pushId: string) {
    setLoading(true)
    setError('')
    setRecord(null)
    setReviewReason('')
    setActionError('')
    setOpenSteps({})
    setAttestationAnswers({})
    setAdminOverrideEnabled(false)
    setDiffContent(null)
    setDiffLines(0)
    try {
      const [data, providerList] = await Promise.all([fetchPush(pushId), fetchProviders()])
      setRecord(data)
      const provider = (providerList as Provider[]).find((p) => p.id === data.provider)
      setAttestationQuestions(provider?.attestationQuestions ?? [])
    } catch (e) {
      setError(String(e))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    if (id) void Promise.resolve().then(() => load(id))
  }, [id])

  useEffect(() => {
    if (!id || !record) return
    void Promise.resolve().then(() => {
      setDiffLoading(true)
      fetchDiff(id)
        .then((d) => {
          setDiffContent(d.content ?? null)
          setDiffLines(d.content ? d.content.split('\n').length : 0)
        })
        .catch(() => setDiffContent(null))
        .finally(() => setDiffLoading(false))
    })
  }, [id, record])

  useEffect(() => {
    if (!diffContent || diffLines >= DIFF_INLINE_THRESHOLD || !diffRef.current) return
    setDiffRendering(true)
    const timer = setTimeout(() => {
      if (!diffRef.current) return
      try {
        const ui = new Diff2HtmlUI(diffRef.current, diffContent, {
          drawFileList: true,
          matching: 'lines',
          outputFormat: diffSideBySide ? 'side-by-side' : 'line-by-line',
          highlight: diffHighlight,
          colorScheme: dark ? ColorSchemeType.DARK : ColorSchemeType.LIGHT,
        })
        ui.draw()
        if (diffHighlight) ui.highlightCode()
      } catch {
        if (diffRef.current) {
          diffRef.current.innerHTML =
            '<pre class="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-wrap overflow-x-auto">' +
            diffContent.replace(/</g, '&lt;') +
            '</pre>'
        }
      } finally {
        setDiffRendering(false)
      }
    }, 16)
    return () => clearTimeout(timer)
  }, [diffContent, diffLines, diffHighlight, diffSideBySide, dark])

  const validationSteps: Step[] = (record?.steps ?? [])
    .filter((s) => !NON_VALIDATION_STEPS.has(s.stepName))
    .sort((a, b) => a.stepOrder - b.stepOrder)

  function errorMessage(e: unknown): string {
    return e instanceof Error ? e.message : String(e)
  }

  async function handleApprove() {
    if (!record || !reviewReason.trim()) return
    setSaving(true)
    setActionError('')
    try {
      await approvePush(record.id, {
        reviewerUsername: currentUser?.username ?? '',
        reviewerEmail: currentUser?.emails[0]?.email ?? '',
        reason: reviewReason,
        attestations: attestationQuestions.length > 0 ? attestationAnswers : undefined,
        adminOverride: adminOverrideEnabled || undefined,
      })
      await load(record.id)
    } catch (e) {
      setActionError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  async function handleReject() {
    if (!record || !reviewReason.trim()) return
    setSaving(true)
    setActionError('')
    try {
      await rejectPush(record.id, {
        reviewerUsername: currentUser?.username ?? '',
        reviewerEmail: currentUser?.emails[0]?.email ?? '',
        reason: reviewReason,
      })
      await load(record.id)
    } catch (e) {
      setActionError(errorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  async function handleCancel() {
    if (!record) return
    setCanceling(true)
    setActionError('')
    try {
      await cancelPush(record.id)
      await load(record.id)
    } catch (e) {
      setActionError(errorMessage(e))
    } finally {
      setCanceling(false)
    }
  }

  return (
    <div className="max-w-5xl mx-auto px-4 py-6 space-y-6">
      <button
        onClick={() => navigate('/')}
        className="text-sm text-blue-600 hover:underline dark:text-blue-400"
      >
        ← Back to push records
      </button>

      {loading && (
        <div className="text-center text-gray-400 py-16 dark:text-gray-500">Loading…</div>
      )}
      {error && <div className="text-red-600 py-8 text-center dark:text-red-400">{error}</div>}

      {record && !loading && (
        <>
          {/* Header card */}
          <div className="bg-white rounded-lg shadow border border-gray-200 px-6 py-4 dark:bg-slate-800 dark:border-slate-700">
            <div className="flex items-start gap-4">
              <StatusBadge status={record.status} className="mt-1" />

              <div className="flex-1 min-w-0 space-y-0.5">
                <div className="font-mono text-sm text-gray-900 truncate dark:text-gray-100">
                  {record.repoUrl ??
                    record.upstreamUrl ??
                    record.url ??
                    (record.project ?? '') + '/' + (record.repoName ?? '')}
                  {record.repoUrl && (
                    <a
                      href={record.repoUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      title="Open repo"
                      className="ml-1.5 text-blue-500 no-underline hover:text-blue-600 dark:text-blue-400 dark:hover:text-blue-300"
                    >
                      ↗
                    </a>
                  )}
                </div>
                <div className="text-xs text-gray-500 font-mono dark:text-gray-400">
                  {record.branch}
                </div>
                <div className="text-xs font-mono text-gray-400 break-all dark:text-gray-500">
                  {record.commitTo}
                  {record.commitUrl && (
                    <a
                      href={record.commitUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      title="Open commit"
                      className="ml-1.5 text-blue-500 no-underline hover:text-blue-600 dark:text-blue-400 dark:hover:text-blue-300"
                    >
                      ↗
                    </a>
                  )}
                </div>
                {record.message && (
                  <div className="text-xs text-gray-600 italic pt-0.5 dark:text-gray-400">
                    {record.message}
                  </div>
                )}
              </div>

              <div className="text-right text-xs text-gray-500 shrink-0 space-y-1 dark:text-gray-400">
                {record.author && (
                  <div>
                    <span className="text-gray-400 dark:text-gray-500">author: </span>
                    <span className="text-gray-600 dark:text-gray-300">{record.author}</span>
                  </div>
                )}
                {record.committer && record.committer !== record.author && (
                  <div>
                    <span className="text-gray-400 dark:text-gray-500">committer: </span>
                    <span className="text-gray-600 dark:text-gray-300">{record.committer}</span>
                  </div>
                )}
                {(() => {
                  try {
                    const upstreamHost = record.upstreamUrl
                      ? new URL(record.upstreamUrl).hostname
                      : null
                    const displayHandle =
                      record.scmUsername ?? (record.resolvedUser ? null : record.user)
                    if (!displayHandle && !record.resolvedUser) return null
                    return (
                      <div className="space-y-0.5">
                        {displayHandle && (
                          <div className="flex items-center justify-end gap-1">
                            <span className="text-gray-400 dark:text-gray-500">pusher</span>
                            {upstreamHost && record.scmUsername && (
                              <img
                                src={`https://${upstreamHost}/favicon.ico`}
                                className="w-3.5 h-3.5"
                                onError={(e) => {
                                  e.currentTarget.style.display = 'none'
                                }}
                              />
                            )}
                            {record.scmUsername && upstreamHost ? (
                              <a
                                href={`https://${upstreamHost}/${record.scmUsername}`}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="text-blue-600 hover:underline font-medium dark:text-blue-400"
                              >
                                {record.scmUsername}
                              </a>
                            ) : (
                              <span className="text-gray-600 dark:text-gray-300">
                                {displayHandle}
                              </span>
                            )}
                          </div>
                        )}
                        {record.resolvedUser && record.resolvedUser !== record.scmUsername && (
                          <div className="text-gray-400 dark:text-gray-500">
                            user: {record.resolvedUser}
                          </div>
                        )}
                      </div>
                    )
                  } catch {
                    // URL parsing failed
                  }
                  return null
                })()}
                <IdentityBadge record={record} />
                <div className="text-gray-400 dark:text-gray-500">
                  {formatTime(record.timestamp)}
                </div>
              </div>
            </div>
            {record.blockedMessage && (
              <div className="mt-3 flex gap-2 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded px-3 py-2 dark:bg-amber-900/20 dark:border-amber-700 dark:text-amber-300">
                <span>⚠️</span>
                <span>{record.blockedMessage}</span>
              </div>
            )}
            {record.attestation && (
              <div className="mt-3 text-sm text-gray-500 border-t border-gray-100 pt-2 dark:text-gray-400 dark:border-slate-700">
                Reviewed by <strong>{record.attestation.reviewerUsername}</strong>
                {record.attestation.reviewerEmail && ` (${record.attestation.reviewerEmail})`}
                {record.attestation.reason && ` · "${record.attestation.reason}"`}
              </div>
            )}
          </div>

          {/* Push timeline */}
          <PushTimeline record={record} />

          {/* Commits */}
          {record.commits && record.commits.length > 0 && (
            <div className="bg-white rounded-lg shadow border border-gray-200 px-6 py-4 dark:bg-slate-800 dark:border-slate-700">
              <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3 dark:text-gray-400">
                Commits ({record.commits.length})
              </h2>
              <div className="space-y-2">
                {(showAllCommits ? record.commits : record.commits.slice(0, COMMITS_PAGE)).map(
                  (c) => (
                    <div
                      key={c.sha}
                      className="border border-gray-100 rounded p-3 space-y-1 dark:border-slate-700"
                    >
                      <div className="font-mono text-xs text-gray-400 dark:text-gray-500">
                        {c.sha}
                      </div>
                      <div className="text-sm text-gray-800 whitespace-pre-wrap dark:text-gray-200">
                        {(c.message ?? '').split('\n')[0].trim()}
                      </div>
                      <div className="text-xs text-gray-500 dark:text-gray-400">
                        <span className="font-medium">Author: </span>
                        {c.authorName} &lt;{c.authorEmail}&gt;
                      </div>
                      {c.committerName &&
                        (c.committerName !== c.authorName ||
                          c.committerEmail !== c.authorEmail) && (
                          <div className="text-xs text-gray-500 dark:text-gray-400">
                            <span className="font-medium">Committer: </span>
                            {c.committerName} &lt;{c.committerEmail}&gt;
                          </div>
                        )}
                      {c.signedOffBy && c.signedOffBy.length > 0 && (
                        <div className="text-xs text-gray-500 dark:text-gray-400">
                          <span className="font-medium">Signed-off-by: </span>
                          {c.signedOffBy.map((sob) => (
                            <span
                              key={sob}
                              className="ml-1 bg-green-50 text-green-700 border border-green-200 rounded px-1 dark:bg-green-900/20 dark:text-green-300 dark:border-green-700"
                            >
                              {sob}
                            </span>
                          ))}
                        </div>
                      )}
                    </div>
                  ),
                )}
              </div>
              {record.commits.length > COMMITS_PAGE && (
                <button
                  onClick={() => setShowAllCommits((v) => !v)}
                  className="mt-3 text-xs text-slate-500 hover:text-slate-700 transition-colors dark:text-slate-400 dark:hover:text-slate-300"
                >
                  {showAllCommits
                    ? '▲ Show fewer'
                    : `▼ Show ${record.commits.length - COMMITS_PAGE} more commit${record.commits.length - COMMITS_PAGE !== 1 ? 's' : ''}`}
                </button>
              )}
            </div>
          )}

          {/* Validation steps */}
          {validationSteps.length > 0 && (
            <div className="bg-white rounded-lg shadow border border-gray-200 px-6 py-4 dark:bg-slate-800 dark:border-slate-700">
              <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3 dark:text-gray-400">
                Validation steps
              </h2>
              <div className="space-y-1">
                {validationSteps.map((s) => {
                  const isFailed = s.status === 'FAIL' || s.status === 'BLOCKED'
                  const isWarn = s.status === 'WARN'
                  const isSkipped = s.status === 'SKIPPED'
                  const isOpen = openSteps[s.id]
                  return (
                    <div key={s.id}>
                      <div
                        className="flex gap-3 items-baseline text-sm cursor-pointer"
                        onClick={() => setOpenSteps((prev) => ({ ...prev, [s.id]: !prev[s.id] }))}
                      >
                        <span
                          className={
                            s.status === 'PASS'
                              ? 'text-green-500 dark:text-green-400'
                              : isFailed
                                ? 'text-red-500 dark:text-red-400'
                                : isWarn
                                  ? 'text-amber-500 dark:text-amber-400'
                                  : isSkipped
                                    ? 'text-yellow-500 dark:text-yellow-400'
                                    : 'text-gray-400 dark:text-gray-500'
                          }
                        >
                          {s.status === 'PASS'
                            ? '✓'
                            : isFailed
                              ? '✗'
                              : isWarn || isSkipped
                                ? '⚠'
                                : '–'}
                        </span>
                        <span className="text-sm text-gray-700 w-56 shrink-0 dark:text-gray-300">
                          {stepDisplayName(s.stepName)}
                        </span>
                        <span className="text-gray-500 text-xs truncate flex-1 dark:text-gray-400">
                          {s.errorMessage ??
                            s.blockedMessage ??
                            (isWarn ? s.content : undefined) ??
                            (isSkipped ? 'skipped' : '')}
                        </span>
                        {(isFailed || isWarn || isSkipped) &&
                          (s.content || s.errorMessage || s.blockedMessage) && (
                            <span className="text-xs text-gray-400 shrink-0 dark:text-gray-500">
                              {isOpen ? '▲ hide' : '▼ details'}
                            </span>
                          )}
                      </div>
                      {isOpen && (s.content || s.errorMessage || s.blockedMessage) && (
                        <pre className="mt-2 ml-6 text-xs bg-gray-50 border border-gray-200 rounded p-3 whitespace-pre-wrap font-mono text-gray-800 overflow-x-auto dark:bg-slate-900 dark:border-slate-700 dark:text-gray-200">
                          {[s.errorMessage ?? s.blockedMessage, s.content]
                            .filter(Boolean)
                            .join('\n\n')}
                        </pre>
                      )}
                    </div>
                  )
                })}
              </div>
            </div>
          )}

          {/* Diff */}
          <div className="bg-white rounded-lg shadow border border-gray-200 px-6 py-4 dark:bg-slate-800 dark:border-slate-700">
            <div className="flex items-center gap-3 mb-3">
              <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide dark:text-gray-400">
                Diff
              </h2>
              {!diffLoading && diffContent && diffLines < DIFF_INLINE_THRESHOLD && (
                <div className="flex items-center gap-2 ml-auto">
                  {diffRendering && (
                    <span className="text-xs text-gray-400 italic dark:text-gray-500">
                      Rendering…
                    </span>
                  )}
                  <button
                    onClick={() => setDiffSideBySide((v) => !v)}
                    className={`px-2.5 py-1 rounded text-xs transition-colors ${
                      diffSideBySide
                        ? 'bg-slate-600 text-white'
                        : 'bg-gray-100 text-gray-500 hover:bg-gray-200 dark:bg-slate-700 dark:text-gray-400 dark:hover:bg-gray-600'
                    }`}
                  >
                    Side-by-side
                  </button>
                  <button
                    onClick={() => setDiffHighlight((v) => !v)}
                    className={`px-2.5 py-1 rounded text-xs transition-colors ${
                      diffHighlight
                        ? 'bg-slate-600 text-white'
                        : 'bg-gray-100 text-gray-500 hover:bg-gray-200 dark:bg-slate-700 dark:text-gray-400 dark:hover:bg-gray-600'
                    }`}
                  >
                    Syntax highlight
                  </button>
                  <Link
                    to={`/push/${id}/diff`}
                    className="px-2.5 py-1 rounded text-xs text-slate-500 hover:text-slate-700 bg-gray-100 hover:bg-gray-200 transition-colors dark:bg-slate-700 dark:text-gray-400 dark:hover:text-gray-300 dark:hover:bg-gray-600"
                  >
                    Full page →
                  </Link>
                </div>
              )}
            </div>
            {diffLoading && (
              <div className="text-gray-400 text-sm dark:text-gray-500">Loading diff…</div>
            )}
            {!diffLoading && !diffContent && (
              <div className="text-gray-400 text-sm dark:text-gray-500">No diff available.</div>
            )}
            {!diffLoading && diffContent && diffLines >= DIFF_INLINE_THRESHOLD && (
              <div className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 flex items-center justify-between gap-4 dark:bg-amber-900/20 dark:border-amber-700">
                <div>
                  <p className="text-sm font-medium text-amber-800 dark:text-amber-300">
                    This diff is large ({diffLines.toLocaleString()} lines)
                  </p>
                  <p className="text-xs text-amber-600 mt-0.5 dark:text-amber-400">
                    Rendering it inline would degrade page performance.
                  </p>
                </div>
                <Link
                  to={`/push/${id}/diff`}
                  className="shrink-0 px-3 py-1.5 bg-amber-700 hover:bg-amber-600 text-white text-xs rounded transition-colors"
                >
                  View full diff →
                </Link>
              </div>
            )}
            {!diffLoading && diffContent && diffLines < DIFF_INLINE_THRESHOLD && (
              <div ref={diffRef} className="text-sm overflow-x-auto" />
            )}
          </div>

          {/* Re-push guidance */}
          {(record.status === 'REJECTED' ||
            record.status === 'CANCELED' ||
            record.status === 'APPROVED') && <RePushGuidance record={record} />}

          {/* Approve / Reject / Cancel */}
          {record.status === 'PENDING' &&
            (() => {
              const isAdmin = currentUser?.authorities?.includes('ROLE_ADMIN') ?? false
              const canSelfCertify = record.canCurrentUserSelfCertify ?? false
              const isPusher =
                !!currentUser?.username &&
                !!record.resolvedUser &&
                currentUser.username === record.resolvedUser
              const isSelfReview = isPusher && !canSelfCertify
              const canCancel = isAdmin || isPusher
              const attestationsComplete = attestationQuestions
                .filter((q) => q.required)
                .every((q) => {
                  const answer = attestationAnswers[q.id]
                  if (!answer || answer === '') return false
                  if (q.type === 'checkbox') return answer === 'true'
                  return true
                })
              return (
                <div className="bg-white rounded-lg shadow border border-gray-200 px-6 py-5 dark:bg-slate-800 dark:border-slate-700">
                  <h2 className="text-sm font-semibold text-gray-500 uppercase tracking-wide mb-3 dark:text-gray-400">
                    Review
                  </h2>
                  <div className="text-xs text-gray-400 mb-3 dark:text-gray-500">
                    Reviewing as{' '}
                    <strong>
                      {currentUser
                        ? currentUser.username +
                          (currentUser.emails[0] ? ` (${currentUser.emails[0].email})` : '')
                        : '…'}
                    </strong>
                    {isAdmin && (
                      <span className="ml-2 inline-flex items-center rounded-full bg-blue-100 px-2 py-0.5 text-xs font-medium text-blue-700 dark:bg-blue-900/30 dark:text-blue-300">
                        admin
                      </span>
                    )}
                  </div>
                  {isSelfReview && !adminOverrideEnabled && (
                    <div className="flex gap-2 text-sm text-amber-800 bg-amber-50 border border-amber-200 rounded px-3 py-2 mb-3 dark:bg-amber-900/20 dark:border-amber-700 dark:text-amber-300">
                      <span>⚠</span>
                      <span>
                        Self-approval is not permitted — you pushed these commits. Another reviewer
                        must approve or reject this push.
                      </span>
                    </div>
                  )}
                  {canSelfCertify && isPusher && (
                    <div className="flex gap-2 text-sm text-blue-800 bg-blue-50 border border-blue-200 rounded px-3 py-2 mb-3 dark:bg-blue-900/20 dark:border-blue-700 dark:text-blue-300">
                      <span>{'ℹ️'}</span>
                      <span>
                        You are self-certifying your own push. This approval will be permanently
                        recorded in the audit log.
                      </span>
                    </div>
                  )}
                  {isAdmin && isPusher && !canSelfCertify && !adminOverrideEnabled && (
                    <button
                      onClick={() => setAdminOverrideEnabled(true)}
                      className="w-full text-left text-xs text-gray-500 hover:text-gray-700 underline mb-3 dark:text-gray-400 dark:hover:text-gray-300"
                    >
                      Enable admin override
                    </button>
                  )}
                  {isAdmin && isPusher && adminOverrideEnabled && (
                    <div className="flex gap-2 text-sm text-red-800 bg-red-50 border border-red-200 rounded px-3 py-2 mb-3 dark:bg-red-900/20 dark:border-red-700 dark:text-red-300">
                      <span>⚠</span>
                      <span>
                        <strong>WARNING:</strong> You are about to approve your own push as an admin
                        override. Only do this if you have a very good reason to — this action will
                        be permanently recorded in the audit log.
                      </span>
                    </div>
                  )}
                  {attestationQuestions.length > 0 && (
                    <div className="mb-4 space-y-3">
                      <div className="text-xs font-semibold text-gray-500 uppercase tracking-wide dark:text-gray-400">
                        Attestation
                      </div>
                      {attestationQuestions.map((q) => (
                        <AttestationQuestionField
                          key={q.id}
                          question={q}
                          value={attestationAnswers[q.id] ?? ''}
                          disabled={isSelfReview && !adminOverrideEnabled}
                          onChange={(val) =>
                            setAttestationAnswers((prev) => ({ ...prev, [q.id]: val }))
                          }
                        />
                      ))}
                    </div>
                  )}
                  <textarea
                    value={reviewReason}
                    onChange={(e) => setReviewReason(e.target.value)}
                    rows={3}
                    placeholder={
                      'Reason (required for both approve and reject)\nDescribe the basis for your decision...'
                    }
                    disabled={isSelfReview && !adminOverrideEnabled}
                    className="w-full border border-gray-300 rounded px-3 py-2 text-sm mb-3 resize-none focus:outline-none focus:ring-2 focus:ring-blue-300 disabled:bg-gray-50 disabled:text-gray-400 dark:bg-slate-700 dark:border-slate-600 dark:text-gray-200 dark:placeholder-gray-400 dark:disabled:bg-gray-800 dark:disabled:text-gray-500"
                  />
                  {actionError && (
                    <div className="text-red-600 text-sm mb-3 dark:text-red-400">{actionError}</div>
                  )}
                  <div className="flex gap-3">
                    <button
                      onClick={handleApprove}
                      disabled={
                        (isSelfReview && !adminOverrideEnabled) ||
                        saving ||
                        canceling ||
                        !reviewReason.trim() ||
                        !attestationsComplete
                      }
                      className="px-4 py-2 text-sm font-medium rounded bg-green-600 text-white hover:bg-green-700 disabled:opacity-40 disabled:cursor-not-allowed disabled:bg-gray-400"
                    >
                      ✓ Approve
                    </button>
                    <button
                      onClick={handleReject}
                      disabled={
                        (isSelfReview && !adminOverrideEnabled) ||
                        saving ||
                        canceling ||
                        !reviewReason.trim()
                      }
                      className="px-4 py-2 text-sm font-medium rounded bg-red-600 text-white hover:bg-red-700 disabled:opacity-40 disabled:cursor-not-allowed disabled:bg-gray-400"
                    >
                      ✗ Reject
                    </button>
                    {canCancel && (
                      <button
                        onClick={handleCancel}
                        disabled={saving || canceling}
                        className="ml-auto px-4 py-2 text-sm font-medium rounded border border-gray-300 text-gray-600 hover:bg-gray-50 disabled:opacity-50 dark:border-slate-600 dark:text-gray-400 dark:hover:bg-gray-700"
                      >
                        {canceling ? 'Canceling…' : 'Cancel push'}
                      </button>
                    )}
                  </div>
                </div>
              )
            })()}
        </>
      )}
    </div>
  )
}
