package com.rbc.fogwall.dashboard.export;

import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import com.rbc.fogwall.permission.GroupPermissionRule;
import com.rbc.fogwall.permission.GroupPermissionStore;
import com.rbc.fogwall.permission.PermissionGroup;
import com.rbc.fogwall.permission.RepoPermission;
import com.rbc.fogwall.permission.RepoPermissionService;
import com.rbc.fogwall.user.ReadOnlyUserStore;
import com.rbc.fogwall.user.Roles;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Who holds which capability on which repositories right now, directly or through a group, and who is in each group. A
 * group rule appears once per member, so every row names a holder.
 */
public final class GrantSnapshot {

    private GrantSnapshot() {}

    /** How a holder came to hold a grant. */
    public enum Via {
        DIRECT,
        GROUP
    }

    /**
     * One holder's grant.
     *
     * @param group the group the grant came through; null for a direct grant
     * @param source where the grant is defined: CONFIG or DB — for a group grant, the group's source
     * @param effective whether the holder's roles let the grant take effect; a reader's or auditor's grants do not
     * @param permissionId the id of the user permission or group rule
     */
    public record Holding(
            String holder,
            Via via,
            String group,
            String provider,
            MatchTarget target,
            String value,
            MatchType matchType,
            RepoPermission.Grant grant,
            String source,
            String grantedBy,
            Instant grantedAt,
            boolean effective,
            String permissionId) {}

    /** One member of one group. */
    public record Membership(String groupId, String group, String groupSource, String member) {}

    private static final Comparator<Holding> HOLDING_ORDER = Comparator.comparing(Holding::holder)
            .thenComparing(Holding::provider)
            .thenComparing(Holding::value)
            .thenComparing(h -> h.grant().name())
            .thenComparing(Holding::permissionId);

    public static List<Holding> holdings(RepoPermissionService permissions, ReadOnlyUserStore users) {
        Map<String, Boolean> canAct = new HashMap<>();
        List<Holding> rows = new ArrayList<>();
        for (RepoPermission p : permissions.findAll()) {
            rows.add(new Holding(
                    p.getUsername(),
                    Via.DIRECT,
                    null,
                    p.getProvider(),
                    p.getTarget(),
                    p.getValue(),
                    p.getMatchType(),
                    p.getGrant(),
                    p.getSource().name(),
                    p.getGrantedBy(),
                    p.getGrantedAt(),
                    canAct(p.getUsername(), users, canAct),
                    p.getId()));
        }
        GroupPermissionStore groups = permissions.getGroupStore();
        if (groups != null) {
            for (PermissionGroup g : groups.findAllGroups()) {
                List<String> members = groups.findMembers(g.getId());
                for (GroupPermissionRule r : groups.findRulesForGroup(g.getId())) {
                    for (String member : members) {
                        rows.add(new Holding(
                                member,
                                Via.GROUP,
                                g.getName(),
                                r.getProvider(),
                                r.getTarget(),
                                r.getValue(),
                                r.getMatchType(),
                                r.getGrant(),
                                g.getSource().name(),
                                r.getGrantedBy(),
                                r.getGrantedAt(),
                                canAct(member, users, canAct),
                                r.getId()));
                    }
                }
            }
        }
        rows.sort(HOLDING_ORDER);
        return rows;
    }

    public static List<Membership> memberships(RepoPermissionService permissions) {
        GroupPermissionStore groups = permissions.getGroupStore();
        List<Membership> rows = new ArrayList<>();
        if (groups == null) {
            return rows;
        }
        for (PermissionGroup g : groups.findAllGroups()) {
            for (String member : groups.findMembers(g.getId())) {
                rows.add(new Membership(g.getId(), g.getName(), g.getSource().name(), member));
            }
        }
        rows.sort(Comparator.comparing(Membership::group).thenComparing(Membership::member));
        return rows;
    }

    private static boolean canAct(String username, ReadOnlyUserStore users, Map<String, Boolean> cache) {
        return cache.computeIfAbsent(
                username,
                u -> users.findByUsername(u)
                        .map(e -> Roles.canAct(e.getRoles()))
                        .orElse(false));
    }
}
