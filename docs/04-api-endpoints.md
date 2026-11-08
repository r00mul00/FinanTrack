# 04 — Endpoints REST (lista inicial)

Base: `/api/v1`. Exceto em **Autenticação**, todas as rotas exigem `Authorization: Bearer <token>` e atuam apenas sobre os dados do usuário do token.

**Listagens:** `?page=0&size=20&sort=campo,direcao`, com resposta `{ content, page, size, totalElements, totalPages }`.

| Status | Uso |
|---|---|
| 200 / 201 / 204 | Sucesso |
| 400 | Corpo ou parâmetro inválido |
| 401 | Não autenticado |
| 404 | Inexistente ou pertencente a outro usuário |
| 409 | Conflito: duplicidade, versão desatualizada, recurso em uso |
| 422 | Regra de negócio violada, por exemplo limite ou saldo insuficiente |
| 429 | Excesso de tentativas |

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
| GET / PUT | `/categories/{id}` | Detalhar; editar |
| GET | `/categories/{id}/usage` | Quantidade de registros vinculados |
| DELETE | `/categories/{id}?reassignTo={id}` | Excluir; 409 se em uso sem `reassignTo` |
| POST | `/categories/{id}/archive` | Arquivar |

## Transações

| Método | Rota | Descrição |
|---|---|---|
| GET | `/transactions` | Filtros: `search`, `from`, `to`, `categoryId`, `type`, `status`, `accountId`, `cardId` |
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
| GET | `/notifications` | Listar (`?unread=`) |
| POST | `/notifications/{id}/read` | Marcar como lida |
| POST | `/notifications/read-all` | Marcar todas |
| GET | `/search?q=` | Pesquisa global |

## Relatórios

Todos aceitam `from` e `to`; exportação com `format=csv|pdf`, respeitando os mesmos filtros.

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
