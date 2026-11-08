# 08 — Cronograma por etapas

Estimativa em **sessões de trabalho de 2 a 3 horas**, com validação ao fim de cada entrega. Ajustar depois das Fases 2 e 3, quando houver ritmo real para comparar.

| Fase | Entregas, em ordem | Sessões | Depende de |
|---|---|---|---|
| 2. Configuração | Backend e banco → frontend e tema → layout → CI → limpeza e primeiro push | 3 a 4 | Fase 1 aprovada |
| 3. Autenticação | Cadastro e login → JWT e refresh → e-mails → telas | 5 a 6 | 2 |
| 4. Transações | Contas → categorias → API de transações → tela | 5 a 6 | 3 |
| 5. Dashboard | Consultas → indicadores → gráficos | 2 a 3 | 4 |
| 6. Recursos financeiros | Orçamentos → metas → cartões e faturas → recorrências → notificações e auditoria → fim do Dashboard | 9 a 12 | 5 |
| 7. Relatórios e Configurações | Relatórios → CSV e PDF → configurações | 4 a 5 | 6 |
| 8. Qualidade | Testes de integração → segurança → performance → responsividade | 3 a 4 | 7 e Docker |
| 9. Deploy | Docker → escolha do provedor → publicação → testes de produção | 2 a 3 | 8 |
| 10. Portfólio | README → prints → documentação → entrevista | 1 a 2 | 9 |
| **Total** | | **34 a 45** | |

Os testes unitários de cada módulo são escritos na própria fase; a Fase 8 completa a cobertura e adiciona os testes de integração.

**Maior risco:** o módulo de cartões, por combinar datas, parcelamento, limite e concorrência.

**Corte possível, se o prazo apertar:** pesquisa global, controle de sessões por dispositivo e exportação de dados pessoais. Nenhum deles afeta as regras financeiras.
