# Frontend FinanTrack: Blazor WebAssembly servido pelo Nginx, que também encaminha /api.
# Contexto de build: a raiz do repositório (docker build -f frontend/Dockerfile .),
# porque a configuração do Nginx fica em infra/nginx.

# --- Publish do Blazor. ---
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Restore primeiro: os pacotes ficam em cache enquanto o .csproj não muda.
COPY frontend/src/FinanTrack.Web/FinanTrack.Web.csproj frontend/src/FinanTrack.Web/
RUN dotnet restore frontend/src/FinanTrack.Web/FinanTrack.Web.csproj

COPY frontend/ frontend/
RUN dotnet publish frontend/src/FinanTrack.Web/FinanTrack.Web.csproj -c Release -o /out --no-restore

# --- CSP: o hash do importmap muda a cada publish, então é calculado aqui. ---
FROM alpine:3.22 AS csp
RUN apk add --no-cache openssl
COPY infra/nginx/importmap-hash.sh infra/nginx/security-headers.conf.in /tmp/
COPY --from=build /out/wwwroot/index.html /tmp/index.html
RUN hash=$(sh /tmp/importmap-hash.sh /tmp/index.html) \
    && sed "s|__IMPORTMAP_HASH__|$hash|" /tmp/security-headers.conf.in > /tmp/security-headers.conf \
    && grep -q "sha256-$hash" /tmp/security-headers.conf

# --- Execução: Nginx sem root, escutando na porta 8080. ---
FROM nginxinc/nginx-unprivileged:1.28-alpine

COPY --from=build /out/wwwroot/ /usr/share/nginx/html/
COPY --from=csp /tmp/security-headers.conf /etc/nginx/snippets/security-headers.conf
COPY infra/nginx/default.conf.template /etc/nginx/templates/default.conf.template

# Endereço interno da API; o provedor pode sobrescrever.
ENV API_UPSTREAM=http://api:8080

EXPOSE 8080
