{{/*
Chart name and version label.
*/}}
{{- define "fogwall.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Fully qualified app name — release name is already unique per install, so this chart's name
is used directly (no truncation dance needed for a single-service chart).
*/}}
{{- define "fogwall.fullname" -}}
{{- .Release.Name -}}
{{- end -}}

{{/*
Common labels applied to every resource.
*/}}
{{- define "fogwall.labels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
helm.sh/chart: {{ include "fogwall.chart" . }}
{{- end -}}

{{/*
Selector labels — must be a stable subset of fogwall.labels (no version, since that changes
across upgrades and Deployment selectors are immutable).
*/}}
{{- define "fogwall.selectorLabels" -}}
app.kubernetes.io/name: {{ .Chart.Name }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Default image repository for the selected variant. Only used when .Values.image.repository is
blank — an explicit value always wins (e.g. a private mirror of either image).
*/}}
{{- define "fogwall.defaultImageRepository" -}}
{{- if eq .Values.variant "server" -}}
ghcr.io/rbc/fogwall-server
{{- else -}}
ghcr.io/rbc/fogwall
{{- end -}}
{{- end -}}

{{/*
Fully qualified image reference.
*/}}
{{- define "fogwall.image" -}}
{{- $repo := .Values.image.repository -}}
{{- if not $repo -}}
{{- $repo = include "fogwall.defaultImageRepository" . -}}
{{- end -}}
{{- printf "%s:%s" $repo .Values.image.tag -}}
{{- end -}}

{{/*
Liveness/readiness probe block for the selected variant. An explicit .Values.livenessProbe /
.Values.readinessProbe always wins over the variant default. The server variant has no HTTP
health endpoint, so its default is a plain TCP check against the app port.
*/}}
{{- define "fogwall.probe" -}}
{{- $probe := index .Values (printf "%sProbe" .kind) -}}
{{- if $probe -}}
{{ toYaml $probe }}
{{- else if eq .Values.variant "server" -}}
tcpSocket:
  port: http
initialDelaySeconds: {{ eq .kind "liveness" | ternary 15 10 }}
periodSeconds: {{ eq .kind "liveness" | ternary 10 5 }}
{{- else -}}
httpGet:
  path: /api/health
  port: http
initialDelaySeconds: {{ eq .kind "liveness" | ternary 15 10 }}
periodSeconds: {{ eq .kind "liveness" | ternary 10 5 }}
{{- end -}}
{{- end -}}
