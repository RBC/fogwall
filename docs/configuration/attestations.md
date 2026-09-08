# Attestations

Attestation questions are presented to reviewers in the dashboard approval form. All configured questions must be
answered before the reviewer can submit an approval. Attestations are hot-reloadable.

```yaml
attestations:
  - id: reviewed-content
    type: checkbox
    label: "I have reviewed the diff and it contains no sensitive or proprietary information"
    required: true

  - id: policy-compliance
    type: checkbox
    label: "This push complies with our open source contribution policy"
    required: true

  - id: ticket-ref
    type: text
    label: "Internal ticket or justification reference"
    required: false

  - id: risk-level
    type: dropdown
    label: "Estimated risk level for this change"
    options:
      - Low
      - Medium
      - High
    required: true
    tooltip: "Select the risk level based on the scope and nature of the change"

  - id: policy-review
    type: checkbox
    label: "I have reviewed the applicable policy"
    required: true
    links:
      - text: "Open source contribution policy"
        url: "https://policy.example.com/open-source"
      - text: "Data classification guide"
        url: "https://policy.example.com/data-classification"
```

## Attestation properties

| Property   | Type    | Default    | Description                                                              |
| ---------- | ------- | ---------- | ------------------------------------------------------------------------ |
| `id`       | string  | —          | Unique key used to store the reviewer's answer in the push record        |
| `type`     | string  | `checkbox` | Input type: `checkbox`, `text`, or `dropdown`                            |
| `label`    | string  | —          | Question text shown in the review form                                   |
| `required` | boolean | `false`    | Whether the question must be answered before the reviewer can submit     |
| `links`    | list    | `[]`       | Policy/reference links (`text` + `url` each) rendered below the question |
| `options`  | list    | _(empty)_  | Choices for `dropdown` type; ignored for other types                     |
| `tooltip`  | string  | _(none)_   | Optional help text shown alongside the question                          |

Set `attestations: []` (or omit the key) to disable attestations entirely.
