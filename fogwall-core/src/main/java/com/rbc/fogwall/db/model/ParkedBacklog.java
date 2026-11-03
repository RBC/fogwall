package com.rbc.fogwall.db.model;

/**
 * The parked pushes whose pack is stored, and the bytes those packs hold.
 *
 * @param pushes stored packs
 * @param bytes their total size
 */
public record ParkedBacklog(long pushes, long bytes) {}
