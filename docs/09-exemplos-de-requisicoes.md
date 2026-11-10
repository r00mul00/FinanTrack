# 09 — Exemplos de requisições

Fluxo completo contra a API local (`SPRING_PROFILES_ACTIVE=dev`), do cadastro ao logout. As respostas abaixo foram **capturadas de uma execução real**; só os identificadores mudam a cada execução. A lista completa de rotas está em [04 — Endpoints](04-api-endpoints.md), e cada operação também está descrita no Swagger UI (http://localhost:8080/swagger-ui.html, perfil `dev`).

Convenções usadas nos exemplos:

```bash
API=http://localhost:8080/api/v1
JAR=cookies.txt   # guarda o cookie do refresh token entre as chamadas
```

- Corpos em JSON UTF-8, com `Content-Type: application/json`.
- Dinheiro com duas casas decimais; datas em ISO 8601 (`2026-11-10`); meses como `2026-11`.
- Todo erro volta no formato Problem Details (RFC 9457), com `Content-Type: application/problem+json`.

> **Windows (Git Bash ou PowerShell):** argumentos com acento podem chegar ao `curl` em outra codificação, e a API responde 400 ("corpo ausente ou mal formatado"). Grave o corpo em um arquivo UTF-8 e envie com `--data-binary @corpo.json`.

## Sumário

1. [Cadastro e login](#1-cadastro-e-login)
2. [Categorias e conta](#2-categorias-e-conta)
3. [Transações](#3-transações)
4. [Erros de validação e de regra de negócio](#4-erros-de-validação-e-de-regra-de-negócio)
5. [Cartão de crédito e faturas](#5-cartão-de-crédito-e-faturas)
6. [Dashboard](#6-dashboard)
7. [Sessão: token ausente, refresh e logout](#7-sessão-token-ausente-refresh-e-logout)

## 1. Cadastro e login

### Cadastro

```bash
curl -i -X POST $API/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Ana Exemplo","email":"ana@exemplo.com","password":"Senha@Forte123"}'
```

```http
HTTP/1.1 202 Accepted
```

A resposta é sempre 202, exista ou não o e-mail, para não revelar quem tem conta. O link de verificação chega por e-mail (no `dev`, ao Mailpit em http://localhost:8025) e precisa ser aberto antes do primeiro login.

### Login

```bash
curl -i -c $JAR -X POST $API/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@exemplo.com","password":"Senha@Forte123"}'
```

```http
HTTP/1.1 200 OK
Set-Cookie: finantrack_refresh=...; Path=/api/v1/auth; Max-Age=604800; HttpOnly; SameSite=Strict
```

```json
{
  "accessToken": "<access token (JWT)>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

O access token vale 15 minutos e vai no cabeçalho `Authorization` das próximas chamadas. O refresh token fica no cookie HttpOnly, restrito a `/api/v1/auth`, e vale 7 dias.

```bash
TOKEN="<accessToken da resposta do login>"
AUTH="Authorization: Bearer $TOKEN"
```

Antes da verificação, o login responde 403 com `"code": "EMAIL_NOT_VERIFIED"`, para o cliente oferecer o reenvio do e-mail.

## 2. Categorias e conta

### Categorias de despesa

Todo usuário novo recebe categorias padrão de receita e de despesa.

```bash
curl -s "$API/categories?type=EXPENSE" -H "$AUTH"
```

```json
[
  {
    "id": "8c4c52b7-0fcc-41a6-8bcc-396733aafc0e",
    "name": "Alimentação",
    "type": "EXPENSE",
    "icon": "Restaurant",
    "color": "#FF8A65",
    "archived": false
  },
  {
    "id": "ec40b67a-165c-45a5-b129-a0e86751c779",
    "name": "Compras",
    "type": "EXPENSE",
    "icon": "ShoppingBag",
    "color": "#F06292",
    "archived": false
  }
]
```

A lista real tem nove categorias de despesa; o exemplo mostra só as duas primeiras.

### Nova conta

```bash
curl -s -X POST $API/accounts -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Conta corrente","type":"CHECKING","initialBalance":2500.00}'
```

`201 Created`

```json
{
  "id": "84b645ef-b1e8-43c9-b15e-dfd279131a11",
  "name": "Conta corrente",
  "type": "CHECKING",
  "initialBalance": 2500.0,
  "currentBalance": 2500.0,
  "archived": false
}
```

Repetir o nome de uma conta ativa responde 409.

## 3. Transações

### Receita efetivada

```bash
curl -s -X POST $API/transactions -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "description": "Salário de novembro",
  "amount": 6800.00,
  "type": "INCOME",
  "status": "COMPLETED",
  "categoryId": "1e3d4a3d-31d0-4f92-853c-321ed90c3655",
  "accountId": "84b645ef-b1e8-43c9-b15e-dfd279131a11",
  "transactionDate": "2026-11-05"
}'
```

`201 Created`

```json
{
  "id": "a3b22990-a41f-43e2-a495-6980de631ab0",
  "description": "Salário de novembro",
  "amount": 6800.0,
  "type": "INCOME",
  "kind": "STANDARD",
  "status": "COMPLETED",
  "categoryId": "1e3d4a3d-31d0-4f92-853c-321ed90c3655",
  "categoryName": "Salário",
  "categoryIcon": "Payments",
  "categoryColor": "#20C997",
  "accountId": "84b645ef-b1e8-43c9-b15e-dfd279131a11",
  "accountName": "Conta corrente",
  "transactionDate": "2026-11-05",
  "dueDate": null,
  "notes": null,
  "recurring": false,
  "version": 0
}
```

Por estar efetivada, a receita já soma ao saldo da conta. O campo `version` é usado no controle de concorrência otimista: a edição envia a versão lida, e uma versão desatualizada responde 409.

### Despesa pendente

```bash
curl -s -X POST $API/transactions -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "description": "Supermercado",
  "amount": 412.90,
  "type": "EXPENSE",
  "status": "PENDING",
  "categoryId": "8c4c52b7-0fcc-41a6-8bcc-396733aafc0e",
  "accountId": "84b645ef-b1e8-43c9-b15e-dfd279131a11",
  "transactionDate": "2026-11-12",
  "dueDate": "2026-11-12"
}'
```

`201 Created`, com `"status": "PENDING"`. A despesa pendente não altera o saldo nem o dashboard, mas entra no orçamento da categoria, que mede o comprometido ([docs/06](06-regras-financeiras.md)).

### Listagem com filtro, ordenação e paginação

```bash
curl -s "$API/transactions?type=EXPENSE&sort=transactionDate,desc&size=5" -H "$AUTH"
```

```json
{
  "content": [
    {
      "id": "9e8ec08b-e584-46ca-a112-59d88102f20e",
      "description": "Supermercado",
      "amount": 412.9,
      "type": "EXPENSE",
      "kind": "STANDARD",
      "status": "PENDING",
      "categoryName": "Alimentação",
      "accountName": "Conta corrente",
      "transactionDate": "2026-11-12",
      "dueDate": "2026-11-12",
      "version": 0
    }
  ],
  "page": 0,
  "size": 5,
  "totalElements": 1,
  "totalPages": 1
}
```

O item foi resumido; a listagem devolve os mesmos campos da criação.

## 4. Erros de validação e de regra de negócio

### 400: campos inválidos

```bash
curl -s -X POST $API/transactions -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "description": "",
  "amount": 0,
  "type": "EXPENSE",
  "status": "PENDING",
  "categoryId": "8c4c52b7-0fcc-41a6-8bcc-396733aafc0e",
  "accountId": "84b645ef-b1e8-43c9-b15e-dfd279131a11",
  "transactionDate": "2026-11-12"
}'
```

```json
{
  "detail": "Existem campos inválidos.",
  "instance": "/api/v1/transactions",
  "status": 400,
  "title": "Requisição inválida",
  "errors": {
    "amount": "O valor deve ser maior que zero.",
    "description": "Informe a descrição."
  }
}
```

O mapa `errors` traz uma mensagem por campo, pronta para exibir ao lado do campo no formulário.

### 422: regra de negócio

Uma despesa com categoria de receita passa na validação dos campos, mas fere uma regra:

```bash
curl -s -X POST $API/transactions -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "description": "Mercado",
  "amount": 10,
  "type": "EXPENSE",
  "status": "PENDING",
  "categoryId": "1e3d4a3d-31d0-4f92-853c-321ed90c3655",
  "accountId": "84b645ef-b1e8-43c9-b15e-dfd279131a11",
  "transactionDate": "2026-11-12"
}'
```

```json
{
  "detail": "A categoria não corresponde ao tipo da movimentação.",
  "instance": "/api/v1/transactions",
  "status": 422,
  "title": "Operação não permitida"
}
```

## 5. Cartão de crédito e faturas

### Novo cartão

```bash
curl -s -X POST $API/cards -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "nickname": "Cartão principal",
  "brand": "MASTERCARD",
  "lastFour": "4821",
  "creditLimit": 5000.00,
  "closingDay": 3,
  "dueDay": 10
}'
```

`201 Created`

```json
{
  "id": "e5dd06cf-2d7f-4c4f-b83e-3b1d1ecc8879",
  "nickname": "Cartão principal",
  "brand": "MASTERCARD",
  "lastFour": "4821",
  "creditLimit": 5000.0,
  "usedLimit": 0,
  "availableLimit": 5000.0,
  "closingDay": 3,
  "dueDay": 10,
  "archived": false
}
```

### Compra parcelada

```bash
CARD=e5dd06cf-2d7f-4c4f-b83e-3b1d1ecc8879
curl -s -X POST $API/cards/$CARD/purchases -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "description": "Notebook",
  "totalAmount": 4799.00,
  "installments": 3,
  "categoryId": "8c4c52b7-0fcc-41a6-8bcc-396733aafc0e",
  "purchaseDate": "2026-11-08"
}'
```

`201 Created`

```json
{
  "id": "9ce67402-9942-431c-841d-335e0eafd36c",
  "description": "Notebook",
  "totalAmount": 4799.0,
  "installments": 3,
  "categoryId": "8c4c52b7-0fcc-41a6-8bcc-396733aafc0e",
  "purchaseDate": "2026-11-08",
  "canceled": false
}
```

### Faturas geradas

```bash
curl -s $API/cards/$CARD/invoices -H "$AUTH"
```

```json
[
  {
    "id": "c484f466-7836-4965-8095-fa574bf2ba70",
    "referenceMonth": "2027-02",
    "closingDate": "2027-02-03",
    "dueDate": "2027-02-10",
    "totalAmount": 1599.66,
    "paidAmount": 0.0,
    "remainingAmount": 1599.66,
    "status": "OPEN"
  },
  {
    "id": "7ad441f4-9176-4f47-a071-7d5a11fa1908",
    "referenceMonth": "2027-01",
    "closingDate": "2027-01-03",
    "dueDate": "2027-01-10",
    "totalAmount": 1599.66,
    "paidAmount": 0.0,
    "remainingAmount": 1599.66,
    "status": "OPEN"
  },
  {
    "id": "ea8b7933-486a-4399-8435-ac4020dfb359",
    "referenceMonth": "2026-12",
    "closingDate": "2026-12-03",
    "dueDate": "2026-12-10",
    "totalAmount": 1599.68,
    "paidAmount": 0.0,
    "remainingAmount": 1599.68,
    "status": "OPEN"
  }
]
```

As regras de [docs/06](06-regras-financeiras.md#parcelamento) aparecem no resultado:

- A compra foi no dia 8, depois do fechamento (dia 3), e por isso a primeira parcela cai na fatura de dezembro.
- 4.799,00 ÷ 3 = 1.599,666…; cada parcela fica com 1.599,66, e os 2 centavos de diferença vão para a primeira (1.599,68).

### 422: compra acima do limite

Todas as parcelas futuras ocupam o limite. Com 4.799,00 em uso, restam 201,00:

```bash
curl -s -X POST $API/cards/$CARD/purchases -H "$AUTH" -H 'Content-Type: application/json' -d '{
  "description": "Televisão",
  "totalAmount": 900.00,
  "installments": 1,
  "categoryId": "8c4c52b7-0fcc-41a6-8bcc-396733aafc0e",
  "purchaseDate": "2026-11-09"
}'
```

```json
{
  "detail": "A compra ultrapassa o limite disponível do cartão.",
  "instance": "/api/v1/cards/e5dd06cf-2d7f-4c4f-b83e-3b1d1ecc8879/purchases",
  "status": 422,
  "title": "Operação não permitida"
}
```

## 6. Dashboard

```bash
curl -s "$API/dashboard/summary?from=2026-11-01&to=2026-11-30" -H "$AUTH"
```

```json
{
  "from": "2026-11-01",
  "to": "2026-11-30",
  "previousFrom": "2026-10-01",
  "previousTo": "2026-10-31",
  "totalBalance": 9300.0,
  "income": 6800.0,
  "expenses": 1599.68,
  "result": 5200.32,
  "previousIncome": 0,
  "previousExpenses": 0,
  "previousResult": 0,
  "incomeChange": null,
  "expensesChange": null,
  "resultChange": null
}
```

Os números seguem as regras do dashboard:

- **Saldo** de 9.300,00: saldo inicial de 2.500,00 mais o salário efetivado de 6.800,00. A despesa pendente e as compras no cartão não movimentam a conta.
- **Despesas** de 1.599,68: só a primeira parcela do notebook, datada de 8 de novembro. Cada parcela conta no mês da sua data, e o supermercado ainda está pendente.
- **Variações** nulas: o período anterior (outubro, calculado sozinho) não tem movimento, e não há base para a porcentagem.

## 7. Sessão: token ausente, refresh e logout

### 401: sem token

```bash
curl -s $API/accounts
```

```json
{
  "detail": "Sessão inválida ou expirada. Entre novamente.",
  "instance": "/api/v1/accounts",
  "status": 401,
  "title": "Não autenticado"
}
```

### Refresh

Envia só o cookie; o corpo é vazio.

```bash
curl -s -b $JAR -c $JAR -X POST $API/auth/refresh
```

```json
{ "accessToken": "<novo access token>", "tokenType": "Bearer", "expiresIn": 900 }
```

O refresh token é rotativo. Cada uso grava um cookie novo e invalida o anterior, e reapresentar um refresh token já usado revoga toda a família de tokens daquela sessão ([docs/05](05-autenticacao.md)).

### Logout

```bash
curl -i -b $JAR -X POST $API/auth/logout
```

```http
HTTP/1.1 204 No Content
Set-Cookie: finantrack_refresh=; Path=/api/v1/auth; Max-Age=0; HttpOnly; SameSite=Strict
```

O refresh token é revogado no servidor, e o cookie é apagado no navegador.
