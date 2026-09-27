import { useEffect, useState } from 'react'
import { NavLink } from 'react-router'
import {
  fetchMyGitCredentialProviders,
  fetchMyGitCredentials,
  fetchUserGitCredentials,
  issueGitCredential,
  revokeGitCredential,
  revokeUserGitCredential,
  rotateGitCredential,
} from '../api'
import type { GitCredential, IssuedGitCredential } from '../types'
import { ConfigBlock } from './ConfigBlock'
import { useToast } from './Toast'

function formatDate(value?: string) {
  return value ? new Date(value).toLocaleDateString() : null
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

/** The credential's expiry, or that it has expired. Nothing for a credential that never expires. */
function Expiry({ credential }: { credential: GitCredential }) {
  if (credential.expired) {
    return (
      <span className="rounded bg-red-100 px-1.5 py-0.5 text-xs font-medium text-red-700 dark:bg-red-950/60 dark:text-red-300">
        Expired
      </span>
    )
  }
  return credential.expiresAt ? <>expires {formatDateTime(credential.expiresAt)}</> : null
}

/**
 * The two ways to hand a just-issued credential to git for one server-mode remote: through the developer's credential
 * helper, or in the remote URL for a machine without one.
 */
function storeCommands(host: string, protocol: string, value: string) {
  const repoPath = 'server/<provider-host>/<owner>/<repo>.git'
  return {
    approve: `git credential approve <<'EOF'
protocol=${protocol.replace(':', '')}
host=${host}
path=${repoPath}
username=fogwall
password=${value}
EOF`,
    remote: `git remote add fogwall ${protocol}//fogwall:${value}@${host}/${repoPath}`,
  }
}

/** Reveals a credential's value once, with the commands to store it, until the user dismisses it. */
function IssuedPanel({
  issued,
  onDismiss,
}: {
  issued: IssuedGitCredential
  onDismiss: () => void
}) {
  const commands = storeCommands(window.location.host, window.location.protocol, issued.value)
  return (
    <div className="space-y-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-4 dark:border-amber-900/50 dark:bg-amber-950/40">
      <p className="text-sm font-medium text-amber-900 dark:text-amber-200">
        Copy the credential &ldquo;{issued.credential.name}&rdquo; now. It is not shown again.
      </p>
      <ConfigBlock label="Credential" config={issued.value} />
      <p className="text-sm text-amber-900 dark:text-amber-200">
        With a credential helper (macOS Keychain, libsecret, Git Credential Manager), store it for a
        repository&rsquo;s server-mode remote. Run the one-time setting under Authentication in the{' '}
        <NavLink to="/setup" className="underline">
          setup guide
        </NavLink>{' '}
        first, so the credential is used only for server-mode remotes.
      </p>
      <ConfigBlock label="With a credential helper" config={commands.approve} />
      <p className="text-sm text-amber-900 dark:text-amber-200">
        Without one, put it in the remote URL. Git keeps it in the repository&rsquo;s{' '}
        <code className="font-mono">.git/config</code>, in plain text.
      </p>
      <ConfigBlock label="In the remote URL" config={commands.remote} />
      <button
        onClick={onDismiss}
        className="px-4 py-2 rounded bg-slate-700 text-white text-sm hover:bg-slate-600 transition-colors"
      >
        Done
      </button>
    </div>
  )
}

/**
 * The profile's git credentials: fogwall-issued credentials for pushing to server-mode remotes of providers that
 * forward such pushes under the user's linked account.
 */
export function GitCredentials({ providers }: { providers: string[] }) {
  const toast = useToast()
  const [credentials, setCredentials] = useState<GitCredential[]>([])
  const [newName, setNewName] = useState('')
  const [busy, setBusy] = useState(false)
  const [issued, setIssued] = useState<IssuedGitCredential | null>(null)
  // Providers this user has linked an account on. A credential is only issued or rotated when there is one: without a
  // linked account there is nothing to forward a push with.
  const [linked, setLinked] = useState<string[] | null>(null)
  const canIssue = linked !== null && linked.length > 0

  useEffect(() => {
    fetchMyGitCredentials()
      .then(setCredentials)
      .catch(() => {})
    fetchMyGitCredentialProviders()
      .then(setLinked)
      .catch(() => setLinked([]))
  }, [])

  const handleIssue = async (e: React.FormEvent) => {
    e.preventDefault()
    setBusy(true)
    try {
      const result = await issueGitCredential(newName.trim())
      setCredentials((prev) =>
        [...prev, result.credential].sort((a, b) => a.name.localeCompare(b.name)),
      )
      setIssued(result)
      setNewName('')
    } catch (err) {
      toast.error((err as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const handleRotate = async (credential: GitCredential) => {
    if (!window.confirm(`Replace "${credential.name}"? Its current value stops working at once.`))
      return
    try {
      const result = await rotateGitCredential(credential.id)
      setCredentials((prev) => prev.map((c) => (c.id === credential.id ? result.credential : c)))
      setIssued(result)
    } catch (err) {
      toast.error((err as Error).message)
    }
  }

  const handleRevoke = async (credential: GitCredential) => {
    if (!window.confirm(`Revoke "${credential.name}"? It stops working at once.`)) return
    try {
      await revokeGitCredential(credential.id)
      setCredentials((prev) => prev.filter((c) => c.id !== credential.id))
      if (issued?.credential.id === credential.id) setIssued(null)
    } catch (err) {
      toast.error((err as Error).message)
    }
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-gray-500 dark:text-gray-400">
        Credentials for pushing through fogwall&rsquo;s server mode to{' '}
        <span className="font-medium">{providers.join(', ')}</span>. fogwall forwards the push with
        the account you linked on the SCM Identities tab. A credential works only against fogwall
        and gives no access on the provider itself. Create one per machine.
      </p>

      {linked !== null && !canIssue && (
        <p className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900/50 dark:bg-amber-950/40 dark:text-amber-200">
          Link your {providers.join(' or ')} account on the SCM Identities tab before creating a git
          credential. An account linked before pushes were enabled has to be linked again.
        </p>
      )}

      {issued && <IssuedPanel issued={issued} onDismiss={() => setIssued(null)} />}

      {credentials.length === 0 ? (
        <p className="text-sm text-gray-400 italic dark:text-gray-500">No git credentials.</p>
      ) : (
        <ul className="divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white dark:bg-slate-800 dark:border-slate-700 dark:divide-gray-700">
          {credentials.map((credential) => (
            <li key={credential.id} className="px-4 py-3 text-sm space-y-1">
              <div className="flex items-center justify-between">
                <span className="flex items-center gap-2">
                  <span className="font-medium text-gray-800 dark:text-gray-200">
                    {credential.name}
                  </span>
                  {credential.expired && <Expiry credential={credential} />}
                </span>
                <span className="flex gap-3">
                  {canIssue && (
                    <button
                      onClick={() => handleRotate(credential)}
                      className="text-gray-400 hover:text-slate-700 transition-colors text-xs dark:text-gray-500 dark:hover:text-gray-300"
                    >
                      Rotate
                    </button>
                  )}
                  <button
                    onClick={() => handleRevoke(credential)}
                    className="text-gray-400 hover:text-red-500 transition-colors text-xs dark:text-gray-500 dark:hover:text-red-400"
                  >
                    Revoke
                  </button>
                </span>
              </div>
              <p className="text-xs text-gray-400 dark:text-gray-500">
                Created {formatDate(credential.createdAt)}
                {credential.expiresAt && !credential.expired && (
                  <>
                    {' · '}
                    <Expiry credential={credential} />
                  </>
                )}
                {' · '}
                {credential.lastUsedAt
                  ? `last used ${formatDate(credential.lastUsedAt)}`
                  : 'never used'}
              </p>
            </li>
          ))}
        </ul>
      )}

      {canIssue && (
        <form onSubmit={handleIssue} className="flex gap-2">
          <input
            type="text"
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
            placeholder="Name, such as the machine it will be used from"
            maxLength={100}
            className="flex-1 rounded border border-gray-300 px-3 py-2 text-sm focus:border-slate-500 focus:outline-none dark:bg-slate-700 dark:border-slate-600 dark:text-gray-200 dark:placeholder-gray-400"
          />
          <button
            type="submit"
            disabled={busy || !newName.trim()}
            className="px-4 py-2 rounded bg-slate-700 text-white text-sm hover:bg-slate-600 disabled:opacity-50 transition-colors"
          >
            Create
          </button>
        </form>
      )}
    </div>
  )
}

/** An administrator's view of another user's git credentials: when each was made and last used, and revocation. */
export function UserGitCredentials({ username }: { username: string }) {
  const toast = useToast()
  const [credentials, setCredentials] = useState<GitCredential[]>([])

  useEffect(() => {
    fetchUserGitCredentials(username)
      .then(setCredentials)
      .catch(() => {})
  }, [username])

  const handleRevoke = async (credential: GitCredential) => {
    if (!window.confirm(`Revoke ${username}'s credential "${credential.name}"?`)) return
    try {
      await revokeUserGitCredential(username, credential.id)
      setCredentials((prev) => prev.filter((c) => c.id !== credential.id))
    } catch (err) {
      toast.error((err as Error).message)
    }
  }

  return (
    <section className="space-y-2">
      <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">Git Credentials</h3>
      {credentials.length === 0 ? (
        <p className="text-sm text-gray-400 italic dark:text-gray-500">No git credentials.</p>
      ) : (
        <ul className="divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white dark:bg-slate-800 dark:border-slate-700 dark:divide-gray-700">
          {credentials.map((credential) => (
            <li key={credential.id} className="flex items-center justify-between px-4 py-3 text-sm">
              <span className="space-x-2">
                <span className="text-gray-800 dark:text-gray-200">{credential.name}</span>
                {credential.expired && <Expiry credential={credential} />}
                <span className="text-xs text-gray-400 dark:text-gray-500">
                  created {formatDate(credential.createdAt)} ·{' '}
                  {credential.expiresAt && !credential.expired && (
                    <>
                      <Expiry credential={credential} />
                      {' · '}
                    </>
                  )}
                  {credential.lastUsedAt
                    ? `last used ${formatDate(credential.lastUsedAt)}`
                    : 'never used'}
                </span>
              </span>
              <button
                onClick={() => handleRevoke(credential)}
                className="text-red-400 text-xs hover:text-red-600 dark:text-red-500 dark:hover:text-red-300"
              >
                Revoke
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}
