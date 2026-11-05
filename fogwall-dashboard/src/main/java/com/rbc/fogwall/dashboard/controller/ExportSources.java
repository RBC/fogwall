package com.rbc.fogwall.dashboard.controller;

import com.rbc.fogwall.dashboard.export.ExportFields;
import com.rbc.fogwall.dashboard.export.ExportSource;
import com.rbc.fogwall.dashboard.export.GrantSnapshot;
import com.rbc.fogwall.db.FetchStore;
import com.rbc.fogwall.db.PushStore;
import com.rbc.fogwall.db.ScmApiActionStore;
import com.rbc.fogwall.db.model.FetchActivity;
import com.rbc.fogwall.db.model.FetchActivityQuery;
import com.rbc.fogwall.db.model.PushQuery;
import com.rbc.fogwall.db.model.PushRecord;
import com.rbc.fogwall.db.model.RecordCursor;
import com.rbc.fogwall.db.model.ScmApiActionQuery;
import com.rbc.fogwall.db.model.ScmApiActionRecord;
import com.rbc.fogwall.permission.AccessChangeService;
import com.rbc.fogwall.permission.MembershipChangeEvent;
import com.rbc.fogwall.permission.MembershipChangeQuery;
import com.rbc.fogwall.permission.PermissionChangeEvent;
import com.rbc.fogwall.permission.PermissionChangeQuery;
import com.rbc.fogwall.permission.RoleChangeEvent;
import com.rbc.fogwall.permission.RoleChangeQuery;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Each record type as an {@link ExportSource}, read from its store in cursor order with the query its listing builds.
 * The query's own limit, offset and cursor are replaced batch by batch, so an export reads every matching row.
 */
final class ExportSources {

    static final String PUSHES = "pushes";
    static final String SCM_API_ACTIONS = "scm-api-actions";
    static final String FETCH_ACTIVITY = "fetch-activity";
    static final String PERMISSION_CHANGES = "permission-changes";
    static final String MEMBERSHIP_CHANGES = "membership-changes";
    static final String ROLE_CHANGES = "role-changes";
    static final String GRANTS = "grants";
    static final String GROUP_MEMBERS = "group-members";

    private ExportSources() {}

    static ExportSource<PushRecord> pushes(PushStore store, PushQuery.PushQueryBuilder query, List<String> columns) {
        return ExportSource.paged(
                PUSHES,
                ExportFields.PUSHES,
                columns,
                (after, n) -> store.find(query.after(after).limit(n).offset(0).build()),
                r -> new RecordCursor(r.getTimestamp(), r.getId()),
                r -> true);
    }

    static ExportSource<ScmApiActionRecord> scmApiActions(
            ScmApiActionStore store, ScmApiActionQuery.ScmApiActionQueryBuilder query, List<String> columns) {
        return ExportSource.paged(
                SCM_API_ACTIONS,
                ExportFields.SCM_API_ACTIONS,
                columns,
                (after, n) -> store.find(query.after(after).limit(n).offset(0).build()),
                r -> new RecordCursor(r.getTimestamp(), r.getId()),
                r -> true);
    }

    static ExportSource<FetchActivity> fetchActivity(
            FetchStore store, FetchActivityQuery.FetchActivityQueryBuilder query, List<String> columns) {
        return ExportSource.paged(
                FETCH_ACTIVITY,
                ExportFields.FETCH_ACTIVITY,
                columns,
                (after, n) -> store.find(query.after(after).limit(n).offset(0).build()),
                r -> new RecordCursor(r.getBucketStart(), r.getId()),
                r -> true);
    }

    /** @param repoPath when present, only the grants whose pattern applies to that repository */
    static ExportSource<PermissionChangeEvent> permissionChanges(
            AccessChangeService changes,
            PermissionChangeQuery.PermissionChangeQueryBuilder query,
            Optional<String> repoPath,
            List<String> columns) {
        Predicate<PermissionChangeEvent> keep = repoPath.<Predicate<PermissionChangeEvent>>map(
                        path -> e -> changes.appliesTo(e, path))
                .orElse(e -> true);
        return ExportSource.paged(
                PERMISSION_CHANGES,
                ExportFields.PERMISSION_CHANGES,
                columns,
                (after, n) -> changes.findPermissionChanges(
                        query.after(after).limit(n).offset(0).build()),
                PermissionChangeEvent::cursor,
                keep);
    }

    static ExportSource<MembershipChangeEvent> membershipChanges(
            AccessChangeService changes,
            MembershipChangeQuery.MembershipChangeQueryBuilder query,
            List<String> columns) {
        return ExportSource.paged(
                MEMBERSHIP_CHANGES,
                ExportFields.MEMBERSHIP_CHANGES,
                columns,
                (after, n) -> changes.findMembershipChanges(
                        query.after(after).limit(n).offset(0).build()),
                MembershipChangeEvent::cursor,
                e -> true);
    }

    static ExportSource<RoleChangeEvent> roleChanges(
            AccessChangeService changes, RoleChangeQuery.RoleChangeQueryBuilder query, List<String> columns) {
        return ExportSource.paged(
                ROLE_CHANGES,
                ExportFields.ROLE_CHANGES,
                columns,
                (after, n) -> changes.findRoleChanges(
                        query.after(after).limit(n).offset(0).build()),
                RoleChangeEvent::cursor,
                e -> true);
    }

    static ExportSource<GrantSnapshot.Holding> grants(List<GrantSnapshot.Holding> rows, List<String> columns) {
        return ExportSource.snapshot(GRANTS, ExportFields.GRANTS, columns, rows);
    }

    static ExportSource<GrantSnapshot.Membership> groupMembers(
            List<GrantSnapshot.Membership> rows, List<String> columns) {
        return ExportSource.snapshot(GROUP_MEMBERS, ExportFields.GROUP_MEMBERS, columns, rows);
    }
}
