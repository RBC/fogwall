package com.rbc.fogwall.permission;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGroupPermissionStore implements GroupPermissionStore {

    private final Map<String, PermissionGroup> groups = new ConcurrentHashMap<>();
    private final Map<String, List<String>> members = new ConcurrentHashMap<>(); // groupId → usernames
    private final Map<String, GroupPermissionRule> rules = new ConcurrentHashMap<>();

    @Override
    public void saveGroup(PermissionGroup group) {
        groups.put(group.getId(), group);
        members.putIfAbsent(group.getId(), new ArrayList<>());
    }

    @Override
    public void updateGroup(String id, String name, String description) {
        groups.computeIfPresent(
                id,
                (k, g) -> PermissionGroup.builder()
                        .id(g.getId())
                        .name(name)
                        .description(description)
                        .source(g.getSource())
                        .build());
    }

    @Override
    public void deleteGroup(String id) {
        groups.remove(id);
        members.remove(id);
        rules.values().removeIf(r -> id.equals(r.getGroupId()));
    }

    @Override
    public Optional<PermissionGroup> findGroupById(String id) {
        return Optional.ofNullable(groups.get(id));
    }

    @Override
    public Optional<PermissionGroup> findGroupByName(String name) {
        return groups.values().stream().filter(g -> name.equals(g.getName())).findFirst();
    }

    @Override
    public List<PermissionGroup> findAllGroups() {
        return groups.values().stream()
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .toList();
    }

    @Override
    public void addMember(String groupId, String username) {
        members.computeIfAbsent(groupId, k -> new ArrayList<>()).add(username);
    }

    @Override
    public void removeMember(String groupId, String username) {
        var list = members.get(groupId);
        if (list != null) list.remove(username);
    }

    @Override
    public List<String> findMembers(String groupId) {
        return List.copyOf(members.getOrDefault(groupId, List.of()));
    }

    @Override
    public List<String> findGroupIdsForUser(String username) {
        return members.entrySet().stream()
                .filter(e -> e.getValue().contains(username))
                .map(Map.Entry::getKey)
                .toList();
    }

    @Override
    public void saveRule(GroupPermissionRule rule) {
        rules.put(rule.getId(), rule);
    }

    @Override
    public void deleteRule(String ruleId) {
        rules.remove(ruleId);
    }

    @Override
    public Optional<GroupPermissionRule> findRuleById(String id) {
        return Optional.ofNullable(rules.get(id));
    }

    @Override
    public List<GroupPermissionRule> findRulesForGroup(String groupId) {
        return rules.values().stream()
                .filter(r -> groupId.equals(r.getGroupId()))
                .toList();
    }

    @Override
    public List<GroupPermissionRule> findRulesByProvider(String provider) {
        return rules.values().stream()
                .filter(r -> provider.equals(r.getProvider()))
                .toList();
    }
}
