#!/bin/sh
# Teste de fumaça das imagens de produção, pela porta do Nginx.
# Uso: sh infra/smoke-test.sh [http://localhost:8080]
set -eu

base=${1:-http://localhost:8080}
here=$(dirname "$0")
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
failures=0

ok()   { echo "  ok    $1"; }
fail() { echo "  FALHA $1"; failures=$((failures + 1)); }
header() { grep -i "^$1:" "$2" | head -1 | cut -d' ' -f2- | tr -d '\r'; }

# Espera um código HTTP; a API demora mais que o Nginx para subir (Flyway, JVM).
wait_for() {
    url=$1; expected=$2; shift 2
    for _ in $(seq 1 60); do
        code=$(curl -s -o /dev/null -w '%{http_code}' "$@" "$url" || true)
        [ "$code" = "$expected" ] && return 0
        sleep 3
    done
    echo "  último código de $url: $code (esperado $expected)"
    return 1
}

echo "Frontend ($base)"
wait_for "$base/" 200 || { echo "  o Nginx não respondeu"; exit 1; }
curl -s -D "$tmp/index.h" -o "$tmp/index.html" "$base/"

csp=$(header Content-Security-Policy "$tmp/index.h")
hash=$(sh "$here/nginx/importmap-hash.sh" "$tmp/index.html")
case "$csp" in *"'sha256-$hash'"*) ok "CSP autoriza o importmap servido" ;; *) fail "hash do importmap ausente da CSP" ;; esac
case "$csp" in *"frame-ancestors 'none'"*) ok "CSP impede o app em iframe" ;; *) fail "CSP sem frame-ancestors" ;; esac
[ -n "$(header Strict-Transport-Security "$tmp/index.h")" ] && ok "HSTS presente" || fail "HSTS ausente"
[ "$(header X-Content-Type-Options "$tmp/index.h")" = "nosniff" ] && ok "nosniff presente" || fail "nosniff ausente"
case "$(header Server "$tmp/index.h")" in *[0-9]*) fail "Server expõe a versão do Nginx" ;; *) ok "Server sem versão" ;; esac

code=$(curl -s -o "$tmp/route.html" -w '%{http_code}' "$base/transacoes")
[ "$code" = 200 ] && grep -q '<div id="app">' "$tmp/route.html" && ok "rota do SPA cai no index.html" || fail "rota do SPA: $code"

asset=$(grep -o '_framework/blazor\.webassembly\.[a-z0-9]*\.js' "$tmp/index.html" | head -1)
curl -s -D "$tmp/asset.h" -o /dev/null -H 'Accept-Encoding: gzip' "$base/$asset"
case "$(header Cache-Control "$tmp/asset.h")" in *immutable*) ok "arquivo com hash em cache longo ($asset)" ;; *) fail "cache do $asset" ;; esac
[ "$(header Content-Encoding "$tmp/asset.h")" = "gzip" ] && ok "gzip pré-comprimido servido" || fail "gzip ausente"

echo "API pelo proxy"
login() { curl -s -D "$tmp/login.h" -o "$tmp/login.json" -w '%{http_code}' -X POST "$base/api/v1/auth/login" \
    -H 'Content-Type: application/json' -d '{"email":"smoke@finantrack.local","password":"Senha-Errada-123"}'; }
wait_for "$base/api/v1/auth/login" 401 -X POST -H 'Content-Type: application/json' \
    -d '{"email":"smoke@finantrack.local","password":"Senha-Errada-123"}' \
    && ok "login inválido devolve 401 (Nginx → API → banco)" || fail "API não respondeu pelo proxy"
login > /dev/null
case "$(header Content-Type "$tmp/login.h")" in application/problem+json*) ok "erro no formato problem+json" ;; *) fail "Content-Type do erro" ;; esac
grep -qi 'exception\|stacktrace\|org\.springframework' "$tmp/login.json" && fail "erro expõe detalhes internos" || ok "erro sem detalhes internos"

curl -s -o "$tmp/actuator" "$base/actuator/health"
grep -q '"status"' "$tmp/actuator" && fail "actuator exposto pelo Nginx" || ok "actuator não exposto"

echo
if [ "$failures" -gt 0 ]; then
    echo "$failures verificação(ões) falharam."
    exit 1
fi
echo "Todas as verificações passaram."
