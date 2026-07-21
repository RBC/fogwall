package com.rbc.fogwall.validation;

/**
 * A single content-pattern match that passed both the context-keyword check and structural validation (if the rule has
 * one). {@code matchedText} is the raw matched value - callers must route it through redaction before it's persisted
 * anywhere (push record, logs), never display it directly. See {@code pushContext.addSecretsToRedact}.
 */
public record ContentPatternFinding(String dataType, String jurisdiction, String matchedText) {}
