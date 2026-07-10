package com.rbc.fogwall.permission;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A named group of users that share a common set of repository permission rules. Users assigned to a group inherit all
 * of the group's {@link GroupPermissionRule}s in addition to any directly-assigned {@link RepoPermission}s.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PermissionGroup {

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String name;
    private String description;

    @Builder.Default
    private Source source = Source.DB;

    public enum Source {
        CONFIG,
        DB
    }
}
