package com.rbc.fogwall.git;

import com.rbc.fogwall.service.ScmOAuthTokenService;
import com.rbc.fogwall.service.ScmOAuthTokenService.Access;
import org.eclipse.jgit.errors.UnsupportedCredentialItem;
import org.eclipse.jgit.transport.CredentialItem;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.URIish;

/**
 * Supplies a fogwall user's linked OAuth token as the credential for fogwall's own upstream git operations: the mirror
 * sync before a push or fetch, and the forward after approval.
 *
 * <p>The access token is looked up each time JGit asks, never captured, because a push may wait for review far longer
 * than a provider's tokens live.
 */
public class ScmOAuthCredentialsProvider extends CredentialsProvider {

    /**
     * The HTTP username sent with the access token. GitLab requires this value for OAuth tokens; GitHub and
     * Forgejo/Gitea accept any username when the password is a token.
     */
    static final String GIT_USERNAME = "oauth2";

    private final ScmOAuthTokenService oauthTokens;
    private final String username;
    private final String provider;

    /**
     * @param username the fogwall user whose linked token is used
     * @param provider the provider name the token was linked on
     */
    public ScmOAuthCredentialsProvider(ScmOAuthTokenService oauthTokens, String username, String provider) {
        this.oauthTokens = oauthTokens;
        this.username = username;
        this.provider = provider;
    }

    /** The fogwall user whose linked token this supplies. */
    public String username() {
        return username;
    }

    /** Looks the linked token up now, refreshing it if it has expired. */
    public Access access() {
        return oauthTokens.access(username, provider);
    }

    @Override
    public boolean isInteractive() {
        return false;
    }

    @Override
    public boolean supports(CredentialItem... items) {
        for (CredentialItem item : items) {
            if (!(item instanceof CredentialItem.Username) && !(item instanceof CredentialItem.Password)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean get(URIish uri, CredentialItem... items) throws UnsupportedCredentialItem {
        if (!(access() instanceof Access.Usable usable)) {
            return false;
        }
        for (CredentialItem item : items) {
            switch (item) {
                case CredentialItem.Username u -> u.setValue(GIT_USERNAME);
                case CredentialItem.Password p ->
                    p.setValue(usable.accessToken().toCharArray());
                default ->
                    throw new UnsupportedCredentialItem(uri, item.getClass().getName() + ":" + item.getPromptText());
            }
        }
        return true;
    }
}
