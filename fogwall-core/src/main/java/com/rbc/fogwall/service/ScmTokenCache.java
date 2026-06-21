package com.rbc.fogwall.service;

import java.util.Optional;

public interface ScmTokenCache {

    Optional<String> lookup(String provider, String tokenHash);

    void store(String provider, String tokenHash, String proxyUsername);

    void evictByUsername(String provider, String proxyUsername);
}
