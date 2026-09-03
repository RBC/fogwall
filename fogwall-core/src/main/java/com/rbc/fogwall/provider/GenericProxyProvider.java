package com.rbc.fogwall.provider;

import java.net.URI;
import lombok.Builder;

public class GenericProxyProvider extends AbstractFogwallProvider {

    private final int blockedInfoRefsStatus;

    @Builder
    GenericProxyProvider(
            String name, String type, URI uri, String pathSuffix, Integer blockedInfoRefsStatus, URI sshUri) {
        super(name, type != null ? type : name, uri, pathSuffix);
        this.blockedInfoRefsStatus = blockedInfoRefsStatus != null ? blockedInfoRefsStatus : 403;
        this.sshUri = sshUri;
    }

    @Override
    public int getBlockedInfoRefsStatus() {
        return blockedInfoRefsStatus;
    }
}
