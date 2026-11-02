# Consultas SQL para apresentação acadêmica

Implementação principal: `backend/src/main/java/com/planejador/repository/ConsultasFinanceirasRepository.java`. Os repositories JPA cuidam dos cadastros, relacionamentos e gravações; as agregações financeiras usam NamedParameterJdbcTemplate.

Os exemplos usam parâmetros nomeados, preenchidos pelo JDBC. Não concatene valores recebidos do cliente. O search_path da aplicação aponta para public. Os exemplos são consultas de leitura e não devem ser adaptados para alterar dados reais durante testes.

## 1. Saldo de uma conta

~~~sql
SELECT c.saldo_inicial
  + COALESCE((
      SELECT SUM(CASE WHEN t.tipo = 'RECEITA' THEN t.valor ELSE -t.valor END)
      FROM transacao t
      WHERE t.conta_id = c.id AND t.usuario_id = c.usuario_id
        AND t.situacao = 'CONFIRMADA' AND t.meio_pagamento <> 'CREDITO'
        AND t.data_liquidacao IS NOT NULL AND t.data_liquidacao <= :hoje
    ), 0)
  + COALESCE((
      SELECT SUM(tr.valor) FROM transferencia tr
      WHERE tr.conta_destino_id = c.id AND tr.usuario_id = c.usuario_id
        AND tr.situacao = 'CONFIRMADA' AND tr.data_transferencia <= :hoje
    ), 0)
  - COALESCE((
      SELECT SUM(tr.valor) FROM transferencia tr
      WHERE tr.conta_origem_id = c.id AND tr.usuario_id = c.usuario_id
        AND tr.situacao = 'CONFIRMADA' AND tr.data_transferencia <= :hoje
    ), 0)
  - COALESCE((
      SELECT SUM(p.valor) FROM pagamento_fatura p
      JOIN fatura f ON f.id = p.fatura_id
      JOIN cartao_credito cc ON cc.id = f.cartao_id
      WHERE p.conta_id = c.id AND cc.usuario_id = c.usuario_id
        AND p.data_pagamento <= :hoje
    ), 0) AS saldo
FROM conta c
WHERE c.id = :contaId AND c.usuario_id = :usuarioId;
~~~

Cada origem financeira é agregada separadamente. A compra no crédito fica fora da conta; seu pagamento entra uma única vez por pagamento_fatura. Transferências entram com sinais opostos nas duas contas. COALESCE transforma a ausência de movimentos em zero.

O saldo inicial é um valor cadastrado. O saldo atual é resultado dessa consulta, sem coluna materializada ou atualização manual a cada operação.

## 2. Receitas e gastos mensais

~~~sql
SELECT
  COALESCE(SUM(CASE WHEN tipo = 'RECEITA' THEN valor ELSE 0 END), 0) AS receitas,
  COALESCE(SUM(CASE WHEN tipo = 'DESPESA' THEN valor ELSE 0 END), 0) AS despesas
FROM transacao
WHERE usuario_id = :usuarioId AND situacao = 'CONFIRMADA'
  AND data_transacao >= :inicio AND data_transacao < :fim;
~~~

Para outubro/2026, inicio = 01/10/2026 e fim = 01/11/2026. O intervalo inclui o início e exclui o fim. O saldo por competência é calculado no serviço como receitas menos despesas.

Crédito entra pelo valor total da compra na data da transação. Parcelas e pagamentos não são somados novamente. O foco dessa consulta é consumo, diferente do saldo bancário da consulta anterior.

## 3. Gastos semanais e por dia

A consulta de totais é reutilizada com inicio na segunda-feira e fim na segunda seguinte. A semana anterior usa o intervalo imediatamente anterior. O serviço calcula diferença e variação percentual, protegendo base zero.

~~~sql
SELECT data_transacao, SUM(valor) AS total
FROM transacao
WHERE usuario_id = :usuarioId AND situacao = 'CONFIRMADA'
  AND tipo = 'DESPESA'
  AND data_transacao >= :inicio AND data_transacao < :fim
GROUP BY data_transacao
ORDER BY data_transacao;
~~~

GROUP BY gera uma linha por dia com gasto. O serviço completa os sete dias da semana com zero quando não há linha correspondente. Não carrega todas as transações para fazer a soma em Java.

## 4. Gastos por categoria

~~~sql
SELECT c.id, c.nome, SUM(t.valor) AS total
FROM transacao t
JOIN categoria c ON c.id = t.categoria_id AND c.usuario_id = t.usuario_id
WHERE t.usuario_id = :usuarioId AND t.situacao = 'CONFIRMADA'
  AND t.tipo = 'DESPESA'
  AND t.data_transacao >= :inicio AND t.data_transacao < :fim
GROUP BY c.id, c.nome
ORDER BY total DESC, c.nome, c.id;
~~~

JOIN obtém o nome da categoria e confere o proprietário. GROUP BY agrupa pela PK e nome. A mesma consulta alimenta o dashboard e os valores realizados de cada item do orçamento.

## 5. Faturas, pagamentos e limites

A implementação usa CTEs para somar parcelas e pagamentos antes de fazer o JOIN final. A versão abaixo corresponde à consulta sem filtro adicional de cartão ou fatura:

~~~sql
WITH faturas_usuario AS (
  SELECT f.*
  FROM fatura f JOIN cartao_credito c ON c.id = f.cartao_id
  WHERE c.usuario_id = :usuarioId
), compras AS (
  SELECT p.fatura_id, SUM(p.valor) AS total
  FROM parcela p
  JOIN transacao t ON t.id = p.transacao_id
  JOIN faturas_usuario f ON f.id = p.fatura_id
  WHERE t.situacao = 'CONFIRMADA' AND t.usuario_id = :usuarioId
  GROUP BY p.fatura_id
), pagamentos AS (
  SELECT p.fatura_id, SUM(p.valor) AS total
  FROM pagamento_fatura p JOIN faturas_usuario f ON f.id = p.fatura_id
  WHERE p.data_pagamento <= :hoje
  GROUP BY p.fatura_id
)
SELECT f.id, f.cartao_id, f.mes_referencia, f.ano_referencia,
       f.data_fechamento, f.data_vencimento,
       COALESCE(c.total, 0) AS total,
       COALESCE(p.total, 0) AS pago,
       GREATEST(COALESCE(c.total, 0) - COALESCE(p.total, 0), 0) AS pendente
FROM faturas_usuario f
LEFT JOIN compras c ON c.fatura_id = f.id
LEFT JOIN pagamentos p ON p.fatura_id = f.id
ORDER BY f.ano_referencia, f.mes_referencia, f.cartao_id;
~~~

Um JOIN direto entre duas parcelas e dois pagamentos produziria quatro linhas e poderia duplicar as somas. A pré-agregação evita esse erro. Há teste de integração específico com duas compras e dois pagamentos.

Filtros opcionais de cartão e fatura acrescentam apenas cláusulas fixas parametrizadas na CTE inicial. O total utilizado do cartão é a soma dos saldos pendentes das suas faturas, já agregadas pelo banco. O dashboard agrupa esses resumos por cartão sem carregar transações individuais.

## 6. Planejamento

~~~sql
SELECT COALESCE(SUM(i.valor_previsto), 0)
FROM item_orcamento i
JOIN orcamento o ON o.id = i.orcamento_id
WHERE o.usuario_id = :usuarioId AND o.ano = :ano AND o.mes = :mes;
~~~

O previsto vem dos itens. O realizado vem da consulta de gastos por categoria, com o mesmo período e critério de competência. O serviço associa os resultados agregados aos itens e apresenta também os gastos de categorias sem previsão.

A restrição UNIQUE de orcamento garante um registro por usuário/mês/ano. A restrição UNIQUE de item_orcamento evita repetir a mesma categoria naquele orçamento.

## 7. Consultas JPA e integridade

- Cadastros e alterações usam os repositories das 12 entidades. Hibernate gera INSERT/UPDATE/DELETE conforme a operação autorizada, respeitando FKs e constraints.
- Consultas por ID financeiro combinam ID e proprietário, como `findByIdAndUsuarioId`.
- Transações e transferências usam Specification para filtros e PageRequest para LIMIT/OFFSET. O total de elementos é obtido por COUNT quando necessário pelo mecanismo de paginação do Spring Data.
- O método `UsuarioRepository.bloquear` usa JPQL com PESSIMISTIC_WRITE; PostgreSQL emite bloqueio de linha. Ele protege a sequência leitura, validação e gravação por usuário.
- Escritas múltiplas são atômicas por @Transactional. Um flush explícito sincroniza JPA antes de agregações JDBC dependentes dessas alterações.

Os índices já existentes ajudam filtros por usuário/data e relacionamentos de transação, parcela, pagamento e transferência. Nenhum índice foi criado ou alterado nesta tarefa. A validação funcional das consultas ocorreu em PostgreSQL 18.6 isolado; não foi feito benchmark ou EXPLAIN sobre os dados reais.

## 8. Verificação de conexão

~~~sql
SELECT current_database() AS database_name,
       current_schema() AS schema_name,
       current_user AS user_name;
~~~

Essa é a consulta de `/api/health/database`. Ela confirma a sessão, sem escrever registros ou demonstrar que todas as regras financeiras foram testadas. Os testes de integração complementam essa verificação.

Referências de infraestrutura: [JDBC no Spring Boot](https://docs.spring.io/spring-boot/reference/data/sql.html) e [bloqueios no Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html).
