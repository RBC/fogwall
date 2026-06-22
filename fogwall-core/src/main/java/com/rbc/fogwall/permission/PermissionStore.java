package com.rbc.fogwall.permission;

import java.util.List;
import java.util.Optional;

public interface PermissionStore<T extends FogwallPermission> {
    /** Called once at startup; implementations may use this to create schema or seed data. */
    default void initialize() {}

    void save(T permission);

    void delete(String id);

    Optional<T> findById(String id);

    List<T> findAll();

    List<T> findByUsername(String username);

    List<T> findByProvider(String provider);
}
