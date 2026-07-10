package com.rbc.fogwall.permission;

import com.rbc.fogwall.db.model.MatchTarget;
import com.rbc.fogwall.db.model.MatchType;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single repository permission rule attached to a {@link PermissionGroup}. Semantically identical to
 * {@link RepoPermission} but scoped to the group rather than an individual user.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class GroupPermissionRule {

    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String groupId;
    private String provider;

    @Builder.Default
    private MatchTarget target = MatchTarget.SLUG;

    private String value;

    @Builder.Default
    private MatchType matchType = MatchType.GLOB;

    @Builder.Default
    private RepoPermission.Grant grant = RepoPermission.Grant.PUSH;
}
