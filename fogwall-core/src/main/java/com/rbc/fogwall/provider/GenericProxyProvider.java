package com.rbc.fogwall.provider;

import java.net.URI;
import lombok.Builder;

public class GenericProxyProvider extends AbstractFogwallProvider {

    private final int blockedInfoRefsStatus;

    @Builder
    GenericProxyProvider(String name, String type, URI uri, String pathSuffix, Integer blockedInfoRefsStatus) {
        super(name, type != null ? type : name, uri, pathSuffix);
        this.blockedInfoRefsStatus = blockedInfoRefsStatus != null ? blockedInfoRefsStatus : 403;
    }

    @Override
    public int getBlockedInfoRefsStatus() {
        return blockedInfoRefsStatus;
    }
}
