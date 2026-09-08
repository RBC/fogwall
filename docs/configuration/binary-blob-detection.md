# Binary blob detection

_Available since v1.3.0._

Push-level check applied once per push against the aggregate diff (all commits combined) — same scope as
[diff scan](diff-scan.md). Flags added/modified blobs that exceed a size threshold or match a denied MIME type.
ENFORCE-only: a match always blocks the push (there is no advisory/WARN mode yet).

MIME type classification sniffs the first few bytes of each blob's content against a built-in table of magic-byte
signatures (the same technique tools like `file`/libmagic use, at a much smaller scale) — file extensions are never
consulted, since they're trivially renamed and unreliable across operating systems. Only a small, bounded header is read
per blob; blob content is never fully loaded. Note that ZIP-based Office formats (`.docx`/`.xlsx`/`.pptx`) share the
same container signature as plain `.zip`/`.jar` archives and cannot be distinguished from magic bytes alone — all are
classified as `application/zip`.

```yaml
binary-blob:
  enabled: true # on by default — the size threshold alone is a useful safety net out of the box
  max-size-bytes: 52428800 # 50MiB; 0 = no size limit
  deny-mime-types: # PDF and ZIP-family denied by default; further types are a policy choice
    - application/pdf
    - application/zip
    # - application/x-executable
    # - application/x-msdownload
```

## Detectable MIME types

`deny-mime-types` entries must match one of the values below exactly — these are the only content types the built-in
magic-byte signature table can identify. Anything not in this list is never denied by MIME type (though it may still be
denied by `max-size-bytes`).

| MIME type                         | Matches                                                                                                                        |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `application/pdf`                 | PDF documents                                                                                                                  |
| `application/zip`                 | ZIP archives, JARs, and OOXML Office docs (`.docx`/`.xlsx`/`.pptx` — indistinguishable from plain zip at the magic-byte level) |
| `application/gzip`                | gzip-compressed files (`.gz`, `.tar.gz`)                                                                                       |
| `application/x-7z-compressed`     | 7-Zip archives                                                                                                                 |
| `application/vnd.rar`             | RAR archives                                                                                                                   |
| `application/x-executable`        | ELF binaries (Linux executables/shared objects)                                                                                |
| `application/x-msdownload`        | Windows PE binaries (`.exe`, `.dll`)                                                                                           |
| `application/vnd.sqlite3`         | SQLite database files                                                                                                          |
| `application/java-vm`             | Compiled Java class files                                                                                                      |
| `application/wasm`                | WebAssembly binaries                                                                                                           |
| `application/zstd`                | Zstandard-compressed files                                                                                                     |
| `application/x-xz`                | XZ-compressed files                                                                                                            |
| `application/x-bzip2`             | Bzip2-compressed files                                                                                                         |
| `application/vnd.apache.parquet`  | Apache Parquet columnar data files                                                                                             |
| `application/x-java-keystore`     | Java Keystore (`.jks`)                                                                                                         |
| `application/x-java-jce-keystore` | Java Cryptography Extension Keystore (`.jceks`)                                                                                |
| `application/x-qemu-disk`         | QEMU/QCOW2 virtual disk images                                                                                                 |
| `application/x-hdf5`              | HDF5 data files (e.g. Keras model checkpoints)                                                                                 |
