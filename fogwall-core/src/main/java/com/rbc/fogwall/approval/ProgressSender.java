package com.rbc.fogwall.approval;

@FunctionalInterface
public interface ProgressSender {
    void send(String message);
}
