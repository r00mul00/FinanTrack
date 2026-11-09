# FinanTrack — Plano de Desenvolvimento

> **Controle suas finanças. Conquiste seus objetivos.**

Plataforma web de gestão financeira pessoal, em português brasileiro e com BRL como moeda padrão. O objetivo é entregar uma aplicação funcional, segura e apresentável no GitHub e em entrevistas técnicas para estágio e Desenvolvedor Júnior.

**Como usar este documento**

- Marque `[x]` em cada passo concluído e atualize a tabela de progresso.
- Uma fase só começa depois que a anterior passar pelo **portão de validação**.
- Itens marcados com **(acréscimo)** não estavam no rascunho original. Foram incluídos para cobrir dependências entre os módulos e podem ser recusados na revisão.

---

## Progresso

| Fase | Nome | Status |
|---|---|---|
| 0 | Preparação do ambiente (acréscimo) | Concluída (Docker adiado, item 0.3b) |
| 1 | Planejamento | Concluída |
| 2 | Configuração | Concluída (teste do Compose depende do item 0.3b) |
| 3 | Autenticação | Concluída |
| 4 | Transações | Concluída |
| 5 | Dashboard | Concluída (metas, vencimentos e alertas no item 6.24) |
| 6 | Recursos financeiros | Em andamento (bloco 5: Transversais, próximo item 6.22) |
| 7 | Relatórios e Configurações | Não iniciada |
| 8 | Qualidade | Não iniciada |
| 9 | Deploy | Não iniciada |
| 10 | Portfólio | Não iniciada |

---

## Regras de condução

1. Trabalhar por etapas: nunca gerar o projeto inteiro de uma vez.
2. Apresentar e justificar a arquitetura antes de escrever código.
3. Dividir as tarefas em passos pequenos.
4. Informar o caminho completo de cada arquivo a criar e onde cada código deve ficar.
5. Entregar códigos completos, sem omitir partes importantes.
6. Fornecer os comandos exatos para o terminal.
7. **Aguardar confirmação antes de avançar para uma nova etapa.**
8. Em caso de erro, investigar a causa antes de propor alterações.
9. Não modificar arquivos que não precisam ser alterados.
10. Usar dados fictícios somente em desenvolvimento e demonstração, nunca como se fossem dados reais.
11. Não considerar uma funcionalidade concluída sem testar seu comportamento.
12. Consultar a documentação oficial e a API da versão instalada. Não inventar APIs, propriedades ou parâmetros.
13. Em decisões arquiteturais importantes, apresentar as alternativas e recomendar a mais adequada.

**Ao terminar cada etapa, apresentar:** o que foi desenvolvido · arquivos criados ou modificados · como executar · como testar · resultado esperado · critérios de conclusão.

---

## Stack obrigatória

| Camada | Tecnologias |
|---|---|
| Backend | Java 21, Spring Boot, Spring Web, Spring Security, Spring Data JPA, Hibernate, PostgreSQL, JWT, Bean Validation, Flyway, Swagger/OpenAPI, JUnit 5, Mockito |
| Frontend | Blazor WebAssembly, C#, Razor Components, MudBlazor, CSS personalizado, HttpClient (REST) |
| Infraestrutura | Docker, Docker Compose, Git/GitHub, GitHub Actions, deploy em serviços compatíveis |

**Proibido:** React, Angular e Bootstrap.

**Documentação MudBlazor:** [site](https://mudblazor.com/) · [componentes](https://mudblazor.com/docs/overview) · [instalação](https://mudblazor.com/getting-started/installation) · [GitHub](https://github.com/MudBlazor/MudBlazor)

---

## Estado atual do repositório

| Item | Situação | Ação |
|---|---|---|
| Projeto Worker | Removido; substituído por `backend/` e `frontend/` | Nenhuma |
| Git | Branch `main` com remoto `origin` privado | Nenhuma |
| `.idea/` | Ignorada no `.gitignore` | Nenhuma |
| .NET SDK | 10.0.403 instalado | Nenhuma |
| JDK | Temurin 21.0.13 instalado | Nenhuma |
| PostgreSQL | 17.11 local, serviço automático. Senha de desenvolvimento em `%USERPROFILE%\.finantrack\postgres-dev.txt`, fora do repositório | Nenhuma |
| Docker | Não instalado; virtualização desativada no firmware e WSL ausente | Item 0.3b, até a Fase 9 |
| Imagem de referência visual | Recebida; usada como direção visual | Nenhuma |

---

## Decisões a confirmar na Fase 1

| # | Decisão | Recomendação | Motivo |
|---|---|---|---|
| D1 | Um repositório ou dois | Monorepo com `backend/`, `frontend/`, `docs/` | Um único link para recrutadores e um único Compose e CI |
| D2 | Maven ou Gradle | Maven | Mais comum em vagas Java júnior |
| D3 | Tipo de ID | UUID nas entidades expostas | Não revela volume e dificulta enumeração; a autorização continua obrigatória |
| D4 | Armazenamento de tokens | Access token em memória; refresh token em cookie `HttpOnly`, `Secure`, `SameSite` | Reduz a exposição a XSS em relação ao `localStorage` |
| D5 | Domínios de produção | Frontend e API sob o mesmo domínio-base | Navegadores podem bloquear cookies entre sites, o que quebraria D4 |
| D6 | Excluir categoria em uso | Exigir reatribuição para outra categoria; arquivar como alternativa | Preserva o histórico financeiro |
| D7 | Geração de recorrências | Job agendado com restrição única (`recorrência + data do ciclo`) | O banco garante a não duplicidade |
| D8 | Concorrência | Lock otimista (`@Version`) e lock pessoal nas operações críticas | Evita saldo inconsistente em metas e faturas |
| D9 | Auditoria | Tabela `audit_log` (acréscimo: não consta na lista de entidades) | O rascunho exige auditoria, mas não define onde registrá-la |
| D10 | E-mail | Mailpit em desenvolvimento e provedor SMTP em produção | Necessário para validação de e-mail e recuperação de senha |
| D11 | Biblioteca de PDF | Avaliar opções e licenças na Fase 7 | Evita escolher antes de conhecer os relatórios |
| D12 | Hospedagem | Avaliar custo e suporte a cookies na Fase 1 | Influencia D5 |

---

## Fase 0 — Preparação do ambiente (acréscimo)

**Objetivo:** deixar a máquina e o repositório prontos.

- [x] 0.1 Instalar o JDK 21 e validar com `java -version` (Temurin 21.0.13).
- [x] 0.2 Decidir pelo Maven Wrapper (`mvnw`), gerado com o projeto na Fase 2; sem Maven global.
- [x] 0.3a Instalar o PostgreSQL 17 local, no lugar do Docker por enquanto (17.11, porta 5432).
- [ ] 0.3b Docker: ativar a virtualização na BIOS, instalar WSL e Docker Desktop. Adiado; obrigatório até a Fase 9.
- [x] 0.4a Definir `main` como branch principal.
- [x] 0.4b Criar o repositório privado [`r00mul00/FinanTrack`](https://github.com/r00mul00/FinanTrack) e configurar o remoto `origin`. Primeiro commit e push só depois da limpeza do Worker (item 2.14).
- [x] 0.5 Destino do projeto Worker: manter até a Fase 2 e remover quando o Blazor for criado.
- [x] 0.6 Ajustar o `.gitignore` para Java, .NET, IDEs e arquivos `.env`.
- [x] 0.7 Fornecer a imagem de referência visual.

**Portão de validação:** JDK e PostgreSQL respondem no terminal e o repositório remoto existe. O Docker ficou fora deste portão por decisão registrada no item 0.3b.

---

## Fase 1 — Planejamento

**Objetivo:** revisar e aprovar a arquitetura. **Nenhum código de aplicação nesta fase.**

Saída: documentos em `docs/`.

- [x] 1.1 Arquitetura completa, com a justificativa de cada tecnologia ([docs/01](docs/01-arquitetura.md)).
- [x] 1.2 Estrutura de repositórios e de pastas do backend ([docs/02](docs/02-estrutura-de-pastas.md)).
- [x] 1.3 Estrutura de pastas do frontend ([docs/02](docs/02-estrutura-de-pastas.md)).
- [x] 1.4 Diagrama de arquitetura ([docs/01](docs/01-arquitetura.md)).
- [x] 1.5 Modelagem completa do banco: IDs, PKs, FKs, restrições, índices, campos obrigatórios, datas de criação e atualização ([docs/03](docs/03-banco-de-dados.md)).
- [x] 1.6 DER e relacionamentos entre as entidades ([docs/03](docs/03-banco-de-dados.md)).
- [x] 1.7 Lista inicial de endpoints REST ([docs/04](docs/04-api-endpoints.md)).
- [x] 1.8 Plano de interface: rotas, layout e identidade visual ([docs/07](docs/07-interface.md)).
- [x] 1.9 Fluxo de autenticação (login, renovação, rotação, revogação, logout) ([docs/05](docs/05-autenticacao.md)).
- [x] 1.10 Regras financeiras documentadas: saldo, faturas, orçamentos e limite do cartão ([docs/06](docs/06-regras-financeiras.md)).
- [x] 1.11 Cronograma de desenvolvimento por etapas ([docs/08](docs/08-cronograma.md)).
- [x] 1.12 Decisões D1 a D12 registradas: recomendações aceitas em 2026-11-08. Em D12, requisitos definidos e provedor escolhido no início da Fase 9.

**Entidades:** User, Account, Transaction, Category, Budget, FinancialGoal, GoalMovement, CreditCard, CreditCardInvoice, CreditCardPurchase, RecurringTransaction, Notification, RefreshToken.

**Portão de validação:** planejamento revisado e aprovado por você.

---

## Fase 2 — Configuração

**Objetivo:** backend, frontend e banco se comunicando, ainda sem regras de negócio.

- [x] 2.1 Criar o projeto Spring Boot com Java 21 e as dependências da stack.
- [x] 2.2 Montar a estrutura de pacotes definida na Fase 1 (`common/`; os módulos nascem nas suas fases).
- [x] 2.3 Criar o banco e o usuário da aplicação no PostgreSQL local. O `docker-compose.yml` (PostgreSQL e Mailpit, conforme D10) está escrito, mas só pode ser testado depois do item 0.3b.
- [x] 2.4 Configurar a conexão com o banco por variáveis de ambiente (`.env`, fora do Git).
- [x] 2.5 Configurar o Flyway e criar a primeira migration.
- [x] 2.6 Configurar Swagger/OpenAPI e o tratamento global de exceções.
- [x] 2.7 Configurar CORS restrito às origens permitidas.
- [x] 2.8 Criar o projeto Blazor WebAssembly em `frontend/`.
- [x] 2.9 Verificar a versão do MudBlazor compatível com o .NET utilizado e instalá-la pela documentação oficial.
- [x] 2.10 Criar o tema: paleta, fonte Inter e CSS de identidade visual.
- [x] 2.11 Criar o layout-base: `MudLayout`, `MudAppBar`, `MudDrawer`, `MudNavMenu`.
- [x] 2.12 Criar o `HttpClient` configurado e um teste de ponta a ponta com um endpoint de saúde.
- [x] 2.13 Criar a pipeline inicial no GitHub Actions: build e testes (acréscimo).
- [x] 2.14 Remover o projeto Worker (`FinanTrack/`, `FinanTrack.sln`) e, depois da limpeza, fazer o primeiro commit e o push.

**Portão de validação:** as migrations são aplicadas no PostgreSQL local, o Swagger abre e o frontend exibe uma resposta real da API. `docker compose up` entra neste portão assim que o item 0.3b for concluído.

---

## Fase 3 — Autenticação

**Objetivo:** identidade do usuário e isolamento de dados desde o início.

**Backend**

- [x] 3.1 Migrations de `User` e `RefreshToken`.
- [x] 3.2 Cadastro com BCrypt.
- [x] 3.3 Login com emissão de JWT.
- [x] 3.4 Validação de JWT no Spring Security.
- [x] 3.5 Refresh Token com rotação e revogação; armazenar apenas o hash.
- [x] 3.6 Detecção de reutilização de refresh token (acréscimo).
- [x] 3.7 Logout com invalidação do refresh token.
- [x] 3.8 Validação de e-mail.
- [x] 3.9 Recuperação de senha, com token de uso único e expiração.
- [x] 3.10 Limite de tentativas em login e recuperação (acréscimo).
- [x] 3.12 Testes de autenticação e de cadastro.

**Frontend**

- [x] 3.13 Telas de login, cadastro, recuperação e validação de e-mail.
- [x] 3.14 `AuthenticationStateProvider` e proteção de rotas.
- [x] 3.15 Renovação automática de sessão no `HttpClient`.
- [x] 3.16 Botão Sair: encerra a sessão, invalida o refresh token, remove credenciais e redireciona ao login.

**Portão de validação:** é possível percorrer cadastro, login, renovação e logout; rota protegida sem autenticação é recusada; refresh token antigo não funciona depois da rotação.

---

## Fase 4 — Transações

**Objetivo:** primeiro módulo financeiro completo, que serve de padrão para os demais.

**Contas (acréscimo).** A transação exige uma Conta, mas o rascunho não define sua gestão.

- [x] 4.1 CRUD de Conta, com tela em uma aba de Configurações, saldo calculado pelas movimentações e bloqueio de exclusão de conta com movimentações.

**Categorias**

- [x] 4.2 Criar, editar e excluir, separando receita e despesa.
- [x] 4.3 Definir ícones (Material Design) e cores.
- [x] 4.3b Criar as categorias padrão no cadastro de novos usuários (movido da Fase 3: depende da tabela de categorias).
- [x] 4.4 Aplicar a estratégia de exclusão de categoria em uso (D6). Cada módulo que referencia categorias implementa `CategoryReferenceHandler`; o primeiro será o de transações.

**Transações**

- [x] 4.5 Migration com índices para os filtros.
- [x] 4.6 API de CRUD com DTOs, Bean Validation e verificação de dono em toda consulta.
- [x] 4.7 Pesquisa por descrição; filtros por período, categoria, tipo e status; ordenação e paginação.
- [x] 4.8 Listagem com `MudDataGrid` e paginação no servidor.
- [x] 4.9 Cadastro e edição em `MudDialog`. Cartão e indicador de recorrência entram na Fase 6, com aqueles módulos.
- [x] 4.10 Pesquisa com debounce e opção de limpar; filtros combináveis e opção de limpar filtros.
- [x] 4.11 Testes: CRUD, categorias, cálculo de saldo e isolamento de dados entre usuários.

**Campos da transação:** descrição, valor, tipo, categoria, conta, cartão (opcional), data da movimentação, data de vencimento (opcional), status, observações, indicador de recorrência.

**Portão de validação:** todos os botões atendem aos comportamentos da seção de referência, e o usuário B recebe recusa ao tentar acessar um registro do usuário A pelo ID.

---

## Fase 5 — Dashboard

**Objetivo:** indicadores calculados com dados reais do usuário autenticado.

- [x] 5.1 Endpoints agregados por período, com consultas eficientes.
- [x] 5.2 Saldo total, receitas do mês, despesas do mês e resultado financeiro.
- [x] 5.3 Comparação com o mês anterior.
- [x] 5.4 Gráfico de receitas versus despesas.
- [x] 5.5 Gráfico de despesas por categoria.
- [x] 5.6 Últimas transações.
- [x] 5.7 Seletor de período atualizando todos os componentes dependentes.
- [x] 5.8 Estados de carregamento (`MudSkeleton`) e estado vazio.
- [x] 5.9 Testes dos cálculos, incluindo a exclusão de pendentes do saldo realizado.

> Metas, próximos vencimentos e alertas de orçamento dependem da Fase 6 e são concluídos no item 6.24.

**Portão de validação:** os números conferem com um conjunto de transações calculado manualmente.

---

## Fase 6 — Recursos financeiros

**Objetivo:** módulos com as regras de negócio mais críticas. Cada bloco é validado separadamente.

**Orçamentos**

- [x] 6.1 Criar, editar e excluir orçamento mensal, com limite por categoria.
- [x] 6.2 Calcular valor utilizado e percentual consumido.
- [x] 6.3 Aplicar os estados: até 79% normal; de 80% a 100% alerta; acima de 100% excedido.
- [x] 6.4 Testes, incluindo os limites de 79%, 80%, 100% e acima de 100%.

**Metas financeiras**

- [x] 6.5 CRUD com nome, descrição, valor desejado, valor inicial e prazo.
- [x] 6.6 Adicionar valor, com validação de valor positivo.
- [x] 6.7 Retirar valor, com confirmação e bloqueio acima do saldo da meta.
- [x] 6.8 Histórico de movimentações e marcação como concluída.
- [x] 6.9 Barras de progresso e percentual concluído.
- [x] 6.10 Testes, incluindo retiradas simultâneas.

**Cartões**

- [x] 6.11 Cadastro: apelido, bandeira, quatro últimos dígitos, limite, dia de fechamento e dia de vencimento.
- [x] 6.12 Garantir que número completo, CVV e senha nunca sejam aceitos nem armazenados.
- [x] 6.13 Compras parceladas distribuídas entre as faturas, com tratamento de arredondamento.
- [x] 6.14 Geração e consulta de faturas, respeitando o dia de fechamento e meses curtos.
- [x] 6.15 Registro de pagamentos e cálculo do limite disponível.
- [x] 6.16 Testes de limite, parcelamento e faturas.

**Contas recorrentes**

- [x] 6.17 Criar receitas e despesas recorrentes, com frequência, início e término opcional.
- [x] 6.18 Pausar e reativar.
- [x] 6.19 Consultar próximas ocorrências.
- [x] 6.20 Geração sem duplicidade em cada ciclo (D7), com teste de execução repetida.

**Transversais (acréscimo)**

- [x] 6.21 Central de notificações, ligada à `MudAppBar`.
- [ ] 6.22 Auditoria das alterações relevantes (D9).
- [ ] 6.23 Campo de pesquisa global da barra superior.
- [ ] 6.24 Concluir o Dashboard: metas, próximos vencimentos e alertas de orçamento.

**Portão de validação:** cada bloco testado e aprovado, com as regras de negócio cobertas por testes automatizados.

---

## Fase 7 — Relatórios e Configurações

**Relatórios**

- [ ] 7.1 Filtros e indicadores.
- [ ] 7.2 Relatórios mensais, anuais, por categoria e por período.
- [ ] 7.3 Receitas versus despesas, evolução financeira e despesas por cartão.
- [ ] 7.4 Exportação em CSV e PDF, respeitando os filtros selecionados (D11).
- [ ] 7.5 Download no navegador e mensagem de erro caso a geração falhe.

**Configurações (acréscimo de cronograma).** O módulo está no rascunho, mas não aparece em nenhuma fase.

- [ ] 7.6 Alterar nome, e-mail (com validação) e senha.
- [ ] 7.7 Configurar moeda e notificações.
- [ ] 7.8 Alternar tema claro e escuro.
- [ ] 7.9 Exportar dados pessoais.
- [ ] 7.10 Solicitar exclusão da conta.

**Portão de validação:** totais dos arquivos exportados iguais aos da tela com os mesmos filtros.

---

## Fase 8 — Qualidade

- [ ] 8.1 Completar os testes: autenticação, cadastro, transações, saldo, categorias, orçamentos, metas, cartões, parcelamento, isolamento de dados e tratamento de erros.
- [ ] 8.2 Testar integração com PostgreSQL real, por exemplo com Testcontainers (acréscimo).
- [ ] 8.3 Revisar a segurança pela checklist de referência.
- [ ] 8.4 Revisar autorização por usuário em todos os endpoints.
- [ ] 8.5 Analisar performance: consultas N+1, índices e paginação.
- [ ] 8.6 Padronizar respostas de erro, sem expor detalhes internos.
- [ ] 8.7 Refatorar.
- [ ] 8.8 Verificar responsividade em desktop, notebook, tablet e smartphone.
- [ ] 8.9 Verificar hover, focus, disabled, loading e feedback visual.

**Portão de validação:** CI verde e checklists de segurança e responsividade concluídas.

---

## Fase 9 — Deploy

- [ ] 9.1 Dockerfiles de produção para backend e frontend.
- [ ] 9.2 Configuração de ambiente e segredos, fora do repositório.
- [ ] 9.3 Publicação do banco, da API e do frontend (D5 e D12).
- [ ] 9.4 CORS, HTTPS e cookies configurados para o domínio real.
- [ ] 9.5 Pipeline de deploy no GitHub Actions.
- [ ] 9.6 Testes de produção: cadastro, login, renovação de sessão, CRUD e exportação.

**Portão de validação:** fluxo completo funcionando na URL pública.

---

## Fase 10 — Portfólio

- [ ] 10.1 README profissional: descrição do produto e tecnologias.
- [ ] 10.2 Arquitetura, com diagrama de arquitetura e diagrama do banco.
- [ ] 10.3 Instruções de execução, variáveis de ambiente, Docker Compose e deploy.
- [ ] 10.4 Documentação Swagger e exemplos de requisições.
- [ ] 10.5 Prints das telas, usando somente dados fictícios de demonstração.
- [ ] 10.6 Descrição dos testes e de como executá-los.
- [ ] 10.7 Organização do GitHub.
- [ ] 10.8 Preparação para entrevista técnica: decisões, trade-offs e desafios do projeto.

**Portão de validação:** uma pessoa que não conhece o projeto consegue executá-lo apenas com o README.

---

# Referência

## Requisitos de interface

**Identidade visual:** SaaS moderno, tema escuro padrão, minimalista e sofisticado, sidebar lateral, cards arredondados, gráficos modernos, tabelas organizadas, ícones consistentes, microinterações e layout responsivo. Fonte Inter. Ícones Material Design do ecossistema MudBlazor; **não usar emojis como ícones**.

| Token | Cor |
|---|---|
| Primary | `#7C4DFF` |
| Background | `#10111B` |
| Surface | `#1B1D2D` |
| Surface Secondary | `#25273A` |
| Success | `#20C997` |
| Error | `#FF647C` |
| Warning | `#FFB547` |
| Text Primary | `#F5F5FA` |
| Text Secondary | `#A6A8BC` |

**Sidebar:** Dashboard, Transações, Categorias, Orçamentos, Metas financeiras, Cartões, Contas recorrentes, Relatórios, Configurações. Recolhível em telas maiores e adaptada como menu em dispositivos móveis. Rodapé com nome, e-mail e botão de sair.

**Barra superior:** logo, pesquisa, notificações, seletor de período quando aplicável, avatar e menu de perfil.

**Em dispositivos móveis:** menu recolhível, cards reorganizados, tabelas adaptadas ou com rolagem horizontal, formulários na largura disponível, botões acessíveis e gráficos redimensionados.

**Componentes prioritários:** MudLayout, MudAppBar, MudDrawer, MudNavMenu, MudCard, MudButton, MudIconButton, MudDialog, MudTextField, MudNumericField, MudSelect, MudDatePicker, MudDataGrid, MudChart, MudProgressLinear, MudSnackbar, MudAlert, MudTabs, MudPagination, MudTooltip, MudMenu, MudSwitch, MudForm, MudSkeleton.

## Comportamento dos botões

| Ação | Comportamento obrigatório |
|---|---|
| Salvar | Validar, impedir envio duplicado, mostrar carregamento, enviar à API, fechar o diálogo em caso de sucesso, atualizar os dados e mostrar Snackbar |
| Cancelar | Fechar sem enviar requisição; confirmar se houver alterações não salvas |
| Editar | Carregar os dados, preencher o formulário e salvar com atualização na API |
| Excluir | Confirmar identificando claramente o item, permitir cancelar, excluir só após confirmação e atualizar a interface |
| Pesquisar | Buscar por descrição, atualizar resultados, permitir limpar e aplicar debounce quando apropriado |
| Filtros | Combinar filtros, atualizar a listagem, manter a paginação consistente e permitir limpar |
| Adicionar à meta | Abrir formulário, validar valor positivo, registrar movimentação e atualizar saldo e progresso |
| Retirar da meta | Validar saldo, confirmar, registrar no histórico e atualizar progresso |
| Exportar | Gerar arquivo, disponibilizar download, respeitar filtros e exibir erro em caso de falha |
| Sair | Encerrar sessão, invalidar refresh token, remover credenciais e redirecionar ao login |

## Regras de negócio

- Valores monetários usam `BigDecimal` no Java e `NUMERIC/DECIMAL` no PostgreSQL. **Nunca `float` ou `double`.**
- Valores financeiros não podem ser negativos.
- Receitas e despesas precisam ser identificadas corretamente.
- Toda transação pertence a um usuário.
- **Um usuário jamais pode consultar, editar ou excluir dados de outro, mesmo conhecendo o ID.**
- Transações pendentes não entram no saldo realizado.
- Lançamentos recorrentes não podem ser duplicados.
- Retiradas não podem superar o saldo da meta.
- O limite disponível do cartão, as compras parceladas e as faturas devem ser calculados corretamente, considerando datas e vencimentos.
- Operações críticas usam transações de banco (`@Transactional`) e proteção contra concorrência inconsistente.
- Alterações relevantes são auditadas.
- As regras de cálculo de saldo, faturas e orçamentos devem estar documentadas.

## Arquitetura de código

**Backend (módulos e camadas):** `controller`, `service`, `repository`, `entity`, `dto`, `mapper`, `config`, `security`, `exception`, `validation`.

Aplicar DTOs de entrada e saída, Bean Validation, tratamento global de exceções, separação de responsabilidades, injeção de dependência, SOLID, paginação, consultas eficientes e `@Transactional`.

**Frontend:** `Layout`, `Pages`, `Components`, `Services`, `Models`, `DTOs`, `State`, `Authentication`, `Theme`, `Validation`.

**Regras financeiras não ficam nos componentes visuais.**

## Checklist de segurança

- [ ] Senhas com BCrypt.
- [ ] JWT validado em toda requisição protegida.
- [ ] Autorização por usuário em todas as operações.
- [ ] CORS restrito às origens permitidas.
- [ ] Validação de entrada.
- [ ] Erros tratados sem expor informações internas.
- [ ] Segredos em variáveis de ambiente.
- [ ] Tokens protegidos no frontend; sem tokens sensíveis em `localStorage` sem avaliação de riscos.
- [ ] Nenhum número completo de cartão, CVV ou senha de cartão armazenado.
