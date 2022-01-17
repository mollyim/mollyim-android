{{ range .Versions -}}
**What's Changed**

{{ range .Commits -}}
{{ if hasSuffix .Committer.Email "@molly.im" -}}
* {{ .Subject -}}
{{ if .Refs }}{{ range .Refs -}}
{{ if and (ne .Action "") (eq .Source "") }} ({{ .Action }} [#{{ .Ref }}]({{ $.Info.RepositoryURL -}}/issues/{{ .Ref -}})){{ end -}}
{{ end }}{{ end }}
{{ end -}}
{{ end }}
**Full Changelog**: {{ if .Tag.Previous }}{{ $.Info.RepositoryURL }}/compare/{{ .Tag.Previous.Name }}...{{ .Tag.Name }}{{ else }}{{ .Tag.Name }}{{ end }}

{{ end -}}
