#!/bin/sh
# Imprime o SHA-256 (base64) do conteúdo do <script type="importmap"> de um index.html,
# exatamente como o navegador o calcula para a CSP. Uso: importmap-hash.sh index.html
#
# O parser HTML troca CRLF por LF antes de calcular o hash; aqui também.
set -eu

tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

awk -v open='<script type="importmap">' '
    { sub(/\r$/, ""); buf = buf $0 "\n" }
    END {
        start = index(buf, open)
        if (start == 0) exit 1
        rest = substr(buf, start + length(open))
        stop = index(rest, "</script>")
        if (stop == 0) exit 1
        printf "%s", substr(rest, 1, stop - 1)
    }' "$1" > "$tmp" || { echo "importmap não encontrado em $1" >&2; exit 1; }

openssl dgst -sha256 -binary "$tmp" | openssl base64 -A
