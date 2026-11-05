package com.rbc.fogwall.dashboard.export;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ExportWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-05T14:00:00.123Z");

    /** A record with a nested list, a nested object, and a field that is not exported. */
    public record Row(String id, String note, List<String> tags, Map<String, Integer> counts, String hidden) {}

    private static final List<String> FIELDS = List.of("id", "note", "tags", "counts");

    private static Row row(String id, String note) {
        return new Row(id, note, List.of("a", "b"), Map.of("n", 1), "not exported");
    }

    private static ExportZip write(int partRows, ExportFormat format, List<String> columns, List<Row> rows)
            throws Exception {
        var out = new ByteArrayOutputStream();
        new ExportWriter(partRows, Clock.fixed(NOW, ZoneOffset.UTC))
                .write(
                        out,
                        List.of(ExportSource.snapshot("rows", FIELDS, columns, rows)),
                        format,
                        Map.of("owner", "acme"));
        return ExportZip.read(out.toByteArray());
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rowsSplitIntoFixedSizeParts_andTheManifestCountsAndHashesEachPart() throws Exception {
        ExportZip zip = write(
                2,
                ExportFormat.CSV,
                FIELDS,
                List.of(row("1", "x"), row("2", "x"), row("3", "x"), row("4", "x"), row("5", "x")));

        assertEquals(
                List.of("rows/part-00001.csv", "rows/part-00002.csv", "rows/part-00003.csv", "manifest.json"),
                List.copyOf(zip.entries().keySet()));
        assertEquals("manifest.json", zip.lastEntry());

        JsonNode manifest = zip.manifest();
        assertEquals(NOW.toString(), manifest.get("generatedAt").asString());
        assertEquals("CSV", manifest.get("format").asString());
        assertEquals("acme", manifest.get("filters").get("owner").asString());
        JsonNode type = manifest.get("types").get(0);
        assertEquals("rows", type.get("type").asString());
        assertEquals(5, type.get("rows").asLong());
        assertTrue(type.get("snapshot").asBoolean());
        long[] counts = {2, 2, 1};
        for (int i = 0; i < 3; i++) {
            JsonNode part = type.get("parts").get(i);
            assertEquals(counts[i], part.get("rows").asLong());
            assertEquals(
                    sha256(zip.entries().get(part.get("file").asString())),
                    part.get("sha256").asString());
        }
    }

    @Test
    void csv_chosenColumnsInOrder_nestedValuesAsJson_andEveryPartHasAHeader() throws Exception {
        ExportZip zip = write(10, ExportFormat.CSV, List.of("tags", "id"), List.of(row("1", "x")));

        assertEquals("tags,id\r\n\"[\"\"a\"\",\"\"b\"\"]\",1\r\n", zip.entries().get("rows/part-00001.csv"));
        assertEquals(
                List.of("tags", "id"),
                JsonMapper.builder()
                        .build()
                        .convertValue(zip.manifest().get("types").get(0).get("columns"), List.class));
    }

    @Test
    void csv_quotesWhatRfc4180Requires_andNeutralisesFormulaCells() throws Exception {
        ExportZip zip = write(
                10,
                ExportFormat.CSV,
                List.of("id", "note"),
                List.of(
                        row("1", "a,b"),
                        row("2", "say \"hi\""),
                        row("3", "two\nlines"),
                        row("4", "=HYPERLINK(\"http://evil\")"),
                        row("5", "+1"),
                        row("6", "-2"),
                        row("7", "@cmd"),
                        row("8", "plain")));

        assertEquals(
                "id,note\r\n"
                        + "1,\"a,b\"\r\n"
                        + "2,\"say \"\"hi\"\"\"\r\n"
                        + "3,\"two\nlines\"\r\n"
                        + "4,\"'=HYPERLINK(\"\"http://evil\"\")\"\r\n"
                        + "5,'+1\r\n"
                        + "6,'-2\r\n"
                        + "7,'@cmd\r\n"
                        + "8,plain\r\n",
                zip.entries().get("rows/part-00001.csv"));
        assertEquals(
                ExportWriter.CSV_FORMULA_ESCAPING,
                zip.manifest().get("csvFormulaEscaping").asString());
    }

    @Test
    void json_writesOneFullRecordPerLine_withExactlyTheExportedFields() throws Exception {
        ExportZip zip = write(10, ExportFormat.JSON, FIELDS, List.of(row("1", "=kept exactly"), row("2", "x")));

        String[] lines = zip.entries().get("rows/part-00001.jsonl").split("\n");
        assertEquals(2, lines.length);
        JsonNode first = JsonMapper.builder().build().readTree(lines[0]);
        assertEquals(List.of("id", "note", "tags", "counts"), List.copyOf(first.propertyNames()));
        assertEquals("=kept exactly", first.get("note").asString());
        assertEquals("b", first.get("tags").get(1).asString());
        assertEquals(1, first.get("counts").get("n").asInt());
        assertNull(zip.manifest().get("csvFormulaEscaping"));
    }

    @Test
    void noRows_writesNoParts_andAManifestSayingSo() throws Exception {
        ExportZip zip = write(10, ExportFormat.CSV, FIELDS, List.of());

        assertEquals(List.of("manifest.json"), List.copyOf(zip.entries().keySet()));
        JsonNode type = zip.manifest().get("types").get(0);
        assertEquals(0, type.get("rows").asLong());
        assertTrue(type.get("parts").isEmpty());
    }
}
