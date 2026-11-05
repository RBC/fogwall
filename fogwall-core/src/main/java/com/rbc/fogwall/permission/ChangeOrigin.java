package com.rbc.fogwall.permission;

/** The surface an access change came in through. */
public enum ChangeOrigin {
    DASHBOARD,
    API,
    CONFIG,
    /** Roles an identity provider asserted at sign-in. */
    IDP
}
