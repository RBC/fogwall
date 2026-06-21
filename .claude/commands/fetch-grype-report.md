# Fetch Latest Grype Container Scan Report

Fetch the latest grype container scan report from GitHub Actions and update the Dockerfile base image SHAs to address
any CVEs.

## Steps

1. Find the latest completed container scan run and download the report into a temp directory:

   ```
   gh run list --workflow=container-scan.yml --status=completed --limit=5 --json databaseId,displayTitle,createdAt,conclusion
   GRYPE_TMP=$(mktemp -d)
   gh run download <RUN_ID> -n grype-container-scan -D "$GRYPE_TMP"
   cat "$GRYPE_TMP/grype-report.txt"
   ```

2. Analyze the report:
   - All current CVEs are in `openjdk` or Ubuntu base packages — both are fixed by updating the base image
   - Check the "FIXED IN" column: if `*21.0.X` is listed, a new temurin release is available
   - If no fix is listed, the CVE cannot be addressed by a base image bump
   - **If CVEs remain after a digest bump:** the temurin Docker image may not yet include the upstream JDK fix. OpenJDK
     releases and the corresponding `eclipse-temurin` Docker image publish are independent — there is typically a lag of
     days to weeks. Check https://github.com/adoptium/containers for open issues or pending PRs tracking the update.

3. Pull the latest temurin images and extract the multi-arch index digests:

   ```bash
   CONTAINER_TOOL=$(command -v docker || command -v podman)

   for IMG in eclipse-temurin:21-jre eclipse-temurin:21-jdk; do
     $CONTAINER_TOOL pull docker.io/$IMG --quiet
     PLATFORM_DIGESTS=$($CONTAINER_TOOL manifest inspect docker.io/$IMG | python3 -c "import json,sys; [print(m['digest']) for m in json.load(sys.stdin)['manifests']]")
     $CONTAINER_TOOL image inspect docker.io/$IMG --format '{{.RepoDigests}}' \
       | tr ' ' '\n' | sed 's/.*@//' | tr -d '[]' \
       | while read d; do
           [ -n "$d" ] && ! echo "$PLATFORM_DIGESTS" | grep -qF "$d" && echo "$IMG  sha256: $d"
         done
   done
   ```

4. Update `Dockerfile` — replace both `@sha256:` pins:
   - Build stage: `FROM docker.io/eclipse-temurin:21-jdk@sha256:<new>`
   - Runtime stage: `FROM docker.io/eclipse-temurin:21-jre@sha256:<new>`

5. Optionally verify locally that the Dockerfile changes resolve the findings. Build the image first, then scan it
   (`fail-on-severity`, `sort-by`, and `output-template-file` come from `.grype.yaml`):

   ```
   CONTAINER_TOOL=$(command -v docker || command -v podman)
   $CONTAINER_TOOL build -t fogwall:local-verify .
   SCAN_TMP=$(mktemp -d)
   grype fogwall:local-verify --config .grype.yaml -o "template=$SCAN_TMP/report.txt" -o "json=$SCAN_TMP/report.json"
   cat "$SCAN_TMP/report.txt"
   ```

6. Commit on a new branch:

   ```
   fix: bump temurin base images to resolve CVEs

   Addresses: <list CVE IDs from report>
   closes #<issue number if one exists>
   ```
