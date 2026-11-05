package com.rbc.fogwall.permission;

import com.rbc.fogwall.user.UserEntry;
import com.rbc.fogwall.user.UserStore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * The one way to change who may do what on a repository: grants and revocations of user and group permissions, group
 * membership, and a user's roles. Each change is applied and recorded as a {@link PermissionChangeEvent},
 * {@link MembershipChangeEvent} or {@link RoleChangeEvent}, and a grant carries who granted it and when.
 *
 * <p>An addition is recorded before it is applied, so if the record cannot be written the access is not granted. A
 * removal is applied before it is recorded, so a failure to write the record never leaves the access in place. Either
 * failure propagates to the caller.
 *
 * <p>Configuration applied at startup is not recorded; that is the deployment's own trail. A live reload is, through
 * {@link #reloadConfigPermissions}.
 */
@Slf4j
public class AccessChangeService {

    private final RepoPermissionService permissions;
    private final GroupPermissionStore groups;
    private final UserStore users;
    private final PermissionChangeStore permissionChanges;
    private final MembershipChangeStore membershipChanges;
    private final RoleChangeStore roleChanges;

    public AccessChangeService(
            RepoPermissionService permissions,
            UserStore users,
            PermissionChangeStore permissionChanges,
            MembershipChangeStore membershipChanges,
            RoleChangeStore roleChanges) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.groups = Objects.requireNonNull(
                permissions.getGroupStore(), "a group store is required: group changes could not be recorded");
        this.users = Objects.requireNonNull(users, "users");
        this.permissionChanges = Objects.requireNonNull(permissionChanges, "permissionChanges");
        this.membershipChanges = Objects.requireNonNull(membershipChanges, "membershipChanges");
        this.roleChanges = Objects.requireNonNull(roleChanges, "roleChanges");
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }

    // ---- user permissions ----

    /** Grants {@code permission}, stamping it with the actor and time the event records. */
    public void grant(RepoPermission permission, ChangeContext context) {
        permission.setGrantedBy(context.actor());
        permission.setGrantedAt(now());
        permissionChanges.record(userEvent(PermissionChangeEvent.Action.GRANTED, permission, context)
                .occurredAt(permission.getGrantedAt())
                .build());
        permissions.save(permission);
    }

    public void revoke(RepoPermission permission, ChangeContext context) {
        permissions.delete(permission.getId());
        permissionChanges.record(userEvent(PermissionChangeEvent.Action.REVOKED, permission, context)
                .build());
    }

    /**
     * Applies the {@code permissions:} section of a reloaded configuration and records what it changed. Grants the
     * reload leaves as they were are not recorded.
     */
    public void reloadConfigPermissions(List<RepoPermission> configured, ChangeContext context) {
        RepoPermissionService.ConfigSeedResult result = permissions.seedFromConfig(configured);
        result.removed()
                .forEach(p -> permissionChanges.record(userEvent(PermissionChangeEvent.Action.REVOKED, p, context)
                        .build()));
        result.added()
                .forEach(p -> permissionChanges.record(userEvent(PermissionChangeEvent.Action.GRANTED, p, context)
                        .occurredAt(p.getGrantedAt())
                        .build()));
    }

    // ---- group rules ----

    /** Adds {@code rule} to {@code group}, stamping it with the actor and time the event records. */
    public void addGroupRule(PermissionGroup group, GroupPermissionRule rule, ChangeContext context) {
        rule.setGrantedBy(context.actor());
        rule.setGrantedAt(now());
        permissionChanges.record(groupEvent(PermissionChangeEvent.Action.GRANTED, group, rule, context)
                .occurredAt(rule.getGrantedAt())
                .build());
        groups.saveRule(rule);
    }

    public void removeGroupRule(PermissionGroup group, GroupPermissionRule rule, ChangeContext context) {
        groups.deleteRule(rule.getId());
        permissionChanges.record(groupEvent(PermissionChangeEvent.Action.REVOKED, group, rule, context)
                .build());
    }

    // ---- membership ----

    public void addMember(PermissionGroup group, String username, ChangeContext context) {
        membershipChanges.record(membershipEvent(MembershipChangeEvent.Action.ADDED, group, username, context));
        groups.addMember(group.getId(), username);
    }

    /** Removes {@code username} from {@code group}. Returns false, recording nothing, when they were not a member. */
    public boolean removeMember(PermissionGroup group, String username, ChangeContext context) {
        if (!groups.findMembers(group.getId()).contains(username)) {
            return false;
        }
        groups.removeMember(group.getId(), username);
        membershipChanges.record(membershipEvent(MembershipChangeEvent.Action.REMOVED, group, username, context));
        return true;
    }

    // ---- roles ----

    /**
     * Creates a local user with {@code roles}, recording the roles they start with.
     *
     * @throws IllegalArgumentException when the user already exists; nothing is recorded
     */
    public void createUser(String username, String passwordHash, List<String> roles, ChangeContext context) {
        if (users.findByUsername(username).isPresent()) {
            throw new IllegalArgumentException("User already exists: " + username);
        }
        roleChanges.record(roleEvent(username, List.of(), roles, context));
        users.createUser(username, passwordHash, String.join(",", roles));
    }

    /**
     * Stores the roles an identity provider asserted for {@code username} at sign-in, creating the user if fogwall has
     * not seen them, and records the change when the roles differ from what was stored. A sign-in that changes nothing
     * records nothing.
     *
     * @param provider the identity provider that asserted the roles, recorded as the actor
     */
    public void syncIdpRoles(String username, List<String> asserted, String provider) {
        List<String> stored = users.findByUsername(username)
                .map(UserEntry::getRoles)
                .map(RoleChangeEvent::normalize)
                .orElse(List.of());
        List<String> roles = RoleChangeEvent.normalize(asserted);
        if (stored.equals(roles)) {
            users.upsertUser(username, asserted);
            return;
        }
        RoleChangeEvent event = roleEvent(username, stored, roles, ChangeContext.by(ChangeOrigin.IDP, provider));
        if (stored.containsAll(roles)) {
            // Only loses roles: a removal, applied first.
            users.upsertUser(username, asserted);
            roleChanges.record(event);
        } else {
            // Gains a role, whatever else it loses: an addition, recorded first.
            roleChanges.record(event);
            users.upsertUser(username, asserted);
        }
        log.info("IdP {} changed roles for '{}': {} -> {}", provider, username, stored, roles);
    }

    // ---- removals that take access with them ----

    /** Deletes {@code group}, recording the revocation of each of its rules and the removal of each member. */
    public void deleteGroup(PermissionGroup group, ChangeContext context) {
        List<GroupPermissionRule> rules = groups.findRulesForGroup(group.getId());
        List<String> members = groups.findMembers(group.getId());
        groups.deleteGroup(group.getId());
        rules.forEach(
                rule -> permissionChanges.record(groupEvent(PermissionChangeEvent.Action.REVOKED, group, rule, context)
                        .build()));
        members.forEach(member -> membershipChanges.record(
                membershipEvent(MembershipChangeEvent.Action.REMOVED, group, member, context)));
    }

    /**
     * Deletes {@code user}, recording the revocation of every grant they held, their removal from every group, and the
     * loss of their roles. The store would drop the grants and memberships with the user; revoking them first records
     * it.
     */
    public void deleteUser(UserEntry user, ChangeContext context) {
        String username = user.getUsername();
        permissions.findByUsername(username).forEach(p -> revoke(p, context));
        for (String groupId : groups.findGroupIdsForUser(username)) {
            Optional<PermissionGroup> group = groups.findGroupById(groupId);
            if (group.isPresent()) {
                removeMember(group.get(), username, context);
            } else {
                log.warn("User '{}' is a member of group id {}, which does not exist", username, groupId);
            }
        }
        users.deleteUser(username);
        roleChanges.record(roleEvent(username, user.getRoles(), List.of(), context));
    }

    // ---- reading the record ----

    public List<PermissionChangeEvent> findPermissionChanges(PermissionChangeQuery query) {
        return permissionChanges.find(query);
    }

    public List<MembershipChangeEvent> findMembershipChanges(MembershipChangeQuery query) {
        return membershipChanges.find(query);
    }

    public List<RoleChangeEvent> findRoleChanges(RoleChangeQuery query) {
        return roleChanges.find(query);
    }

    /** Whether the grant {@code event} describes applies to the repository at {@code path} ({@code /owner/repo}). */
    public boolean appliesTo(PermissionChangeEvent event, String path) {
        return permissions.patternMatches(event.target(), event.value(), event.matchType(), path);
    }

    // ---- events ----

    private static PermissionChangeEvent.PermissionChangeEventBuilder userEvent(
            PermissionChangeEvent.Action action, RepoPermission p, ChangeContext context) {
        return PermissionChangeEvent.builder()
                .action(action)
                .origin(context.origin())
                .actor(context.actor())
                .sourceRevision(context.sourceRevision())
                .subjectType(PermissionChangeEvent.SubjectType.USER)
                .subject(p.getUsername())
                .permissionId(p.getId())
                .provider(p.getProvider())
                .target(p.getTarget())
                .value(p.getValue())
                .matchType(p.getMatchType())
                .grant(p.getGrant());
    }

    private static PermissionChangeEvent.PermissionChangeEventBuilder groupEvent(
            PermissionChangeEvent.Action action,
            PermissionGroup group,
            GroupPermissionRule rule,
            ChangeContext context) {
        return PermissionChangeEvent.builder()
                .action(action)
                .origin(context.origin())
                .actor(context.actor())
                .sourceRevision(context.sourceRevision())
                .subjectType(PermissionChangeEvent.SubjectType.GROUP)
                .subject(group.getName())
                .permissionId(rule.getId())
                .provider(rule.getProvider())
                .target(rule.getTarget())
                .value(rule.getValue())
                .matchType(rule.getMatchType())
                .grant(rule.getGrant());
    }

    private static MembershipChangeEvent membershipEvent(
            MembershipChangeEvent.Action action, PermissionGroup group, String username, ChangeContext context) {
        return MembershipChangeEvent.builder()
                .action(action)
                .origin(context.origin())
                .actor(context.actor())
                .sourceRevision(context.sourceRevision())
                .groupId(group.getId())
                .groupName(group.getName())
                .member(username)
                .build();
    }

    private static RoleChangeEvent roleEvent(
            String username, List<String> previous, List<String> roles, ChangeContext context) {
        return RoleChangeEvent.builder()
                .origin(context.origin())
                .actor(context.actor())
                .username(username)
                .previousRoles(previous)
                .roles(roles)
                .build();
    }
}
