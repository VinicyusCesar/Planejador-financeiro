# Tarefa 02 — Backend financeiro integrado

Relatório finalizado em 27/10/2026, horário de São Paulo. As execuções registradas abaixo ocorreram em 26/10/2026.

## 1. Resumo da implementação

O backend existente foi ampliado sem recriar o projeto ou trocar versões da stack. Foram entregues as 12 entidades JPA, seus repositories, DTOs, serviços, controllers REST, cálculos financeiros, tratamento global de erros, validação de entradas e proteção de propriedade dos relacionamentos.

A API permite cadastrar usuários, contas, categorias e cartões; registrar, consultar, editar e cancelar transações permitidas; distribuir parcelas e faturas; efetuar pagamentos parciais/totais; transferir valores; criar orçamento mensal; manter metas e consultar dashboards mensal, semanal e de cartões.

O projeto compila, gera JAR executável e foi validado com PostgreSQL **18.6 real em uma instância isolada**. A execução completa realizou **76 testes: 76 aprovados, zero falhas, zero erros e zero ignorados**, incluindo testes concorrentes. O JAR iniciou e respondeu por HTTP aos dois endpoints de saúde usando o banco de teste.

O banco de desenvolvimento na porta 5432 não foi modificado: a senha continua indisponível neste ambiente. A correspondência do schema desse banco com V1.sql não pôde ser confirmada. As funcionalidades foram verificadas em uma cópia isolada do SQL original.

Não houve implementação Angular, uso de Docker, alteração de V1.sql, commit ou push.

## 2. Arquivos criados e alterados

Caminhos relativos à raiz. O inventário abaixo agrupa os principais arquivos por responsabilidade.

### Código de produção criado

| Local | Arquivos e responsabilidade |
| --- | --- |
| backend/src/main/java/com/planejador/model/ | Usuario, Conta, Categoria, CartaoCredito, Transacao, Fatura, Parcela, PagamentoFatura, Transferencia, Orcamento, ItemOrcamento e MetaFinanceira: mapeamentos das 12 tabelas. |
| backend/src/main/java/com/planejador/enums/ | TipoConta, TipoCategoria, TipoTransacao, MeioPagamento, CondicaoPagamento, SituacaoTransacao, SituacaoFatura e SituacaoMeta, conforme CHECKs SQL. PeriodoFatura é um filtro da API, sem tabela ou enum SQL. |
| backend/src/main/java/com/planejador/repository/ | Os 12 repositories JPA, com buscas por proprietário e bloqueio de usuário; ConsultasFinanceirasRepository agrega saldo, consumo, orçamento e faturas por SQL parametrizado. |
| backend/src/main/java/com/planejador/dto/ | UsuarioDto, ContaDto, CategoriaDto, CartaoDto, TransacaoDto, FaturaDto, TransferenciaDto, OrcamentoDto, MetaDto e DashboardDto agrupam records de entrada/saída por recurso. PaginaDto padroniza paginação; ApiErro padroniza falhas. |
| backend/src/main/java/com/planejador/service/ | UsuarioService, ContaService, CategoriaService e CartaoService implementam cadastros e integridade; TransacaoService, FaturaService e TransferenciaService coordenam movimentações atômicas; OrcamentoService e MetaService cuidam do planejamento; DashboardService monta indicadores; ReferenciaService confere existência/propriedade e bloqueia usuário; CalculosFinanceirosService calcula dinheiro, parcelas, calendário e percentuais. |
| backend/src/main/java/com/planejador/controller/ | Dez novos controllers de usuários, contas, categorias, cartões, transações, faturas, transferências, orçamentos, metas e dashboard. Recebem DTOs, delegam aos serviços e retornam códigos HTTP. |
| backend/src/main/java/com/planejador/config/FinanceiroConfig.java | Clock no fuso America/Sao_Paulo para cálculos determinísticos e testes. |
| backend/src/main/java/com/planejador/exception/ | ApiException representa erros de negócio; ApiExceptionHandler converte erros de validação, conflitos, indisponibilidade e falhas não previstas em JSON seguro. |

### Testes e ferramentas criados

- `backend/src/test/java/com/planejador/service/CalculosFinanceirosServiceTests.java`: centavos, parcelas, calendário, meses curtos, situação e percentual.
- `backend/src/test/java/com/planejador/service/TransacaoServiceTests.java`: receitas, despesas, pendências e rejeições com mocks, sem banco.
- `backend/src/test/java/com/planejador/integration/SuportePostgres.java`: contexto real, relógio fixo e fixtures exclusivas de teste.
- `backend/src/test/java/com/planejador/integration/PostgresIntegrationTests.java`: integração JPA/JDBC/HTTP MVC, saldos, compras, faturas, pagamentos, filtros, orçamento e dashboard.
- `backend/src/test/java/com/planejador/integration/ConcorrenciaPostgresTests.java`: pagamentos concorrentes, reutilização de fatura e disputa de limite.
- `backend/src/test/resources/application-postgres-test.yaml`: credenciais por ambiente para o banco isolado, mantendo Hibernate validate e scripts automáticos desabilitados.
- `backend/scripts/Test-Postgres.ps1`: cria cluster de teste em target, prepara cópia do schema, executa verify e, opcionalmente, smoke HTTP do JAR; encerra a instância ao final.

### Arquivos existentes alterados

| Arquivo | Alteração |
| --- | --- |
| backend/src/main/java/com/planejador/config/CorsConfig.java | Métodos financeiros, cabeçalho X-Usuario-Id, Content-Type e exposição de Location, mantendo origem Angular específica. |
| backend/src/main/java/com/planejador/exception/HealthExceptionHandler.java | Prioridade para preservar o contrato HTTP 503 da Tarefa 01 junto ao handler global. |
| backend/src/main/resources/application.yaml | Bind explícito em 127.0.0.1 para manter a aplicação local. Configuração PostgreSQL e proteções do schema preservadas. |
| backend/src/test/java/com/planejador/BackendApplicationTests.java | Contexto limitado ao health para o teste HTTP sem banco, enquanto os testes PostgreSQL carregam a aplicação completa. |
| backend/src/test/java/com/planejador/controller/HealthControllerTests.java | Ajuste no preflight para a lista de métodos agora suportados. |
| backend/README.md | Instruções completas de execução, testes isolados, API, decisões e limitações. |

Documentação criada: `docs/api-backend.md`, `docs/regras-financeiras.md`, `docs/consultas-sql.md` e este relatório. Nenhum arquivo foi removido.

Foram preservados pom.xml, Maven Wrapper, classe principal, endpoints de health, README da raiz e relatório da Tarefa 01. Nenhuma dependência Maven adicional foi necessária.

## 3. Mapeamento do banco

| Tabela | Entidade | Relacionamentos e particularidades |
| --- | --- | --- |
| usuario | Usuario | UUID; nome/email; criado_em mapeado para LocalDateTime sem fuso; email único no SQL. |
| conta | Conta | ManyToOne obrigatório com Usuario; saldo_inicial, tipo e ativa. |
| categoria | Categoria | ManyToOne com Usuario; tipo RECEITA/DESPESA/AMBOS; UNIQUE de usuário/nome/tipo preservado. |
| cartao_credito | CartaoCredito | Usuario obrigatório; contaPagamento opcional; final_cartao com tipo CHAR(4); limite e dias. |
| transacao | Transacao | Usuario e Categoria obrigatórios; Conta/CartaoCredito opcionais conforme meio; enums, valor e três datas. |
| fatura | Fatura | CartaoCredito obrigatório; referência mês/ano, fechamento, vencimento e situação; UNIQUE por cartão/período. |
| parcela | Parcela | Transacao e Fatura obrigatórias; número e valor; UNIQUE de transação/número. |
| pagamento_fatura | PagamentoFatura | Fatura e Conta obrigatórias; valor e data do pagamento. |
| transferencia | Transferencia | Usuario, contaOrigem e contaDestino; valor, data e situação. |
| orcamento | Orcamento | Usuario; mês/ano únicos por usuário; meta_economia opcional. |
| item_orcamento | ItemOrcamento | Orcamento e Categoria; valor_previsto; UNIQUE de orçamento/categoria. |
| meta_financeira | MetaFinanceira | Usuario; valor_objetivo, data_inicio, data_limite e situação; sem registros de aportes. |

As entidades usam @Entity, @Table com schema public, @Id UUID gerado pela aplicação, @Column com os nomes/tamanhos SQL, @ManyToOne/@JoinColumn e enums STRING. Valores monetários usam precisão 15 e escala 2. As relações são unidirecionais e lazy, sem cascatas de exclusão que apaguem histórico financeiro. Não foram criadas coleções bidirecionais desnecessárias.

V1.sql foi lido integralmente e permaneceu com o mesmo SHA-256:

~~~text
2BC31F5245FD4C4529B0534D5A12E5C4980E44A04B2EF71D564BDEE99A626BD7
~~~

Hibernate validate passou com as 12 tabelas copiadas para PostgreSQL 18.6 isolado. A coluna CHAR(4) recebeu mapeamento específico para preservar o tipo SQL. Não houve comparação autenticada com o schema real de desenvolvimento, portanto nenhuma divergência contra ele é afirmada ou descartada.

## 4. Regras financeiras

### Saldos e competência

O saldo bancário é saldo inicial mais receitas diretas liquidadas, menos despesas diretas liquidadas, mais transferências recebidas, menos enviadas e menos pagamentos de fatura. Exige CONFIRMADA e data efetiva até hoje. PENDENTE, CANCELADA e compras no crédito não debitam diretamente a conta.

O dashboard usa competência pela data da compra e pelo valor total da transação confirmada. Uma compra parcelada não é repetida nas despesas a cada parcela nem quando a fatura é paga. Transferências internas não são receitas/despesas.

### Parcelas e faturas

O total é convertido em centavos inteiros e dividido com distribuição do resto às primeiras parcelas. A soma é exata. Todas as parcelas devem ter valor positivo; há limite operacional de 600 parcelas. Crédito à vista também gera uma parcela.

A compra até o dia de fechamento entra no ciclo que fecha naquele dia; após o fechamento, no ciclo seguinte. Dias 29/30/31 são ajustados ao último dia existente. O vencimento sempre é posterior ao fechamento, usando o mês seguinte quando necessário. Parcelas usam meses consecutivos, reaproveitando fatura existente.

Total da fatura é a soma de parcelas de transações confirmadas. O saldo pendente desconta pagamentos, sem multiplicar valores em JOINs. A situação é calculada pelas datas e pagamentos e atualizada nas gravações relevantes.

### Limites, pagamentos e integridade

Limite utilizado é toda a dívida pendente do cartão, inclusive parcelas futuras e faturas antigas. Limite disponível é limite cadastrado menos utilizado. Compras confirmadas não podem exceder esse valor.

Pagamentos podem ser parciais ou totais, exigem conta ativa do proprietário e não podem exceder a dívida. Não geram transação de consumo. O pagamento reduz saldo bancário e libera o valor correspondente do limite.

Qualquer pagamento bloqueia novas compras naquela fatura e edição/cancelamento de compras vinculadas a ela. O calendário de cartão com faturas não pode ser alterado. Essas restrições conservadoras preservam o histórico sem inventar operações de estorno ausentes do escopo.

### Transferências, orçamento e indicadores

Uma transferência é um único registro transacional, refletido com sinais opostos nas contas. Cancelar remove seu efeito calculado sem excluir o registro.

Orçamento é único por mês e usuário. Os itens definem previsões por categoria de despesa. O realizado inclui todas as despesas confirmadas, com indicação separada das categorias sem previsão. Diferença = previsto menos realizado.

A semana começa na segunda e retorna sete dias. Compara total atual ao da semana anterior, com diferença monetária e percentual. Base anterior zero produz percentual null, com indicador explícito de indisponibilidade. Uma semana em andamento é comparada ao total completo da anterior.

Metas mantêm valor objetivo, datas e situação declarada. Não há progresso calculável no schema; o retorno informa indisponibilidade e valores null.

As regras completas e os exemplos estão em [regras-financeiras.md](../regras-financeiras.md).

## 5. Fluxos de execução

Os JSONs abaixo são exemplos didáticos com marcadores de UUID. Os trechos de resposta mostram os campos relevantes; o contrato completo está na documentação da API.

### Fluxo A — despesa PIX

~~~http
POST /api/transacoes
X-Usuario-Id: UUID_DO_USUARIO
Content-Type: application/json
~~~

~~~json
{
  "categoriaId":"UUID_DA_CATEGORIA","contaId":"UUID_DA_CONTA",
  "descricao":"Mercado","valor":150.00,"tipo":"DESPESA",
  "meioPagamento":"PIX","dataTransacao":"2026-10-20"
}
~~~

~~~text
HTTP → TransacaoController (Bean Validation)
     → TransacaoService (bloqueio, propriedade, categoria, valor e liquidação)
     → Usuario/Conta/CategoriaRepository (SELECT e bloqueio de usuário)
     → TransacaoRepository (INSERT em transacao)
     → PostgreSQL (constraints e commit)
     → TransacaoDto.Saida → JSON HTTP 201
~~~

A situação assume CONFIRMADA; data de liquidação assume 20/10/2026. O INSERT grava o movimento, sem atualizar uma coluna de saldo. Uma consulta posterior à conta com saldo inicial de 1.000,00 retorna 850,00.

~~~json
{"id":"UUID_DA_TRANSACAO","valor":150.00,"situacao":"CONFIRMADA","dataLiquidacao":"2026-10-20"}
~~~

### Fluxo B — compra parcelada no cartão

~~~json
{
  "categoriaId":"UUID_DA_CATEGORIA","cartaoId":"UUID_DO_CARTAO",
  "descricao":"Computador","valor":1200.00,"tipo":"DESPESA",
  "meioPagamento":"CREDITO","quantidadeParcelas":3,
  "condicao":"PARCELADO","dataTransacao":"2026-10-20"
}
~~~

~~~text
POST /api/transacoes → TransacaoController
     → TransacaoService (validação de crédito, proprietário e limite)
     → ConsultasFinanceirasRepository (SELECT da dívida atual)
     → TransacaoRepository (INSERT da compra)
     → FaturaService (centavos e calendário)
     → FaturaRepository (SELECT/reutilização ou INSERT de cada fatura)
     → ParcelaRepository (três INSERTs de parcelas)
     → PostgreSQL (uma transação atômica)
     → DTO da compra → JSON HTTP 201
~~~

Com fechamento 25 e vencimento 5, são geradas três parcelas de 400,00: referência outubro com vencimento 05/11, novembro com vencimento 05/12 e dezembro com vencimento 05/01/2027. O banco de dados aplica FKs e unicidades. A conta bancária não muda; o cartão utiliza 1.200,00 de limite. O consumo de outubro aumenta em 1.200,00.

O endpoint `GET /api/transacoes/{id}/parcelas` apresenta IDs, números, valores e vencimentos.

### Fluxo C — pagamento de fatura

Considerando a primeira fatura da compra do fluxo B:

~~~http
POST /api/faturas/UUID_DA_FATURA/pagamentos
X-Usuario-Id: UUID_DO_USUARIO
Content-Type: application/json
~~~

~~~json
{"contaId":"UUID_DA_CONTA","valor":200.00,"dataPagamento":"2026-10-26"}
~~~

~~~text
HTTP → FaturaController
     → FaturaService (bloqueio do usuário e validação da conta/fatura/valor)
     → ConsultasFinanceirasRepository (SELECT do saldo pendente)
     → PagamentoFaturaRepository (INSERT de pagamento)
     → flush JPA e consulta agregada atualizada
     → FaturaRepository/Hibernate (UPDATE da situação quando aplicável)
     → PostgreSQL (commit atômico)
     → PagamentoSaida → JSON HTTP 201
~~~

A fatura de 400,00 passa a ter 200,00 pagos e 200,00 pendentes. A conta perde 200,00; a dívida total do cartão passa de 1.200,00 para 1.000,00. O consumo de outubro continua em 1.200,00 para essa compra, sem nova despesa de 200,00.

~~~json
{"id":"UUID_DO_PAGAMENTO","faturaId":"UUID_DA_FATURA","contaId":"UUID_DA_CONTA","valor":200.00,"dataPagamento":"2026-10-26"}
~~~

## 6. Endpoints

A relação completa de métodos e exemplos encontra-se em [api-backend.md](../api-backend.md). Rotas entregues:

| Recurso | Métodos e operações |
| --- | --- |
| /api/health e /api/health/database | GET de aplicação e banco. |
| /api/usuarios | GET/POST; GET/PUT por ID. |
| /api/contas | GET/POST; GET/PUT por ID; PATCH /{id}/desativacao; GET /{id}/saldo. |
| /api/categorias | GET/POST; GET/PUT/DELETE por ID, respeitando dependências. |
| /api/transacoes | GET/POST; GET/PUT por ID; POST /{id}/cancelamento; GET /{id}/parcelas. |
| /api/cartoes | GET/POST; GET/PUT por ID; PATCH /{id}/desativacao; GET /{id}/faturas. |
| /api/faturas | GET; GET /{id}; POST /{id}/pagamentos. |
| /api/transferencias | GET/POST; GET por ID; POST /{id}/confirmacao e /{id}/cancelamento. |
| /api/orcamentos | GET/POST; GET/PUT por ID, incluindo substituição dos itens. |
| /api/metas | GET/POST; GET/PUT por ID. |
| /api/dashboard | GET /resumo-mensal, /resumo-semanal e /cartoes. |

DTOs separam entrada e saída; nenhuma entidade JPA é retornada diretamente. Os recursos financeiros exigem X-Usuario-Id; filtros de proprietário também são aplicados nos repositories e SQL. Transações e transferências possuem paginação com tamanho máximo de 100.

Criações retornam 201, consultas/atualizações 200 e operações sem corpo 204. Erros usam 400, 404, 409, 500 e, para indisponibilidade de banco, 503, além dos códigos de protocolo 405/415. O handler nunca retorna stack trace ou detalhes SQL.

## 7. Testes

### Comandos executados

Dentro de backend:

~~~powershell
.\mvnw.cmd -B -DskipTests compile
.\mvnw.cmd -B test
.\mvnw.cmd -B verify
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\Test-Postgres.ps1 -PostgresBin 'E:\postgres\18\bin' -SmokeHttp
~~~

Também foram executados initdb, pg_ctl, createdb e psql apenas para a instância isolada, com banco planejador_financeiro_test em 127.0.0.1:55432. O schema V1.sql foi aplicado exclusivamente nessa instância. A instância real na porta 5432 não recebeu nenhuma gravação.

### Resultado completo com PostgreSQL

| Classe | Testes executados | Falhas / erros / ignorados |
| --- | --- | --- |
| BackendApplicationTests | 1 | 0 / 0 / 0 |
| HealthControllerTests | 6 | 0 / 0 / 0 |
| CalculosFinanceirosServiceTests | 22 | 0 / 0 / 0 |
| TransacaoServiceTests | 9 | 0 / 0 / 0 |
| PostgresIntegrationTests | 35 | 0 / 0 / 0 |
| ConcorrenciaPostgresTests | 3 | 0 / 0 / 0 |
| **Total** | **76** | **0 / 0 / 0** |

Os testes verificam os mapeamentos reais com validate, consultas JPA/JDBC, HTTP MVC, recebimentos/despesas, liquidação, pendências, crédito à vista e parcelado, centavos, calendário, pagamentos, cancelamentos, transferências, saldo, propriedade, categorias, filtros, orçamento, metas e dashboards.

Nos testes concorrentes, duas requisições de pagamento de 80,00 disputam uma dívida de 100,00: somente uma é aprovada. Duas compras simultâneas no mesmo ciclo reutilizam uma fatura. Duas compras de 100,00 com limite de 150,00 aprovam somente uma.

### Execução sem PostgreSQL

A execução final de verify sem TEST_DB_URL também passou: **38 testes executados e aprovados**, com **36 descritores ignorados** das classes PostgreSQL. O Surefire apresentou 74 descritores nessa execução; a diferença para 76 ocorre porque casos parametrizados desabilitados não são expandidos.

Essa execução comprova que a suíte unitária/MVC não depende de PostgreSQL disponível. As integrações só são habilitadas quando a URL aponta explicitamente para 127.0.0.1, porta diferente de 5432 e banco planejador_financeiro_test.

### Inicialização do JAR e HTTP real

O script iniciou o JAR gerado na porta 18080, conectado à instância isolada, e obteve:

~~~json
{"success":true,"status":"UP"}
~~~

~~~json
{"success":true,"database":"planejador_financeiro_test","schema":"public","user":"backend_test"}
~~~

Os testes utilizam PostgreSQL 18.6 real, sem H2 ou Docker. A maioria das integrações usa rollback; testes concorrentes possuem transações próprias e gravam fixtures apenas no banco descartável. Os processos temporários são encerrados ao fim do script; logs ficam em backend/target.

A execução contra o banco real de desenvolvimento não foi feita com sucesso porque DB_PASSWORD continua ausente. Não se afirma que seu schema ou seus dados tenham sido validados.

## 8. Problemas e limitações

- **Senha do banco real ausente:** a consulta psql sem senha retornou `fe_sendauth: no password supplied`. Isso impediu inspecionar seu schema. A solução para testar o backend foi um cluster isolado com os mesmos binários PostgreSQL 18.6, sem modificar credenciais ou dados reais.
- **Execução padrão sem banco:** uma verificação intermediária identificou que a condição de ambiente na classe abstrata não desabilitava as integrações concretas. Houve 38 erros de contexto nessa execução, por ausência de TEST_DB_URL. As condições foram aplicadas explicitamente às duas classes; verify sem banco passou após a correção.
- **PowerShell no Windows:** o sistema bloqueia scripts por padrão. O comando documentado usa RemoteSigned apenas no processo executor. O inicializador pg_ctl é aguardado por WaitForExit, pois Start-Process -Wait também aguardava o servidor filho. O encerramento do smoke trata a JVM filha criada pelo launcher Oracle.
- **Avisos não impeditivos:** permanecem avisos de consulta de metadados HTTP 401 de repositórios transitivos Flyway e avisos de agente dinâmico Mockito/JDK. As dependências necessárias foram resolvidas e os builds finais passaram.
- **Progresso de metas:** não há aportes ou saldo acumulado associados à meta. O retorno é indisponível/null; não há cálculo fictício.
- **Histórico sem estorno:** não há API de estorno de pagamentos. Compras vinculadas a faturas com qualquer pagamento ficam protegidas e novas compras nessas faturas são recusadas.
- **Mudança de calendário:** cartões com faturas não alteram fechamento/vencimento. Essa política preserva as datas históricas com o schema atual.
- **Autenticação fora do escopo:** X-Usuario-Id seleciona o usuário local, não autentica sua identidade. A aplicação fica restrita ao loopback; não foi disponibilizada publicamente.
- **Sem auditoria temporal ou idempotência:** não foram criadas colunas/tabelas extras. Repetir pagamento pode gerar outro registro se houver dívida suficiente; o bloqueio e a validação impedem exceder o devido. Alterações manuais externas podem contornar regras de aplicação.
- **Situação diária de fatura:** o DTO sempre calcula a situação atual; a coluna persistida é atualizada nas operações financeiras, sem agendador diário.
- **Sem benchmark no banco real:** as consultas agregam no SQL e evitam carregar todas as transações, mas planos e desempenho com volume real não foram medidos.

Nenhuma funcionalidade obrigatória restante exige alterar o schema. As limitações acima são explicitadas nos contratos e regras; a validação operacional do banco real continua dependendo de credenciais válidas.

## 9. Próximos passos para integração Angular

1. Definir DB_PASSWORD no ambiente local e iniciar o backend contra o banco de desenvolvimento para confirmar sua correspondência com V1.sql, sem aplicar scripts automaticamente.
2. Criar os tipos TypeScript conforme os DTOs e o contrato da API.
3. Implementar a seleção local de usuário e enviar X-Usuario-Id nos recursos financeiros.
4. Construir telas de contas, categorias, transações, cartões, faturas, orçamento, metas e dashboard.
5. Tratar paginação/filtros, datas ISO, valores monetários, erros por campo e conflitos 409.
6. Distinguir saldo bancário, consumo por competência e dívida do cartão na interface.
7. Exibir claramente progresso de meta e percentual semanal indisponíveis, sem substituir null por um progresso inventado.

Os critérios de mapeamento, CRUDs, transações, parcelas, faturas, pagamentos, transferências, saldos, orçamento, dashboard, erros, compilação, testes e documentação foram atendidos e testados no ambiente isolado. A correspondência do banco real permanece uma verificação operacional pendente por credencial.

**O frontend não foi implementado. Nenhum trabalho adicional será iniciado antes da revisão do usuário.**
