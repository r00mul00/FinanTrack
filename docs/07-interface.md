# 07 — Plano de interface

> A imagem de referência recebida orienta a composição: sidebar em cartão, cards arredondados, tema escuro e destaques em roxo com gradiente. Ela é apenas direção visual: nome, marca e conteúdo são do FinanTrack, e as cores seguem a paleta do plano.

## Rotas

| Rota | Tela | Layout |
|---|---|---|
| `/login`, `/cadastro` | Acesso | AuthLayout |
| `/recuperar-senha`, `/redefinir-senha`, `/validar-email` | Fluxos por e-mail | AuthLayout |
| `/` | Dashboard | MainLayout |
| `/transacoes` | Transações | MainLayout |
| `/categorias` | Categorias | MainLayout |
| `/orcamentos` | Orçamentos | MainLayout |
| `/metas`, `/metas/{id}` | Metas e histórico | MainLayout |
| `/cartoes`, `/cartoes/{id}` | Cartões, faturas, compras | MainLayout |
| `/recorrencias` | Contas recorrentes | MainLayout |
| `/relatorios` | Relatórios | MainLayout |
| `/configuracoes` | Abas: Perfil, Contas, Preferências, Segurança | MainLayout |

**Contas** fica em uma aba de Configurações, mantendo os nove itens da sidebar definidos no rascunho.

## Layout principal

```text
┌──────────────┬───────────────────────────────────────────────┐
│ FinanTrack   │ [Pesquisar...]      [Período] [Sino] [Avatar] │
│              ├───────────────────────────────────────────────┤
│ Dashboard    │                                               │
│ Transações   │                                               │
│ Categorias   │                Área da página                 │
│ Orçamentos   │                                               │
│ Metas        │                                               │
│ Cartões      │                                               │
│ Recorrentes  │                                               │
│ Relatórios   │                                               │
│ Configurações│                                               │
│──────────────│                                               │
│ Nome         │                                               │
│ e-mail  Sair │                                               │
└──────────────┴───────────────────────────────────────────────┘
```

| Largura | Comportamento |
|---|---|
| Desktop e notebook | `MudDrawer` responsivo, recolhível pelo botão da barra superior |
| Tablet e smartphone | Drawer temporário, aberto pelo botão da `MudAppBar` |

## Dashboard

```text
[ Saldo total ] [ Receitas ] [ Despesas ] [ Resultado ]
[ Receitas x Despesas (2/3)   ] [ Por categoria (1/3) ]
[ Últimas transações (2/3)    ] [ Próximos vencimentos ]
[ Metas (1/2)                 ] [ Alertas de orçamento ]
```

Grade com `MudGrid`: 4 colunas no desktop, 2 no tablet, 1 no smartphone.

## Padrões de tela

| Situação | Padrão |
|---|---|
| Listagem | `MudDataGrid` com paginação no servidor e rolagem horizontal em telas pequenas |
| Criar e editar | `MudDialog` com `MudForm`; tela cheia em smartphone |
| Excluir | Diálogo que cita o nome do item |
| Carregando | `MudSkeleton` no conteúdo e indicador no botão |
| Sem dados | Mensagem com a ação principal |
| Erro | `MudAlert` na página ou `MudSnackbar` na ação |
| Sucesso | `MudSnackbar` |

Os comportamentos de cada botão estão na referência do `PLANO_DESENVOLVIMENTO.md`.

## Tema

`MudTheme` definido em `Theme/`, com a paleta do plano para o modo escuro; a variante clara será definida na Fase 7. Fonte Inter. Os parâmetros dos componentes serão conferidos na documentação do MudBlazor 9 antes de cada implementação.
