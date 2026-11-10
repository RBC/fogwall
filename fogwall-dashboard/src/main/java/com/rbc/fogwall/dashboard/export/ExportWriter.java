package com.rbc.fogwall.dashboard.export;

import java.io.BufferedWriter;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Writes an export as a zip: for each record type, a folder of parts of at most {@link #PART_ROWS} rows, then a
 * {@code manifest.json} naming the filters, the generation time, and each part's row count and SHA-256.
 *
 * <p>Rows stream straight from the stores to the response; nothing is held beyond one batch or written to disk. The
 * manifest is the last entry, so an export cut short — a dropped connection, a store error once the response has
 * started — is a zip with no manifest.
 *
 * <p>CSV values are the record's exported properties. A nested value (a list or an object) is its JSON text. A cell
 * that a spreadsheet would read as a formula — beginning {@code = + - @}, tab or carriage return — is prefixed with
 * {@code '}; the JSON format keeps every value exactly, and the manifest records the escaping.
 */
public final class ExportWriter {

    public static final int PART_ROWS = 100_000;

    static final String CSV_FORMULA_ESCAPING =
            "a cell beginning with = + - @, tab or carriage return is prefixed with ' so a spreadsheet reads it as text";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final int partRows;
    private final Clock clock;

    public ExportWriter() {
        this(PART_ROWS, Clock.systemUTC());
    }

    ExportWriter(int partRows, Clock clock) {
        this.partRows = partRows;
        this.clock = clock;
    }

    /** @param filters the filters the export was asked for, as given, recorded in the manifest */
    public void write(OutputStream out, List<ExportSource<?>> sources, ExportFormat format, Map<String, String> filters)
            throws IOException {
        Instant generatedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        ObjectNode manifest = MAPPER.createObjectNode();
        manifest.put("generatedAt", generatedAt.toString());
        manifest.put("format", format.name());
        ObjectNode filterNode = manifest.putObject("filters");
        filters.forEach(filterNode::put);
        manifest.put("partRows", partRows);
        if (format == ExportFormat.CSV) {
            manifest.put("csvFormulaEscaping", CSV_FORMULA_ESCAPING);
        }
        ArrayNode types = manifest.putArray("types");
        for (ExportSource<?> source : sources) {
            types.add(writeType(zip, source, format));
        }
        zip.putNextEntry(new ZipEntry("manifest.json"));
        zip.write(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest));
        zip.closeEntry();
        zip.finish();
        zip.flush();
    }

    private ObjectNode writeType(ZipOutputStream zip, ExportSource<?> source, ExportFormat format) throws IOException {
        ObjectNode entry = MAPPER.createObjectNode();
        entry.put("type", source.type());
        entry.put("snapshot", source.snapshot());
        ArrayNode columns = entry.putArray(format == ExportFormat.CSV ? "columns" : "fields");
        (format == ExportFormat.CSV ? source.columns() : source.fields()).forEach(columns::add);
        ArrayNode parts = entry.putArray("parts");
        long total = 0;
        Part part = null;
        var rows = source.rows();
        while (rows.hasNext()) {
            if (part == null || part.rows == partRows) {
                if (part != null) {
                    parts.add(part.close());
                }
                part = Part.open(zip, source, format, parts.size() + 1);
            }
            part.write(rows.next());
            total++;
        }
        if (part != null) {
            parts.add(part.close());
        }
        entry.put("rows", total);
        return entry;
    }

    /** One zip entry being written, hashed as it goes. */
    private static final class Part {
        private final ZipOutputStream zip;
        private final String file;
        private final MessageDigest digest;
        private final Writer writer;
        private final ExportSource<?> source;
        private final ExportFormat format;
        private long rows;

        private Part(ZipOutputStream zip, String file, ExportSource<?> source, ExportFormat format) {
            this.zip = zip;
            this.file = file;
            this.source = source;
            this.format = format;
            this.digest = sha256();
            this.writer = new BufferedWriter(
                    new OutputStreamWriter(new DigestOutputStream(new KeepOpen(zip), digest), StandardCharsets.UTF_8));
        }

        static Part open(ZipOutputStream zip, ExportSource<?> source, ExportFormat format, int number)
                throws IOException {
            String file = String.format("%s/part-%05d.%s", source.type(), number, format.extension());
            zip.putNextEntry(new ZipEntry(file));
            Part part = new Part(zip, file, source, format);
            if (format == ExportFormat.CSV) {
                part.writer.write(csvLine(source.columns()));
            }
            return part;
        }

        void write(Object row) throws IOException {
            ObjectNode node = MAPPER.valueToTree(row);
            if (format == ExportFormat.JSON) {
                ObjectNode kept = MAPPER.createObjectNode();
                for (String field : source.fields()) {
                    kept.set(field, node.get(field));
                }
                writer.write(MAPPER.writeValueAsString(kept));
                writer.write('\n');
            } else {
                writer.write(csvLine(
                        source.columns().stream().map(c -> cell(node.get(c))).toList()));
            }
            rows++;
        }

        ObjectNode close() throws IOException {
            writer.flush();
            zip.closeEntry();
            ObjectNode entry = MAPPER.createObjectNode();
            entry.put("file", file);
            entry.put("rows", rows);
            entry.put("sha256", HexFormat.of().formatHex(digest.digest()));
            return entry;
        }
    }

    /** A cell's text: empty for null, the value for a scalar, JSON text for a list or an object. */
    static String cell(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return "";
        }
        return value.isValueNode() ? value.asString() : MAPPER.writeValueAsString(value);
    }

    /** One RFC 4180 line, CRLF-terminated, with formula-leading cells neutralised. */
    static String csvLine(List<String> cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                line.append(',');
            }
            line.append(csvCell(cells.get(i)));
        }
        return line.append("\r\n").toString();
    }

    static String csvCell(String value) {
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;
        }
        boolean quote = v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        return quote ? '"' + v.replace("\"", "\"\"") + '"' : v;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JCA spec but is unavailable", e);
        }
    }

    /** Lets a part's writer flush into the zip without closing the zip stream. */
    private static final class KeepOpen extends FilterOutputStream {
        KeepOpen(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }
}
