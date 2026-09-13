package com.rbc.fogwall.db.model;

/**
 * Which fogwall surface performed an SCM API action: the dashboard acting on a signed-in user's behalf, or the SCM API
 * proxy forwarding a CLI's mutation. Set by fogwall at the point the record is written, never read off the request.
 *
 * <p>Distinct from {@link ScmApiActionRecord#getClientType()} on purpose. That field classifies a caller-supplied
 * {@code User-Agent} — evidence, forgeable, and never an input to a decision. This one is fogwall's own account of
 * which of its entry points ran the action, so it is safe to filter and report on.
 */
public enum ScmApiActionOrigin {

    /** The dashboard's own write path — a signed-in user acting through the UI with their linked OAuth token. */
    DASHBOARD,

    /** The SCM API proxy — a mutation forwarded on behalf of a CLI (gh, glab, tea, fj) or any other API client. */
    SCM_API
}
