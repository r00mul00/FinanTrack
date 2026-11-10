#!/bin/sh
# Cria o .env.prod a partir do .env.prod.example, com DB_PASSWORD e JWT_SECRET aleatórios.
# Uso, a partir da raiz do repositório: sh infra/generate-secrets.sh [arquivo]
#
# Nunca sobrescreve um arquivo existente: trocar o JWT_SECRET derruba as sessões,
# e trocar a senha do banco exige alterá-la também no Postgres.
set -eu

target=${1:-.env.prod}
example=.env.prod.example

[ -f "$example" ] || { echo "Rode a partir da raiz do repositório ($example não encontrado)." >&2; exit 1; }
[ -e "$target" ] && { echo "$target já existe; apague-o antes se quiser gerar novos segredos." >&2; exit 1; }

# base64url: sem "$", "/" ou "+", que atrapalham o Compose, URLs e shells.
random() { openssl rand -base64 "$1" | tr '+/' '-_' | tr -d '=\n'; }

umask 077
db_password=$(random 24)
jwt_secret=$(random 48)
sed -e "s|^DB_PASSWORD=.*|DB_PASSWORD=$db_password|" \
    -e "s|^JWT_SECRET=.*|JWT_SECRET=$jwt_secret|" \
    "$example" > "$target"

echo "$target criado com DB_PASSWORD e JWT_SECRET aleatórios."
echo "Preencha FRONTEND_URL e as variáveis de e-mail antes de subir."
