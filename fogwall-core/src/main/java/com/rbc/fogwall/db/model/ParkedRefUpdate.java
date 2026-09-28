package com.rbc.fogwall.db.model;

import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * One ref update a client pushed to a parked push, as the client sent it.
 *
 * @param refName the full ref name, e.g. {@code refs/heads/main}
 * @param oldId the tip the client expected the ref to have; all zeros when creating it
 * @param newId the tip the client pushed; all zeros when deleting the ref
 * @param type how the client's update relates to the old tip
 */
public record ParkedRefUpdate(String refName, String oldId, String newId, ReceiveCommand.Type type) {}
