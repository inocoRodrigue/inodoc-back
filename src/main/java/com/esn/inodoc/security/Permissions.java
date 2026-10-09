package com.esn.inodoc.security;

/**
 * RBAC permission codes, used in @PreAuthorize annotations.
 */
public final class Permissions {

    public static final String USER_READ = "USER_READ";
    public static final String USER_MANAGE = "USER_MANAGE";

    public static final String PROFILE_READ = "PROFILE_READ";
    public static final String PROFILE_WRITE = "PROFILE_WRITE";

    public static final String SHARE_MANAGE = "SHARE_MANAGE";

    public static final String DOCUMENT_GENERATE = "DOCUMENT_GENERATE";

    public static final String SETTINGS_MANAGE = "SETTINGS_MANAGE";

    private Permissions() {
    }
}
