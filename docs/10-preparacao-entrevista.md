# 10 — Preparação para entrevista técnica

Roteiro para apresentar o FinanTrack: o que contar, quais decisões defender e onde está a prova no código. Cada afirmação aponta para um documento ou arquivo do repositório, para você poder abrir na hora se pedirem.

## Sumário

1. [Apresentação](#1-apresentação)
2. [Números do projeto](#2-números-do-projeto)
3. [Decisões e trade-offs](#3-decisões-e-trade-offs)
4. [Desafios técnicos](#4-desafios-técnicos)
5. [Perguntas prováveis](#5-perguntas-prováveis)
6. [Limitações e próximos passos](#6-limitações-e-próximos-passos)
7. [Roteiro de demonstração](#7-roteiro-de-demonstração)

## 1. Apresentação

**Em 30 segundos**

> O FinanTrack é um sistema de finanças pessoais completo: contas, transações, orçamentos, metas, cartões de crédito com faturas parceladas, contas recorrentes, dashboard e relatórios. O backend é uma API REST em Java 21 com Spring Boot, PostgreSQL e Flyway; o frontend é uma SPA em Blazor WebAssembly. Tem 371 testes de integração e unitários rodando contra PostgreSQL real no CI, autenticação com JWT e refresh token rotativo, e uma demonstração pública no GitHub Pages.

**Em 2 minutos**, acrescente três pontos, nesta ordem:

1. **Regras financeiras escritas antes do código** ([docs/06](06-regras-financeiras.md)): saldo calculado e nunca armazenado, compra no cartão contando como despesa na data de cada parcela, orçamento medindo o comprometido. Os testes verificam essas regras.
2. **Segurança tratada como requisito**: isolamento por usuário em toda consulta, 404 em vez de 403 para recurso alheio, refresh token com detecção de reuso, limite de tentativas e varredura de segredos no CI.
3. **Entrega de ponta a ponta**: CI com quatro jobs, imagens Docker de produção com teste de fumaça, documentação Swagger completa e uma demonstração que roda sem servidor.

## 2. Números do projeto

| Item | Valor |
|---|---|
| Módulos de negócio no backend | 14 (`account`, `auth`, `transaction`, `card`, `budget`, `goal`, `recurring`...) |
| Endpoints REST | 76, todos documentados no Swagger |
| Migrations Flyway | 13 |
| Testes do backend | 371, em 32 classes, contra PostgreSQL 17 |
| Páginas e componentes Blazor | 57 arquivos `.razor` |
| Jobs do CI | 4: backend, frontend, imagens de produção, varredura de segredos |
| Decisões técnicas registradas | D1 a D12, no [plano](../PLANO_DESENVOLVIMENTO.md) |

## 3. Decisões e trade-offs

Para cada decisão, saiba dizer **a alternativa** e **o custo** da escolha. Ser honesto sobre o custo vale mais que defender a escolha como perfeita.

| Decisão | Alternativa descartada | Por quê | Custo aceito |
|---|---|---|---|
| **Monólito modular**, pacotes por domínio ([docs/01](01-arquitetura.md#estilo-arquitetural)) | Microsserviços; camadas globais (`controllers/`, `services/`) | Transações de banco simples entre módulos e deploy único; cada funcionalidade fica num lugar só | Escala só verticalmente; a fronteira entre módulos depende de disciplina, não do compilador |
| **Access token em memória e refresh token em cookie HttpOnly** (D4) | Tudo no `localStorage` | Um script injetado (XSS) não consegue ler o cookie | Exige mesma origem (D5) e proteção contra CSRF (`SameSite=Strict` e cookie restrito a `/api/v1/auth`) |
| **Frontend e API na mesma origem**, com Nginx na frente (D5) | Domínios separados com CORS | Navegadores bloqueiam cada vez mais cookies entre sites, o que quebraria D4 | Um proxy a mais na infraestrutura |
| **Saldo calculado em consulta, nunca armazenado** ([docs/06](06-regras-financeiras.md#saldo)) | Coluna de saldo atualizada a cada transação | Não existe saldo para ficar dessincronizado | Uma agregação por consulta, coberta por índice |
| **UUID** nas entidades expostas (D3) | `BIGSERIAL` | Não revela volume de dados nem facilita enumeração | Índices maiores; a autorização continua obrigatória |
| **404 para recurso de outro usuário** | 403 | 403 confirmaria que o ID existe | O cliente não distingue "não existe" de "não é seu", e nem deve |
| **Lock pessimista** em cartão e meta; **`@Version`** nas edições (D8) | Só lock otimista | Limite do cartão e saldo da meta não podem ser lidos desatualizados durante uma compra ou retirada | Serializa operações no mesmo cartão, o que é aceitável para um usuário |
| **Recorrências por job com índice único** (D7) | Gerar na leitura | O banco garante que o mesmo ciclo nunca é gerado duas vezes, mesmo com o job repetido | Lançamento só aparece quando o job roda; mitigado rodando também ao iniciar e ao criar |
| **Exclusão de categoria em uso exige reatribuição** (D6) | Apagar em cascata ou deixar sem categoria | Preserva o histórico financeiro e os relatórios | Um passo a mais para o usuário |
| **Demonstração no GitHub Pages com API simulada no navegador** (D12) | Hospedar a API num plano gratuito | Custo zero, sem hibernação, sem dados reais de ninguém num servidor | As regras de negócio existem duas vezes (Java e C#); a demo não envia e-mails nem gera PDF |
| **OpenPDF** (D11) | iText | Licença LGPL/MPL; o iText exigiria AGPL ou licença paga | API menos moderna |

## 4. Desafios técnicos

Formato sugerido para contar: **situação → problema → solução → como verifiquei**.

### Refresh token rotativo sem derrubar quem usa várias abas

- **Problema:** com rotação, um refresh token usado duas vezes indica roubo, e a reação correta é revogar a sessão. Mas duas abas renovando ao mesmo tempo também usam o mesmo token duas vezes, legitimamente.
- **Solução:** o token guarda `rotated_at`. Reuso em menos de 10 segundos responde 409, e o cliente tenta de novo; reuso depois disso revoga toda a família e é auditado. No frontend, as requisições simultâneas de uma aba compartilham um único pedido de renovação.
- **Prova:** [docs/05](05-autenticacao.md#renovação-com-rotação), `RefreshTokenIntegrationTest`, `frontend/.../Authentication/AuthSession.cs`. Só o hash do token fica no banco.

### Parcelamento e faturas

- **Problema:** dividir R$ 4.799,00 em 3 vezes não dá um valor exato, a fatura depende do dia de fechamento e há meses sem o dia 31.
- **Solução:** cada parcela é o total dividido e truncado em centavos, e a diferença vai para a primeira (1.599,68 + 1.599,66 + 1.599,66). Compra depois do fechamento cai na fatura seguinte. Dia inexistente usa o último dia do mês, sem perder o dia original nos meses seguintes.
- **Prova:** [docs/06](06-regras-financeiras.md#cartões), `card/service` nos testes unitários, e o exemplo real em [docs/09](09-exemplos-de-requisicoes.md#5-cartão-de-crédito-e-faturas).

### Concorrência em dinheiro

- **Problema:** duas compras simultâneas no mesmo cartão podem, cada uma, ler o limite disponível antes da outra gravar, e as duas passam.
- **Solução:** a compra, o cancelamento e o pagamento de fatura carregam o cartão com `PESSIMISTIC_WRITE` (`SELECT ... FOR UPDATE`). O mesmo vale para aportes e retiradas em metas. Edições comuns usam `@Version` e respondem 409 quando a versão está desatualizada.
- **Prova:** `CreditCardRepository.findForUpdate`, `FinancialGoalRepository.findForUpdate`, [docs/06](06-regras-financeiras.md#concorrência).

### Consultas N+1

- **Problema:** listagens que carregam categoria e conta de cada transação podem disparar uma consulta por linha.
- **Solução:** as entidades guardam só os IDs de categoria e conta. A listagem busca a página de transações e depois carrega todas as categorias e contas da página em uma consulta cada (`findAllById`), montando a resposta em memória. As parcelas de uma compra são gravadas em lote, e há índice por categoria.
- **Prova:** `QueryCountIntegrationTest` liga as estatísticas do Hibernate e falha se o número de consultas por requisição crescer com a quantidade de registros. É um teste de regressão: a proteção continua valendo depois de qualquer refatoração.

### Recorrências idempotentes

- **Problema:** o job pode rodar duas vezes (reinício, duas instâncias) ou ficar dias sem rodar.
- **Solução:** índice único `(recurring_id, occurrence_date)`, uma transação por recorrência e o cursor `next_occurrence_date`, que avança até alcançar o presente. O job também roda ao iniciar a aplicação.
- **Prova:** [docs/06](06-regras-financeiras.md#recorrências), `RecurringIntegrationTest`. O "hoje" vem de um `Clock` injetável, e os testes controlam a data.

### Não vazar informação em detalhes pequenos

- **Cadastro, recuperação de senha e reenvio** respondem 202 de forma idêntica, exista o e-mail ou não.
- **Login recusado não é auditado:** a escrita extra só para e-mails existentes mudaria o tempo de resposta e revelaria quem tem conta.
- **Erros** seguem a RFC 9457, sem stack trace nem mensagem interna (`ApiErrorResponsesIntegrationTest`).

### Demonstração sem servidor

- **Problema:** portfólio a custo zero, mas o GitHub Pages só serve arquivos estáticos.
- **Solução:** na branch `demo/github-pages`, um `HttpMessageHandler` substitui a rede no Blazor e responde às mesmas rotas, com as mesmas regras, guardando dados fictícios no `localStorage`. As páginas não sabem que estão em modo demonstração.
- **Por que é interessante:** mostra que o frontend depende só do contrato HTTP. O ponto fraco é a duplicação das regras, por isso a demo cobre o fluxo principal e deixa e-mails e PDF de fora.

## 5. Perguntas prováveis

**Por que JWT com refresh token, e não sessão no servidor?**
A API fica sem estado para validar requisições; só a renovação consulta o banco. O access token dura 15 minutos, e o refresh token pode ser revogado. Uma sessão clássica com cookie também atenderia; o JWT foi escolhido para separar bem SPA e API e para praticar rotação e revogação.

**Como você garante que um usuário não acessa dados de outro?**
O `userId` vem sempre do JWT, nunca do corpo ou da URL, e toda busca filtra pelo usuário (`findByIdAndUserId`). Cada módulo tem testes de acesso cruzado, que esperam 404.

**Por que `BigDecimal` e `NUMERIC(14,2)`?**
`double` não representa 0,10 exatamente, e o erro se acumula em somas. O arredondamento é `HALF_UP`, com escala fixa de duas casas.

**O que acontece se dois usuários editarem a mesma transação?**
Não acontece entre usuários, por causa do isolamento. Entre duas abas do mesmo usuário, o `@Version` faz a segunda gravação responder 409, com a mensagem "O registro foi alterado por outra operação. Recarregue e tente novamente."

**Como os testes rodam contra o banco?**
Contra um PostgreSQL 17 real: no CI é um serviço do GitHub Actions, e localmente é o banco `finantrack_test`. O H2 foi evitado porque as regras usam recursos do PostgreSQL, como restrições `CHECK` e índices parciais. O próximo passo seria Testcontainers (item 8.2), que depende do Docker.

**Como você evita subir segredos para o repositório?**
Segredos só em variáveis de ambiente, `.env` no `.gitignore` e gitleaks varrendo todo o histórico em cada PR. No perfil `prod`, a API se recusa a iniciar com segredo fraco, valor de exemplo ou URL sem HTTPS.

**Esse limite de tentativas funciona com várias instâncias?**
Não. Ele usa contadores em memória, suficientes para uma instância, e o próprio código registra isso. Com várias instâncias, os contadores iriam para um armazenamento comum, como o Redis, ou para o proxy.

**Por que Blazor e não React ou Angular?**
A stack do projeto exigia Blazor e C# no frontend. Isso dá tipagem forte dos dois lados e um único ecossistema de ferramentas no cliente. O custo é o primeiro carregamento maior, porque o runtime .NET é baixado.

**O que você faria diferente?**
Escolha um ponto e fale com segurança, por exemplo: começar com Testcontainers desde a Fase 2, ou adicionar testes de componentes no frontend (hoje, a cobertura automatizada está toda no backend).

## 6. Limitações e próximos passos

Assumir as limitações antes que perguntem mostra que você conhece o próprio sistema.

| Limitação | Próximo passo |
|---|---|
| Sem deploy público do backend; a demo usa uma API simulada | Hospedar API e banco quando houver orçamento; as imagens de produção já existem e são testadas no CI |
| Sistema completo em containers ainda não validado localmente (9.7) | Depende de Docker na máquina de desenvolvimento |
| Testes de integração no PostgreSQL local, não em Testcontainers (8.2) | Migrar quando o Docker estiver disponível |
| Sem testes automatizados no frontend | bUnit para os componentes com regra, como formulários e o fluxo de renovação |
| Limite de tentativas em memória | Redis ou limite no proxy, se houver mais de uma instância |
| Fora do escopo: transferência entre contas, várias moedas, juros de fatura | Documentado em [docs/06](06-regras-financeiras.md#fora-do-escopo-inicial) |

## 7. Roteiro de demonstração

Cerca de 5 minutos, na [demonstração pública](https://r00mul00.github.io/FinanTrack/). Se houver tempo e ambiente, mostre também o Swagger local.

1. **Login** com qualquer e-mail. Explique em uma frase que esta versão roda no navegador e que a `main` tem a API real.
2. **Dashboard:** saldo, comparação com o período anterior e próximos vencimentos. Pergunta para deixar no ar: "por que a despesa pendente não entra aqui, mas entra no orçamento?"
3. **Transações:** filtros, ordenação, criar uma despesa pendente e efetivá-la, vendo o saldo mudar.
4. **Cartão:** abrir uma fatura, mostrar as parcelas e o limite em uso. Contar o desafio do arredondamento.
5. **Orçamentos:** estados normal, alerta (80%) e excedido.
6. **Relatórios:** exportar CSV.
7. **Código**, se pedirem: um controller com `@Operation`, o service com `findForUpdate`, e um teste de integração que cria dois usuários e verifica o 404.
8. **Swagger**, se a API local estiver rodando: o botão Authorize, um erro 422 documentado com exemplo e o formato Problem Details.
