# 06 — Regras financeiras

Fonte única das regras de cálculo. Cada regra deve ter teste automatizado no módulo correspondente.

## Definições

| Conceito | Regra |
|---|---|
| `PENDING` | Previsto; ainda não recebido ou pago |
| `COMPLETED` | Efetivamente recebido ou pago |
| `CANCELED` | Ignorado em todos os cálculos |
| `STANDARD` | Receita ou despesa comum, ligada a uma conta |
| `CARD_INSTALLMENT` | Parcela de cartão; não movimenta conta |
| `INVOICE_PAYMENT` | Pagamento de fatura; movimenta conta, mas não é uma nova despesa |

Valores são sempre positivos; o sentido vem de `type`.

## Saldo

```text
saldo da conta = saldo inicial
               + receitas COMPLETED da conta
               - despesas COMPLETED da conta (inclui pagamentos de fatura)

saldo total = soma dos saldos das contas não arquivadas
```

- Calculado em consulta, nunca armazenado: não existe saldo para ficar dessincronizado.
- O saldo pode ficar negativo (cheque especial). A proibição de valores negativos vale para os **valores informados**.

## Receitas, despesas e resultado do período

```text
receitas = INCOME e COMPLETED, por transaction_date
despesas = EXPENSE, STANDARD e COMPLETED
         + CARD_INSTALLMENT não cancelada
resultado = receitas - despesas
```

**Por que o cartão entra assim:** a despesa acontece na compra. Se contássemos o pagamento da fatura, a mesma compra seria somada duas vezes. Por isso `INVOICE_PAYMENT` afeta o saldo, mas nunca os totais de despesa, categorias ou orçamentos.

**Comparação:** um mês completo é comparado com o mês anterior; qualquer outro período, com o mesmo número de dias imediatamente antes. As regras são as mesmas nos dois períodos. A variação usa o valor absoluto da base, pois o resultado anterior pode ser negativo. Com período anterior zerado, a API retorna percentual nulo, e a interface mostra "sem base de comparação".

**Saldo total** não depende do período: é a posição atual das contas não arquivadas.

## Orçamentos

```text
utilizado  = despesas da categoria no mês (STANDARD PENDING ou COMPLETED
             + CARD_INSTALLMENT não cancelada)
percentual = utilizado / limite × 100
```

Pendentes entram aqui porque orçamento mede o que está comprometido, diferente do saldo.

| Percentual exato | Estado |
|---|---|
| Abaixo de 80% | `NORMAL` |
| De 80% a 100%, inclusive | `WARNING` |
| Acima de 100% | `EXCEEDED` |

A comparação usa o valor sem arredondar: 79,99% é normal. O percentual exibido é truncado em duas casas, para 79,996% nunca aparecer como 80,00%.

## Metas

```text
current_amount = initial_amount + depósitos - retiradas
progresso = min(current_amount / target_amount × 100, 100)
```

- Depósitos e retiradas devem ser maiores que zero.
- Retirada acima de `current_amount` responde 422.
- Meta concluída não aceita movimentações.
- Alcançar o valor não conclui a meta automaticamente: o usuário confirma.
- Editar o valor inicial desloca o saldo pela mesma diferença e é recusado se o saldo ficaria negativo.
- O prazo não pode estar no passado ao criar; prazo vencido em meta ativa é sinalizado.
- **Movimentar uma meta não altera o saldo das contas:** a meta é uma reserva de controle, sem transferência real.

## Cartões

### Datas da fatura

Dia inexistente no mês usa o último dia do mês: fechamento 31 em fevereiro de 2027 vira 28/02.

```text
compra com dia <= fechamento do próprio mês → fatura desse mês
compra com dia >  fechamento                → fatura do mês seguinte

vencimento: se due_day > closing_day → mesmo mês do fechamento
            caso contrário           → mês seguinte
```

Exemplo: fechamento dia 15. Compra em 15/03 entra na fatura de março; compra em 16/03, na de abril.

### Parcelamento

- A parcela `k` vai para a fatura do mês-base + `k - 1`. Faturas são criadas quando necessárias.
- Valor: total ÷ parcelas, truncado em centavos. **A diferença vai para a primeira parcela.**
- Exemplo: R$ 100,00 em 3 vezes = 33,34 + 33,33 + 33,33.
- `transaction_date` da parcela `k` = data da compra + `k - 1` meses. É ela que define o mês nas despesas e nos orçamentos.

### Fatura

```text
total    = soma das parcelas de compras não canceladas
restante = total - paid_amount
```

| Estado (derivado) | Condição, nesta ordem |
|---|---|
| `PAID` | total > 0 e restante = 0 |
| `OVERDUE` | hoje > vencimento e restante > 0 |
| `CLOSED` | hoje > fechamento |
| `OPEN` | Demais casos |

- Pagamento: maior que zero e até o restante (422 se exceder). Pagamento parcial é permitido.
- Cada pagamento gera um `INVOICE_PAYMENT` `COMPLETED` na conta escolhida.
- Ao quitar a fatura, as parcelas passam de `PENDING` para `COMPLETED`.

### Limite disponível

```text
disponível = limite - soma de (restante) das faturas não quitadas
```

Todas as parcelas futuras ocupam limite. Compra acima do disponível responde 422. Se o limite for reduzido abaixo do que já está em uso, o valor exibido é 0.

### Cancelamento

Compra só pode ser cancelada se nenhuma parcela estiver em fatura com pagamento registrado (422 caso contrário).

## Recorrências

- Job diário (02:00, `America/Sao_Paulo`): para cada recorrência `ACTIVE` com `next_occurrence_date <= hoje`, cria a transação `PENDING` e avança a data, repetindo até alcançar o presente.
- Tudo em uma transação de banco por recorrência.
- **Não duplicidade:** o índice único `(recurring_id, occurrence_date)` protege contra execução repetida; o conflito é tratado como "já gerado".
- **Lançamento excluído não volta:** como o cursor já avançou, ele não é recriado.
- Datas mensais partem do dia de `start_date`; dia inexistente usa o último do mês, sem perder o dia original nos meses seguintes.
- Pausa: nada é gerado. Ao reativar, o cursor vai para a primeira data `>= hoje`, sem lançamentos retroativos do período pausado.
- Após `end_date`, o status vira `FINISHED`.
- Editar afeta apenas as próximas ocorrências. A data de início é fixa, e uma recorrência encerrada não aceita alterações.
- Ao criar ou reativar, os ciclos já devidos são gerados na hora; o job também roda ao iniciar a aplicação, para recuperar ciclos perdidos.
- Excluir a recorrência mantém os lançamentos já gerados, sem o vínculo.

## Notificações

Job diário (02:30, `America/Sao_Paulo`, depois das recorrências) que também roda ao iniciar a aplicação. Um aviso por evento, garantido pelo índice único `(user_id, dedupe_key)`.

| Tipo | Quando | Chave |
|---|---|---|
| `BUDGET_ALERT` | Orçamento do mês corrente no estado de alerta | Orçamento |
| `BUDGET_EXCEEDED` | Orçamento do mês corrente excedido | Orçamento |
| `DUE_DATE` | Despesa comum `PENDING` vencendo de hoje a 3 dias | Transação + vencimento |
| `INVOICE_DUE` | Fatura com valor em aberto vencendo de hoje a 3 dias | Fatura |

- Quem excede sem passar pelo alerta recebe só o aviso de excedido.
- Receitas e parcelas de cartão não geram `DUE_DATE`; as parcelas são avisadas pela fatura.
- Alterar o vencimento de uma transação gera novo aviso, se a nova data estiver na janela.

## Categorias

Exclusão de categoria em uso exige `reassignTo`, com categoria de mesmo tipo, em uma única transação. Orçamentos seguem junto; se o destino já tiver orçamento naquele mês, o da categoria excluída é removido. Como alternativa, a categoria pode ser arquivada.

## Concorrência

| Operação | Proteção |
|---|---|
| Retirada e depósito em meta | Lock pessoal na meta (`PESSIMISTIC_WRITE`) |
| Compra, cancelamento e pagamento de fatura | Lock pessoal no cartão, para o limite e os pagamentos nunca serem lidos desatualizados |
| Edição simultânea | `@Version`; conflito responde 409 |
| Geração de recorrências e notificações | Índices únicos |

## Auditoria

Registrar criação, alteração e exclusão de transações, compras, pagamentos, movimentações de metas e alterações de limite, além de eventos de segurança. Nunca gravar senhas ou tokens.

- O registro entra na transação da própria alteração: se ela for desfeita, o registro também é.
- `changes` guarda os valores `before` e `after` dos campos auditados. Observações e notas, por serem texto livre, ficam de fora.
- Eventos de segurança: login realizado, logout, redefinição de senha e reutilização de refresh token.
- **Login recusado não é auditado:** a escrita extra só para e-mails existentes criaria diferença no tempo de resposta e ajudaria a descobrir quais estão cadastrados.
- Lançamentos gerados por recorrência são auditados como criação de transação. As parcelas de cartão são cobertas pelo registro da compra.

## Fora do escopo inicial

Transferência entre contas; conversão entre moedas (a moeda escolhida altera apenas a exibição); estorno parcial de compras; juros de fatura em atraso.
