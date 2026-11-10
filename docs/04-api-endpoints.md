# 04 — Endpoints REST (lista inicial)

Base: `/api/v1`. Exceto em **Autenticação**, todas as rotas exigem `Authorization: Bearer <token>` e atuam apenas sobre os dados do usuário do token.

Cada operação está descrita no Swagger UI (`/swagger-ui.html`, perfil `dev`), com os erros possíveis e exemplos. Um fluxo completo com `curl` e respostas reais está em [09 — Exemplos de requisições](09-exemplos-de-requisicoes.md).

**Listagens:** `?page=0&size=20&sort=campo,direcao`, com resposta `{ content, page, size, totalElements, totalPages }`.

| Status | Uso |
|---|---|
| 200 / 201 / 204 | Sucesso |
| 400 | Corpo ou parâmetro inválido |
| 401 | Não autenticado |
| 404 | Inexistente ou pertencente a outro usuário |
| 409 | Conflito: duplicidade, versão desatualizada, recurso em uso |
| 422 | Regra de negócio violada, por exemplo limite ou saldo insuficiente |
| 403 | E-mail não validado no login (`code: EMAIL_NOT_VERIFIED`) |
| 405 / 415 | Método HTTP ou formato de conteúdo não suportado |
| 429 | Excesso de tentativas |
| 500 | Erro inesperado, registrado no log do servidor |

**Erros:** todas as respostas de erro, inclusive a 401, seguem a [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) (`application/problem+json`), com título e mensagem em português e sem nomes internos de classes ou detalhes de implementação.

```json
{
  "title": "Requisição inválida",
  "status": 400,
  "detail": "Existem campos inválidos.",
  "instance": "/api/v1/transactions",
  "errors": { "from": "Valor inválido." }
}
```

`errors` só aparece em falhas de validação, com uma mensagem por campo. O frontend exibe a primeira delas ou, na falta, o `detail`.

## Autenticação

| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/register` | Cadastro; envia e-mail de validação (202) |
| POST | `/auth/verify-email` | Confirma o e-mail |
| POST | `/auth/resend-verification` | Reenvia a validação (202) |
| POST | `/auth/login` | Retorna access token e define o cookie |
| POST | `/auth/refresh` | Rotaciona o refresh token |
| POST | `/auth/logout` | Revoga o token e limpa o cookie |
| POST | `/auth/forgot-password` | Sempre 202 |
| POST | `/auth/reset-password` | Define nova senha e revoga as sessões |

## Usuário e configurações

| Método | Rota | Descrição |
|---|---|---|
| GET / PATCH | `/users/me` | Consultar e alterar nome |
| PUT | `/users/me/password` | Alterar senha |
| POST | `/users/me/email-change` | Solicitar troca de e-mail |
| POST | `/users/me/email-change/confirm` | Confirmar troca |
| GET / PATCH | `/users/me/preferences` | Moeda, tema, notificações |
| GET | `/users/me/sessions` | Listar sessões ativas |
| DELETE | `/users/me/sessions/{id}` | Encerrar uma sessão |
| GET | `/users/me/export` | Exportar dados pessoais |
| POST | `/users/me/deletion-request` | Solicitar exclusão |

## Contas

| Método | Rota | Descrição |
|---|---|---|
| GET / POST | `/accounts` | Listar com saldo; criar |
| GET / PUT / DELETE | `/accounts/{id}` | Detalhar, editar, excluir ou arquivar |

## Categorias

| Método | Rota | Descrição |
|---|---|---|
| GET / POST | `/categories` | Listar (`?type=`); criar |
| GET / PUT | `/categories/{id}` | Detalhar; editar e arquivar (campo `archived`). O tipo não pode mudar (422) |
| GET | `/categories/{id}/usage` | Quantidade de registros vinculados |
| DELETE | `/categories/{id}?reassignTo={id}` | Excluir; 409 se em uso sem `reassignTo` |

## Transações

| Método | Rota | Descrição |
|---|---|---|
| GET | `/transactions` | Filtros: `search`, `from`, `to`, `categoryId`, `type`, `status`, `accountId` (`cardId` na Fase 6). Ordenação por `transactionDate`, `amount`, `description`, `status` ou `dueDate`; `size` até 100 |
| POST | `/transactions` | Criar; com `cardId`, gera compra e parcelas |
| GET / PUT / DELETE | `/transactions/{id}` | Detalhar, editar, excluir |
| POST | `/transactions/{id}/complete` | Marcar como efetivada |

Lançamentos com `kind` diferente de `STANDARD` são alterados apenas pelos endpoints de cartão (422 nas demais rotas).

## Dashboard

| Método | Rota | Descrição |
|---|---|---|
| GET | `/dashboard/summary?from&to` | Saldo, receitas, despesas, resultado, comparação |
| GET | `/dashboard/income-vs-expense?from&to` | Série do gráfico |
| GET | `/dashboard/expenses-by-category?from&to` | Distribuição |
| GET | `/dashboard/upcoming` | Próximos vencimentos |

## Orçamentos

| Método | Rota | Descrição |
|---|---|---|
| GET | `/budgets?month=YYYY-MM` | Lista com utilizado, percentual e estado |
| POST | `/budgets` | Criar |
| PUT / DELETE | `/budgets/{id}` | Editar limite; excluir |

## Metas

| Método | Rota | Descrição |
|---|---|---|
| GET / POST | `/goals` | Listar; criar |
| GET / PUT / DELETE | `/goals/{id}` | Detalhar, editar, excluir |
| POST | `/goals/{id}/deposits` | Adicionar valor |
| POST | `/goals/{id}/withdrawals` | Retirar valor |
| GET | `/goals/{id}/movements` | Histórico |
| POST | `/goals/{id}/complete` | Concluir |

## Cartões

| Método | Rota | Descrição |
|---|---|---|
| GET / POST | `/cards` | Listar com limite disponível; criar |
| GET / PUT / DELETE | `/cards/{id}` | Detalhar, editar, excluir ou arquivar |
| GET / POST | `/cards/{id}/purchases` | Listar; registrar compra |
| DELETE | `/cards/{id}/purchases/{purchaseId}` | Cancelar compra |
| GET | `/cards/{id}/invoices` | Listar faturas |
| GET | `/cards/{id}/invoices/{invoiceId}` | Detalhar com parcelas |
| POST | `/cards/{id}/invoices/{invoiceId}/payments` | Registrar pagamento |

## Recorrências

| Método | Rota | Descrição |
|---|---|---|
| GET / POST | `/recurring` | Listar; criar |
| GET / PUT / DELETE | `/recurring/{id}` | Detalhar, editar, excluir |
| POST | `/recurring/{id}/pause` | Pausar |
| POST | `/recurring/{id}/resume` | Reativar |
| GET | `/recurring/{id}/upcoming?count=` | Próximas ocorrências |

## Notificações e pesquisa

| Método | Rota | Descrição |
|---|---|---|
| GET | `/notifications` | Listar (`?unread=&page=&size=`), com o total de não lidas |
| POST | `/notifications/{id}/read` | Marcar como lida |
| POST | `/notifications/read-all` | Marcar todas |
| GET | `/search?q=` | Pesquisa global em transações, categorias, metas, cartões e recorrências: até 5 itens por tipo, a partir de 2 caracteres |

## Relatórios

O mensal usa `month`, o anual usa `year` e os demais usam `from` e `to`; sem filtro, valem o mês ou o ano correntes. Exportação com `format=csv|pdf`, respeitando os mesmos filtros.

| Método | Rota | Descrição |
|---|---|---|
| GET | `/reports/monthly?month=` | Mensal |
| GET | `/reports/annual?year=` | Anual |
| GET | `/reports/by-category` | Por categoria |
| GET | `/reports/by-period` | Por período |
| GET | `/reports/income-vs-expense` | Receitas versus despesas |
| GET | `/reports/evolution` | Evolução financeira |
| GET | `/reports/by-card` | Despesas por cartão |
| GET | `/reports/{tipo}/export?format=` | Download do arquivo |
