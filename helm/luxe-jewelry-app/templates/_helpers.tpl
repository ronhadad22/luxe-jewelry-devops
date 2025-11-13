{{/*
Expand the name of the chart.
*/}}
{{- define "luxe-jewelry-app.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "luxe-jewelry-app.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "luxe-jewelry-app.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "luxe-jewelry-app.labels" -}}
helm.sh/chart: {{ include "luxe-jewelry-app.chart" . }}
{{ include "luxe-jewelry-app.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- with .Values.commonLabels }}
{{ toYaml . }}
{{- end }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "luxe-jewelry-app.selectorLabels" -}}
app.kubernetes.io/name: {{ include "luxe-jewelry-app.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Backend labels
*/}}
{{- define "luxe-jewelry-app.backend.labels" -}}
{{ include "luxe-jewelry-app.labels" . }}
app.kubernetes.io/component: backend
{{- end }}

{{/*
Backend selector labels
*/}}
{{- define "luxe-jewelry-app.backend.selectorLabels" -}}
{{ include "luxe-jewelry-app.selectorLabels" . }}
app.kubernetes.io/component: backend
{{- end }}

{{/*
Auth service labels
*/}}
{{- define "luxe-jewelry-app.auth.labels" -}}
{{ include "luxe-jewelry-app.labels" . }}
app.kubernetes.io/component: auth
{{- end }}

{{/*
Auth service selector labels
*/}}
{{- define "luxe-jewelry-app.auth.selectorLabels" -}}
{{ include "luxe-jewelry-app.selectorLabels" . }}
app.kubernetes.io/component: auth
{{- end }}

{{/*
Frontend labels
*/}}
{{- define "luxe-jewelry-app.frontend.labels" -}}
{{ include "luxe-jewelry-app.labels" . }}
app.kubernetes.io/component: frontend
{{- end }}

{{/*
Frontend selector labels
*/}}
{{- define "luxe-jewelry-app.frontend.selectorLabels" -}}
{{ include "luxe-jewelry-app.selectorLabels" . }}
app.kubernetes.io/component: frontend
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "luxe-jewelry-app.serviceAccountName" -}}
{{- if .Values.security.serviceAccount.create }}
{{- default (include "luxe-jewelry-app.fullname" .) .Values.security.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.security.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create image name with registry and tag
*/}}
{{- define "luxe-jewelry-app.image" -}}
{{- $registry := .Values.global.imageRegistry -}}
{{- $repository := .repository -}}
{{- $tag := .tag | toString -}}
{{- printf "%s/%s:%s" $registry $repository $tag }}
{{- end }}

{{/*
Create environment variables for database connection
*/}}
{{- define "luxe-jewelry-app.databaseEnv" -}}
{{- if .Values.database.enabled }}
- name: DATABASE_HOST
  value: {{ .Values.database.host | quote }}
- name: DATABASE_PORT
  value: {{ .Values.database.port | quote }}
- name: DATABASE_NAME
  value: {{ .Values.database.name | quote }}
- name: DATABASE_USERNAME
  valueFrom:
    secretKeyRef:
      name: {{ include "luxe-jewelry-app.fullname" . }}-db-secret
      key: username
- name: DATABASE_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ include "luxe-jewelry-app.fullname" . }}-db-secret
      key: password
{{- end }}
{{- end }}

{{/*
Create environment variables for Redis connection
*/}}
{{- define "luxe-jewelry-app.redisEnv" -}}
{{- if .Values.redis.enabled }}
- name: REDIS_HOST
  value: {{ .Values.redis.host | quote }}
- name: REDIS_PORT
  value: {{ .Values.redis.port | quote }}
{{- if .Values.redis.password }}
- name: REDIS_PASSWORD
  valueFrom:
    secretKeyRef:
      name: {{ include "luxe-jewelry-app.fullname" . }}-redis-secret
      key: password
{{- end }}
{{- end }}
{{- end }}
