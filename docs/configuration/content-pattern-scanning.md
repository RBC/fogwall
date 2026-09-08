# Content-pattern scanning

_Available since v1.3.0._

Push-level check applied against both the aggregate diff and every pushed commit's message. Flags structured identifier
content (national ID numbers, IBANs, credit card numbers, crypto wallet addresses, etc.) using fogwall's built-in
pattern bundles — distinct from [secret scanning](secret-scanning.md), which targets credential-shaped content (API
keys, tokens). Every bundle here requires a structural checksum or Presidio-derived regex match, not free-text PII
detection (names, addresses, emails) — that class needs NLP/NER to keep an acceptable false-positive rate, which is out
of scope; see [Design notes](#design-notes) below.

**WARN-only on the push path** — a match is recorded as a `WARN` step for the reviewer to see, it never blocks the push.
These patterns have a real false-positive rate (a bare regex match on a short numeric identifier can't be fully
disambiguated from unrelated numbers), and there's currently no override path for a wrongly-blocked push. Since every
push already requires a human reviewer to look at it, WARN gets the finding in front of them without the downside of
blocking on a false positive.

**Blocking on the proposal path** — that reasoning depends on the reviewer, and a proposal has none: it is forwarded to
the upstream or refused, with no held state to annotate. A warning recorded against a merge request description that is
already published is not a control, so a match there refuses the proposal (`REJECTED`) the same way a blocked term or a
detected secret does. See [Content inspection](../admin/proposals.md#content-inspection).

Bundle content (regexes, context keywords, structural validators like Luhn/IBAN/Base58Check checksums) is hand-ported
from [data-privacy-stack/presidio](https://github.com/data-privacy-stack/presidio) (MIT licensed) where noted below —
see `PROVENANCE.md` alongside the bundle resources in the fogwall source tree for exact provenance, including the small
number of validators (US bank routing, Bitcoin SegWit/Taproot, Ethereum) that aren't from Presidio. fogwall does not run
Presidio itself; only the matching logic is translated to Java.

```yaml
content-patterns:
  enabled: true
  bundles:
    - national-id-all-geos # every national-id-<cc> bundle below
    - generic-iban
    - generic-crypto-wallet
    # generic-credit-card and generic-us-bank-routing use a Luhn-strength (mod-10) checksum - noisier than the
    # above, so they're opt-in only; add them individually, or use the generic-all alias for all four generic
    # bundles at once.
  scan-diff: true # set false to skip scanning the push diff
  scan-commit-messages: true # set false to skip scanning commit messages
  scan-proposals: true # set false to skip scanning proposal titles, descriptions and comments
```

`scan-diff`/`scan-commit-messages`/`scan-proposals` independently gate the three content sources - all default `true`,
so selecting a bundle covers every surface it can appear on. An operator who considers commit messages low-risk (or
wants to reduce push-summary noise) can disable that half without affecting diff scanning, and vice versa. This is
distinct from disabling a bundle: the bundle selection still applies to whichever source(s) remain enabled.

## Available bundles

Two tiers: `national-id-<cc>` bundles (ISO 3166-1 alpha-2 country codes) each cover a single country's
national-identity-registry number — not that country's full PII catalog. `generic-<type>` bundles cover a data type that
isn't tied to a jurisdiction. Two group aliases are always available: `national-id-all-geos` (every `national-id-*`
bundle) and `generic-all` (every `generic-*` bundle).

### National ID bundles

| Bundle           | Jurisdiction   | Detects                                                                                  |
| ---------------- | -------------- | ---------------------------------------------------------------------------------------- |
| `national-id-ca` | Canada         | Social Insurance Number (SIN)                                                            |
| `national-id-us` | United States  | Social Security Number (SSN)                                                             |
| `national-id-gb` | United Kingdom | National Insurance Number (NINO); NHS Number                                             |
| `national-id-au` | Australia      | Tax File Number (TFN); Australian Business Number (ABN); Australian Company Number (ACN) |
| `national-id-de` | Germany        | Rentenversicherungsnummer                                                                |
| `national-id-in` | India          | Aadhaar Number                                                                           |
| `national-id-sg` | Singapore      | NRIC/FIN Number                                                                          |
| `national-id-za` | South Africa   | South African ID Number                                                                  |
| `national-id-es` | Spain          | NIF Number                                                                               |
| `national-id-se` | Sweden         | Personnummer                                                                             |
| `national-id-tr` | Turkey         | National ID Number (TC Kimlik No)                                                        |
| `national-id-fi` | Finland        | Personal Identity Code (Henkilötunnus)                                                   |
| `national-id-it` | Italy          | Fiscal Code (Codice Fiscale)                                                             |
| `national-id-kr` | South Korea    | Resident Registration Number                                                             |
| `national-id-ng` | Nigeria        | National Identification Number                                                           |
| `national-id-ph` | Philippines    | UMID Number                                                                              |
| `national-id-th` | Thailand       | National ID Number                                                                       |

### Generic bundles

| Bundle                    | Enabled by default | Detects                                                                                               | Checksum strength                    |
| ------------------------- | :----------------: | ----------------------------------------------------------------------------------------------------- | ------------------------------------ |
| `generic-iban`            |        Yes         | IBAN                                                                                                  | ISO 7064 mod-97-10 (strong)          |
| `generic-crypto-wallet`   |        Yes         | Bitcoin (legacy Base58Check, SegWit/Taproot Bech32); Ethereum (EIP-55, checksum-cased addresses only) | SHA-256d / BCH / Keccak-256 (strong) |
| `generic-credit-card`     |    No — opt in     | Credit card number                                                                                    | Luhn (mod-10, weak)                  |
| `generic-us-bank-routing` |    No — opt in     | US bank routing number (ABA)                                                                          | ABA weighted mod-10 (weak)           |

## Design notes

Presidio's recognizer catalog goes well beyond what fogwall ports: unstructured categories like names, physical
addresses, phone numbers, and email addresses rely on Presidio's NLP/NER pipeline to keep an acceptable false-positive
rate, not a checksum. fogwall has no NLP pipeline (that's the dependency this feature deliberately avoided — see
`PROVENANCE.md`), so only checksum-or-strict-structural-regex data types are in scope. If a data type doesn't have a
real checksum, it isn't a good fit for this WARN-only, regex-based mechanism.
