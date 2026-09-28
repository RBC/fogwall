package com.rbc.fogwall.git;

import com.rbc.fogwall.approval.SelfApprovalPolicy;
import com.rbc.fogwall.db.ParkedPushStore;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.model.Attestation;
import com.rbc.fogwall.db.model.ParkedPush;
import com.rbc.fogwall.db.model.ParkedRefUpdate;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.PushStatus;
import com.rbc.fogwall.service.ScmOAuthTokenService;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.PackParser;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;

/**
 * Forwards parked pushes upstream once approved, from whichever instance is asked to. Nothing about a parked push lives
 * on the instance that received it: the pack and ref updates come from the {@link ParkedPushStore}, the upstream
 * credential is the forwarding user's linked OAuth grant, and the mirror is brought current from upstream first.
 *
 * <p>A forward starts only after its caller takes the claim on the push record, so however many instances are asked,
 * exactly one forwards; a claim whose holder dies lapses and {@link #sweep} picks the push up again. The forward itself
 * runs on a background thread, since the client that pushed has long since disconnected.
 *
 * <p>The pack is indexed into a quarantine over the mirror rather than the mirror itself, which stays a reflection of
 * upstream. A fast-forward update is pushed without an expected-old check, so a push queued behind another parked push
 * to the same branch still applies once that one lands; upstream's own fast-forward rule decides. A forced update or
 * delete keeps the check, so it never overwrites a tip the developer did not see.
 */
@Slf4j
public class DeferredForwarder implements AutoCloseable {

    /**
     * How long a claim holds if its instance dies mid-forward. Well beyond any forward of a size-bounded pack, so a
     * slow forward is never taken over while it is still running.
     */
    public static final Duration DEFAULT_CLAIM_TTL = Duration.ofMinutes(30);

    /** How long closing waits for forwards in flight before leaving them to a sweep on another instance. */
    private static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(30);

    /** Pushes picked up per sweep, so one sweep cannot run unboundedly long. */
    private static final int SWEEP_LIMIT = 100;

    /** How long a pack stays after its push record disappears before it is treated as orphaned. */
    private static final Duration ORPHAN_GRACE = Duration.ofHours(1);

    private final PushStore pushStore;
    private final ParkedPushStore parkedPushStore;
    private final LocalRepositoryCache cache;
    private final ScmOAuthTokenService oauthTokens;
    private final SelfApprovalPolicy selfApprovalPolicy;
    private final String instanceId;
    private final Duration claimTtl;
    private final Duration failedRetention;
    private final int connectTimeoutSeconds;
    private final Clock clock;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    /**
     * @param instanceId identifies this instance on the claims it takes
     * @param claimTtl how long a claim holds if this instance dies mid-forward; longer than any forward takes
     * @param failedRetention how long a push whose forward failed keeps its pack for a retry
     * @param connectTimeoutSeconds upstream transport timeout; 0 for none
     */
    public DeferredForwarder(
            PushStore pushStore,
            ParkedPushStore parkedPushStore,
            LocalRepositoryCache cache,
            ScmOAuthTokenService oauthTokens,
            SelfApprovalPolicy selfApprovalPolicy,
            String instanceId,
            Duration claimTtl,
            Duration failedRetention,
            int connectTimeoutSeconds,
            Clock clock) {
        this.pushStore = pushStore;
        this.parkedPushStore = parkedPushStore;
        this.cache = cache;
        this.oauthTokens = oauthTokens;
        this.selfApprovalPolicy = selfApprovalPolicy;
        this.instanceId = instanceId;
        this.claimTtl = claimTtl;
        this.failedRetention = failedRetention;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.clock = clock;
    }

    /**
     * An identifier for this process's claims: the host name, for an operator reading the push record, and a random
     * suffix, so a restarted instance never mistakes its predecessor's claim for its own.
     */
    public static String newInstanceId() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            host = "unknown-host";
        }
        return host + "/" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Claims the push and forwards it in the background. Returns whether this call started a forward: false when the
     * push is not a deferred push in a forwardable state, or another instance is already forwarding it.
     *
     * @param retryFailed also forward a push whose previous forward ended in ERROR
     */
    public boolean start(String pushId, boolean retryFailed) {
        if (!pushStore.claimForward(pushId, instanceId, clock.instant(), claimTtl, retryFailed)) {
            return false;
        }
        executor.submit(() -> forwardClaimed(pushId));
        return true;
    }

    /**
     * Starts every approved push nobody is forwarding, and deletes the packs nothing can forward any more. Safe to run
     * on every instance at once: the claim decides who forwards, and deleting a pack twice is harmless.
     *
     * @return how many forwards this call started
     */
    public int sweep() {
        Instant now = clock.instant();
        int started = 0;
        for (String pushId : pushStore.findForwardable(now, SWEEP_LIMIT)) {
            if (start(pushId, false)) {
                started++;
            }
        }
        for (String pushId :
                parkedPushStore.findReclaimable(now.minus(failedRetention), now.minus(ORPHAN_GRACE), SWEEP_LIMIT)) {
            parkedPushStore.delete(pushId);
            log.info("Deleted the stored pack of push {}: nothing can forward it any more", pushId);
        }
        return started;
    }

    /** Deletes the stored pack of a push that will never be forwarded. Best-effort: {@link #sweep} is the backstop. */
    public void discard(String pushId) {
        try {
            parkedPushStore.delete(pushId);
        } catch (RuntimeException e) {
            log.warn("Could not delete the stored pack of push {}: {}", pushId, e.getMessage());
        }
    }

    /** Runs one claimed forward to completion and records its outcome. Package-visible so tests can run it inline. */
    void forwardClaimed(String pushId) {
        ForwardOutcome outcome;
        try {
            outcome = forward(pushId);
        } catch (Exception e) {
            log.error("Deferred forward of push {} failed", pushId, e);
            outcome = new Failed("Forwarding failed: " + e.getMessage());
        }
        boolean recorded =
                switch (outcome) {
                    case Forwarded() -> pushStore.completeForward(pushId, instanceId, PushStatus.FORWARDED, null);
                    case Failed(String reason) ->
                        pushStore.completeForward(pushId, instanceId, PushStatus.ERROR, reason);
                };
        if (!recorded) {
            log.warn("Push {} forward finished but this instance no longer holds its claim", pushId);
            return;
        }
        switch (outcome) {
            case Forwarded() -> {
                log.info("Push {} forwarded", pushId);
                discard(pushId);
            }
            case Failed(String reason) -> log.info("Push {} failed to forward: {}", pushId, reason);
        }
    }

    /** How a forward ended: every ref update applied upstream, or the cause it did not. */
    private sealed interface ForwardOutcome permits Forwarded, Failed {}

    private record Forwarded() implements ForwardOutcome {}

    private record Failed(String reason) implements ForwardOutcome {}

    private ForwardOutcome forward(String pushId) throws Exception {
        Optional<PushRecord> record = pushStore.findById(pushId);
        Optional<ParkedPush> parked = parkedPushStore.find(pushId);
        if (record.isEmpty() || parked.isEmpty()) {
            return new Failed("The stored push is no longer available; push again");
        }
        return forward(pushId, record.get(), parked.get());
    }

    private ForwardOutcome forward(String pushId, PushRecord record, ParkedPush parked) throws Exception {
        // The claim already requires APPROVED or a failed forward, which only follows an approval; the attestation is
        // checked as well, so nothing but a recorded approval ever sends a push upstream.
        if (record.getAttestation() == null || record.getAttestation().getType() != Attestation.Type.APPROVAL) {
            return new Failed("No approval is recorded for this push");
        }
        SelfApprovalPolicy.Verdict verdict = selfApprovalPolicy.evaluate(record);
        if (!verdict.isHonored()) {
            return new Failed(verdict.getReason());
        }

        var credentials = new ScmOAuthCredentialsProvider(oauthTokens, parked.forwardUser(), parked.providerName());
        if (credentials.access() instanceof ScmOAuthTokenService.Access.Unusable unusable) {
            return new Failed("The pusher's linked account could not be used to forward (" + unusable.reason()
                    + "). They can link it again from their fogwall profile, then forward the push again.");
        }

        String principal = "fogwall-user:" + parked.forwardUser();
        Repository mirror = cache.getOrClone(parked.upstreamUrl(), credentials, null, principal);
        cache.refreshNow(parked.upstreamUrl(), credentials, null, principal);

        try (QuarantineObjectStore quarantine = QuarantineObjectStore.create(mirror, pushId)) {
            Repository repo = quarantine.getRepository();
            if (parked.packBytes() > 0) {
                indexPack(repo, pushId);
            }
            for (ParkedRefUpdate ref : parked.refs()) {
                if (ref.type() != ReceiveCommand.Type.DELETE
                        && !repo.getObjectDatabase().has(ObjectId.fromString(ref.newId()))) {
                    return new Failed(
                            "The stored push is missing " + ref.newId() + " for " + ref.refName() + "; push again");
                }
            }
            return push(repo, parked, credentials);
        }
    }

    private void indexPack(Repository repo, String pushId) throws IOException {
        try (InputStream pack = parkedPushStore.openPack(pushId);
                ObjectInserter inserter = repo.newObjectInserter()) {
            PackParser parser = inserter.newPackParser(pack);
            parser.setAllowThin(true);
            var lock = parser.parse(NullProgressMonitor.INSTANCE);
            inserter.flush();
            if (lock != null) {
                lock.unlock();
            }
        }
    }

    /** Pushes every ref update; fails with the upstream's refusals if any update did not apply. */
    private ForwardOutcome push(Repository repo, ParkedPush parked, ScmOAuthCredentialsProvider credentials)
            throws Exception {
        List<RemoteRefUpdate> updates = new ArrayList<>();
        for (ParkedRefUpdate ref : parked.refs()) {
            updates.add(toRemoteUpdate(repo, ref));
        }
        List<String> failures = new ArrayList<>();
        try (Transport transport = Transport.open(repo, new URIish(parked.upstreamUrl()))) {
            transport.setCredentialsProvider(credentials);
            if (connectTimeoutSeconds > 0) {
                transport.setTimeout(connectTimeoutSeconds);
            }
            PushResult result = transport.push(NullProgressMonitor.INSTANCE, updates);
            for (RemoteRefUpdate update : result.getRemoteUpdates()) {
                RemoteRefUpdate.Status status = update.getStatus();
                if (status != RemoteRefUpdate.Status.OK && status != RemoteRefUpdate.Status.UP_TO_DATE) {
                    String message = update.getMessage();
                    failures.add(
                            update.getRemoteName() + " -> " + status + (message != null ? " (" + message + ")" : ""));
                }
            }
        }
        return failures.isEmpty() ? new Forwarded() : new Failed("Upstream refused: " + String.join("; ", failures));
    }

    private static RemoteRefUpdate toRemoteUpdate(Repository repo, ParkedRefUpdate ref) throws IOException {
        ObjectId oldId = ObjectId.fromString(ref.oldId());
        return switch (ref.type()) {
            case DELETE -> new RemoteRefUpdate(repo, null, ObjectId.zeroId(), ref.refName(), true, null, oldId);
            case UPDATE_NONFASTFORWARD ->
                new RemoteRefUpdate(repo, null, ObjectId.fromString(ref.newId()), ref.refName(), true, null, oldId);
            case CREATE, UPDATE ->
                new RemoteRefUpdate(repo, null, ObjectId.fromString(ref.newId()), ref.refName(), false, null, null);
        };
    }

    /**
     * Stops taking forwards and waits a bounded time for those in flight, so a shutdown does not abandon a push mid-way
     * with a claim no other instance can take until it lapses.
     */
    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_GRACE.toSeconds(), TimeUnit.SECONDS)) {
                log.warn(
                        "Deferred forwards still running after {}; their claims lapse and a sweep retries them",
                        SHUTDOWN_GRACE);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
