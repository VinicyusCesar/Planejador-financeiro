# Regras financeiras do backend

O SQL em `backend/src/main/resources/db/migration/V1.sql` permanece a fonte dos tipos, relacionamentos e valores permitidos. Não há criação automática de tabelas ou alteração física do banco pela aplicação.

## 1. Dinheiro e datas

Valores usam BigDecimal, duas casas decimais e os limites de NUMERIC(15,2). Operações exigem valor maior que zero; orçamento e limite admitem zero. O saldo inicial segue o SQL e admite valor negativo. Não são usados double ou float em cálculos.

Datas financeiras usam LocalDate. A data atual é obtida por Clock no fuso America/Sao_Paulo, substituído por relógio fixo nos testes. CONFIRMADA não admite transação ou transferência futura. PENDENTE pode representar planejamento futuro.

## 2. Saldo bancário

~~~text
saldo atual =
saldo inicial
+ receitas diretas confirmadas e liquidadas
- despesas diretas confirmadas e liquidadas
+ transferências confirmadas recebidas
- transferências confirmadas enviadas
- pagamentos de fatura
~~~

Somente movimentos até hoje entram no saldo. Uma transação direta precisa de `data_liquidacao` não nula. Na criação/atualização de uma operação direta confirmada, a data de liquidação assume a data da transação quando omitida.

PENDENTE e CANCELADA não movimentam saldo. Crédito não movimenta a conta na compra. O pagamento da fatura é o momento da saída bancária e está em sua própria tabela. Nenhum saldo atual é persistido; PUT da conta altera somente nome e tipo.

O MVP permite saldo negativo, pois o schema não possui regra de cheque especial, limite de conta ou proibição de saldo negativo. O backend não inventa esses limites.

## 3. Consumo por competência e dashboard

Para este MVP, competência significa o mês ou a semana de `data_transacao`, considerando somente transações CONFIRMADAS. Uma compra parcelada entra pelo valor total na data da compra. As parcelas distribuem a dívida entre faturas, sem criar novas despesas de consumo.

Exemplo: compra de 1.200,00 em outubro, em três parcelas de 400,00:

| Visão | Outubro | Novembro | Dezembro |
| --- | --- | --- | --- |
| Consumo por data da compra | 1.200,00 | 0,00 | 0,00 |
| Distribuição em faturas, se a compra ocorreu até o fechamento de outubro | 400,00 | 400,00 | 400,00 |

As datas do débito bancário dependem dos pagamentos efetivos, e podem diferir dos meses de referência das faturas. Saldo do mês no dashboard é receitas menos despesas por competência; não equivale ao saldo bancário e não inclui saldo inicial.

Transferências internas e pagamentos de fatura não participam de receitas/despesas do dashboard. O meio TRANSFERENCIA em uma transação comum representa um movimento externo. Transferência entre contas próprias usa exclusivamente a tabela e a rota de transferências.

## 4. Consistência das transações

- Categoria RECEITA aceita receitas; DESPESA aceita despesas; AMBOS aceita ambos.
- Operação direta exige conta e proíbe cartão.
- Crédito exige DESPESA, cartão e nenhuma conta.
- Parcelamento é exclusivo do crédito.
- AVISTA exige uma parcela; PARCELADO exige mais de uma.
- Quantidade máxima de 600 parcelas limita a geração de registros no MVP.
- Cada parcela deve ter no mínimo um centavo.
- Operação pendente não pode ter data de liquidação.
- Liquidação direta confirmada deve ocorrer entre a data da transação e hoje.
- Crédito usa o calendário das faturas; não permite vencimento ou liquidação bancária manual na transação.
- Contas e cartões inativos não recebem novas movimentações.
- Todos os relacionamentos são conferidos contra o proprietário selecionado.
- Uma transação cancelada permanece no histórico e não pode ser editada.

Crédito PENDENTE não gera parcelas, faturas ou uso de limite. Ao confirmar por PUT, o backend valida o limite e gera as parcelas na mesma transação.

## 5. Parcelas e centavos

O total é convertido exatamente em centavos inteiros. A divisão gera uma parcela base e distribui um centavo adicional às primeiras parcelas até consumir o resto.

~~~text
100,00 / 3 = 33,34 + 33,33 + 33,33 = 100,00
~~~

Não há arredondamento que altere o total. Uma compra de 0,01 em duas parcelas é recusada porque o SQL exige valor positivo em cada parcela.

Mesmo uma compra à vista no crédito gera uma parcela, permitindo calcular todas as faturas pelo mesmo caminho.

## 6. Calendário e situação das faturas

A referência da fatura é o mês de fechamento. A compra no dia do fechamento entra nessa mesma referência. Compras posteriores entram na referência seguinte.

Para fechamento 25 e vencimento 5:

| Compra | Referência | Fechamento | Vencimento |
| --- | --- | --- | --- |
| 20/10/2026 | Outubro/2026 | 25/10/2026 | 05/11/2026 |
| 25/10/2026 | Outubro/2026 | 25/10/2026 | 05/11/2026 |
| 27/10/2026 | Novembro/2026 | 25/11/2026 | 05/12/2026 |

Cada parcela usa um mês de referência consecutivo. O dia é limitado ao último dia real do mês, incluindo fevereiro bissexto. O vencimento sempre ocorre depois do fechamento; se o dia ajustado do mesmo mês não for posterior, usa o mês seguinte.

A fatura é reutilizada por cartão, mês e ano. O banco possui UNIQUE nesses campos. As gravações financeiras do mesmo usuário também obtêm bloqueio pessimista na linha de usuário, evitando a corrida entre verificar e criar a fatura.

Situações retornadas:

- PAGA: total positivo integralmente pago.
- ATRASADA: saldo pendente positivo e data atual posterior ao vencimento.
- FECHADA: data atual posterior ao fechamento e sem as condições anteriores.
- ABERTA: demais casos; inclui o próprio dia do fechamento.

O cálculo usa as datas atuais em consultas. Gravações de compras e pagamentos atualizam também a situação persistida. Não existe um agendador diário; a coluna pode ficar com a última situação gravada até a próxima operação, enquanto a API apresenta a situação calculada.

Consultar a fatura atual usa o ciclo correspondente a hoje. Depois do fechamento, a fatura atual passa ao ciclo seguinte; a anterior continua consultável e pode continuar pendente. GET não cria faturas vazias.

## 7. Pagamentos e limite

~~~text
total da fatura = soma das parcelas de transações confirmadas
saldo pendente = máximo(total da fatura - pagamentos registrados até hoje, 0)
limite utilizado = soma dos saldos pendentes de todas as faturas do cartão
limite disponível = limite cadastrado - limite utilizado
~~~

O limite considera a dívida inteira da compra parcelada, incluindo faturas futuras. O pagamento parcial libera apenas o valor pago. Faturas antigas com dívida continuam utilizando limite.

Cada pagamento exige conta ativa do mesmo usuário, valor positivo, data até hoje e valor menor ou igual ao saldo pendente. A operação grava somente em pagamento_fatura; não cria uma nova transação de despesa.

Pagamentos concorrentes são serializados pelo bloqueio do proprietário. Um segundo pagamento consulta o saldo já atualizado depois do primeiro commit. Não há chave de idempotência no schema: repetir uma requisição pode registrar outro pagamento se ainda houver dívida, mas nunca pode ultrapassá-la pelas operações da API.

Para manter a correção com o schema atual, qualquer pagamento, inclusive parcial, bloqueia a edição/cancelamento de compras daquela fatura e a inclusão de novas compras nela. Não há estorno de pagamento nesta etapa. Uma compra com parcelas em várias faturas fica protegida se qualquer uma já recebeu pagamento.

Alterar fechamento/vencimento de um cartão com faturas é recusado para preservar o calendário existente. Reduzir o limite abaixo da dívida atual também é recusado. Nome, final e conta padrão podem ser editados com validação de propriedade.

## 8. Transferências

Uma única transferência CONFIRMADA gera, por consulta, saída na origem e entrada no destino. Sua gravação é transacional; não são criadas duas transações financeiras independentes.

As contas devem ser diferentes, ativas e do mesmo usuário. PENDENTE não gera efeito até confirmação. Cancelar remove o efeito dos dois saldos sem apagar o registro. Transferência cancelada não pode ser confirmada novamente.

## 9. Orçamento e metas

Existe um orçamento por usuário, ano e mês. Os itens são únicos por categoria, usam categorias DESPESA ou AMBOS e admitem previsão zero. PUT substitui integralmente os itens em uma transação.

O realizado segue a mesma competência do dashboard. O total realizado inclui todas as despesas confirmadas do mês, mesmo em categorias sem previsão. Cada item apresenta previsto, realizado e diferença. `realizadoSemPrevisao` explicita os gastos de categorias não cadastradas no orçamento.

A diferença é previsto menos realizado: valor negativo indica gasto acima da previsão. `metaEconomia` é uma meta declarada, sem movimentação automática de dinheiro.

MetaFinanceira não possui conta vinculada, aportes ou saldo acumulado. Por isso, o backend não calcula percentual nem valor acumulado. `progressoDisponivel=false` informa essa limitação. CONCLUIDA é uma situação declarada pelo usuário.

## 10. Semana e integridade

A semana começa na segunda e termina no domingo. O dashboard retorna sete dias, preenchendo dias sem gasto com zero. A semana selecionada é comparada à semana completa anterior; em uma semana em andamento, o total atual é naturalmente parcial.

~~~text
diferença = gasto atual - gasto anterior
variação percentual = diferença / gasto anterior * 100
~~~

Quando o gasto anterior é zero, o percentual é null e `comparacaoPercentualDisponivel=false`. Não se produz infinito ou um percentual fictício.

Operações com múltiplas gravações usam @Transactional. O bloqueio por usuário cobre compras, edições, cancelamentos, pagamentos e transferências. Antes de consultas JDBC que dependem de gravações JPA na mesma operação, é feito flush explícito. Falhas desfazem a operação inteira.

A consistência dessas regras é garantida pelas operações da API e pelas constraints existentes. Escritas manuais externas podem contornar validações que não possuem uma constraint própria no schema.
