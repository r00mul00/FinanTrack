# 02 — Estrutura de pastas

## Repositório

```text
FinanTrack/
├── backend/                  # API Spring Boot
├── frontend/                 # Blazor WebAssembly
├── docs/                     # Documentação e diagramas
├── infra/                    # Nginx e Compose de produção (Fase 9)
├── .github/workflows/        # CI/CD
├── docker-compose.yml        # Ambiente de desenvolvimento
├── .env.example              # Variáveis, sem valores reais
├── PLANO_DESENVOLVIMENTO.md
└── README.md
```

## Backend

Organizado por módulo. Dentro de cada módulo ficam as camadas exigidas.

```text
backend/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/
├── Dockerfile
└── src/
    ├── main/
    │   ├── java/com/finantrack/
    │   │   ├── FinanTrackApplication.java
    │   │   ├── common/
    │   │   │   ├── config/        # OpenAPI, CORS, Clock, agendamento
    │   │   │   ├── security/      # SecurityFilterChain, JWT, CurrentUser
    │   │   │   ├── exception/     # GlobalExceptionHandler, exceções de domínio
    │   │   │   ├── validation/    # Validadores personalizados
    │   │   │   ├── entity/        # Classe-base com id e datas
    │   │   │   └── dto/           # PageResponse
    │   │   ├── auth/              # cadastro, login, tokens, e-mail
    │   │   ├── user/              # perfil, preferências, exclusão de conta
    │   │   ├── account/
    │   │   ├── category/
    │   │   ├── transaction/
    │   │   ├── dashboard/
    │   │   ├── budget/
    │   │   ├── goal/
    │   │   ├── card/              # cartões, faturas, compras, parcelas
    │   │   ├── recurring/
    │   │   ├── notification/
    │   │   ├── report/            # consultas, CSV, PDF
    │   │   └── audit/
    │   └── resources/
    │       ├── application.yml
    │       ├── application-dev.yml
    │       ├── application-prod.yml
    │       └── db/migration/      # V1__..., V2__...
    └── test/java/com/finantrack/  # Mesma organização por módulo
```

**Interior de um módulo** (exemplo: `transaction/`):

```text
transaction/
├── controller/  TransactionController.java
├── service/     TransactionService.java
├── repository/  TransactionRepository.java
├── entity/      Transaction.java, TransactionType.java, TransactionStatus.java
├── dto/         TransactionRequest.java, TransactionResponse.java, TransactionFilter.java
└── mapper/      TransactionMapper.java
```

## Frontend

```text
frontend/
├── FinanTrack.Web.sln
├── Dockerfile
└── src/FinanTrack.Web/
    ├── FinanTrack.Web.csproj
    ├── Program.cs
    ├── App.razor
    ├── _Imports.razor
    ├── Layout/            # MainLayout, AuthLayout, NavMenu, TopBar
    ├── Pages/
    │   ├── Auth/          # Login, Cadastro, RecuperarSenha, ValidarEmail
    │   ├── Dashboard/
    │   ├── Transactions/
    │   ├── Categories/
    │   ├── Budgets/
    │   ├── Goals/
    │   ├── Cards/
    │   ├── Recurring/
    │   ├── Reports/
    │   └── Settings/      # Perfil, Contas, Preferências, Segurança
    ├── Components/        # Diálogos e peças reutilizáveis
    ├── Services/          # Um cliente HttpClient por recurso da API
    ├── Models/            # Modelos de tela e enums
    ├── DTOs/              # Contratos da API
    ├── State/             # Período selecionado, usuário, tema
    ├── Authentication/    # AuthenticationStateProvider, handler de renovação
    ├── Theme/             # MudTheme com a paleta
    ├── Validation/        # Validadores de formulário
    └── wwwroot/
        ├── index.html
        └── css/app.css
```

**Regra:** componentes só exibem o que a API calcula. Saldos, percentuais de orçamento e limites de cartão nunca são calculados no frontend.
