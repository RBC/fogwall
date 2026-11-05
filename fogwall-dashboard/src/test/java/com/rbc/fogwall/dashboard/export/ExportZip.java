package com.rbc.fogwall.dashboard.export;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** An export zip read back for assertions: each entry's text, in the order written. */
public record ExportZip(Map<String, String> entries) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public static ExportZip read(byte[] zip) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return new ExportZip(entries);
    }

    public JsonNode manifest() {
        return MAPPER.readTree(entries.get("manifest.json"));
    }

    public String lastEntry() {
        return entries.keySet().stream().reduce((a, b) -> b).orElseThrow();
    }
}
