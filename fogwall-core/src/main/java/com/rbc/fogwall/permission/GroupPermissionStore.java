package com.rbc.fogwall.permission;

import java.util.List;
import java.util.Optional;

public interface GroupPermissionStore {

    default void initialize() {}

    // ---- groups ----

    void saveGroup(PermissionGroup group);

    void updateGroup(String id, String name, String description);

    void deleteGroup(String id);

    Optional<PermissionGroup> findGroupById(String id);

    Optional<PermissionGroup> findGroupByName(String name);

    List<PermissionGroup> findAllGroups();

    // ---- members ----

    void addMember(String groupId, String username);

    void removeMember(String groupId, String username);

    List<String> findMembers(String groupId);

    /** Returns the IDs of all groups the given user belongs to. */
    List<String> findGroupIdsForUser(String username);

    // ---- rules ----

    void saveRule(GroupPermissionRule rule);

    void deleteRule(String ruleId);

    Optional<GroupPermissionRule> findRuleById(String id);

    List<GroupPermissionRule> findRulesForGroup(String groupId);

    /** Returns all rules across all groups for the given provider. */
    List<GroupPermissionRule> findRulesByProvider(String provider);
}
