package com.rbc.fogwall.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link YamlStructureValidator}. */
class YamlStructureValidatorTest {

    @Test
    void aliasToList_resolvesAcrossSections(@TempDir Path tempDir) throws IOException {
        // A YAML anchor/alias de-duplicating a List<...> value across two sections - here the same allowed-domain
        // matcher applied to both the author and committer email checks - must resolve to the anchored list, not be
        // handed to the deserializer as the literal string "*shared-domain-rules".
        Path file = tempDir.resolve("fogwall.yml");
        Files.writeString(file, """
                commit:
                  author:
                    email:
                      rules: &shared-domain-rules
                        - action: allow
                          field: domain
                          match: literal
                          value: example.com
                  committer:
                    email:
                      rules: *shared-domain-rules
                """);

        assertDoesNotThrow(() -> YamlStructureValidator.validateFile(file));
    }

    @Test
    void unknownKey_stillFailsValidation(@TempDir Path tempDir) throws IOException {
        Path file = tempDir.resolve("fogwall.yml");
        Files.writeString(file, "not-a-real-top-level-key: true\n");

        assertThrows(IllegalStateException.class, () -> YamlStructureValidator.validateFile(file));
    }
}
