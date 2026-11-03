# Migração para SQL Server — relatório de implementação

Data: 03/11/2026. **Aceite de integração pendente**: usuário confirmou que ainda não possui instância SQL Server disponível. Nenhum resultado de autenticação, migração física, integridade ou concorrência SQL Server foi declarado aprovado.

## Alterações

O backend existente recebeu um profile `sqlserver`, sem reconstrução de serviços ou alteração de endpoints/DTOs. O Angular não foi modificado. Permanecem `BigDecimal`, datas financeiras `LocalDate`, `@Transactional`, saldo calculado e a serialização das escritas por proprietário.

- Maven: driver oficial Microsoft 13.4.0.jre11 e módulo Flyway SQL Server 12.4.0, ambos nas versões gerenciadas pelo Spring Boot 4.1.2/Java 21 existentes. Os drivers PostgreSQL foram mantidos.
- `application.yaml`: configuração original habilitada apenas quando `sqlserver` não está ativo. As propriedades PostgreSQL, inclusive schema `public`, continuam com os valores anteriores. Assim, a senha PostgreSQL não é resolvida nem vinculada no profile novo.
- `application-sqlserver.properties`: NativeAuthentication, TLS validado por padrão, endpoint obrigatório, sem username/password, isolamento READ COMMITTED, Flyway dedicado a `db/sqlserver` e Hibernate `validate`.
- `SqlServerAuthenticationConfig`: recusa datasource com credenciais, URL sem autenticação integrada nativa ou propriedades alternativas de conexão. Testes locais cobrem aceitação da configuração nativa e rejeição de credencial externa/autenticação desabilitada.
- As 12 entidades passam a usar o schema padrão do profile, em vez de fixar `public` nas anotações. Não foi alterado o schema físico PostgreSQL.
- `V1__schema.sql`: 12 tabelas, 12 PKs, 20 FKs, seis restrições UNIQUE (incluindo email), oito índices explícitos e nove colunas monetárias DECIMAL(15,2). Mantidas regras CHECK/NOT NULL e referências sem cascata de exclusão.
- Strings UTF-8 usam collation binária para distinguir caixa/acentos. Campos de texto livre reservam até quatro bytes por caractere e CHECK de comprimento, preservando os limites em caracteres do PostgreSQL mesmo com acentos. A aplicação continua removendo espaços externos conforme os serviços existentes; comparação e ordenação de texto/UUID podem variar entre SGBDs.
- `ConsultasFinanceirasRepository`: UUID como String somente nos parâmetros JDBC SQL Server, retorno por `UUID.fromString`, datas por `getDate().toLocalDate()` e CASE equivalente a GREATEST. Valores do cliente continuam em parâmetros; concatenação limita-se a filtros SQL fixos.
- Agregados usados em somas/subtrações recebem DECIMAL(28,2) antes da aritmética. Evita redução de escala causada pelo SUM de precisão 38 no T-SQL. Valores persistidos continuam DECIMAL(15,2), cálculos Java continuam BigDecimal. Os agregados têm limite explícito de 26 dígitos inteiros, muito superior aos valores individuais; overflow deve gerar erro, nunca aproximação por ponto flutuante. [Regras de precisão Microsoft](https://learn.microsoft.com/sql/t-sql/data-types/precision-scale-and-length-transact-sql).
- CTEs de parcelas e pagamentos continuam agregadas separadamente. Não se adiciona pagamento de fatura à tabela de transações de consumo.
- `DatabaseHealthRepository`: DB_NAME/SCHEMA_NAME/SUSER_SNAME no SQL Server, consulta anterior no PostgreSQL; mesmo contrato REST. Erros reais são registrados pelo backend, mantendo resposta pública 503 existente.
- Scripts administrativos, consultas para demonstração, executor PowerShell com relatório por cenário e guia `SETUP_SQLSERVER_WINDOWS.md`.

A migração PostgreSQL `db/migration/V1.sql`, os testes PostgreSQL e os arquivos do frontend não foram alterados. Nenhuma conexão de teste ou comando de escrita foi executado no PostgreSQL original. A referência da implementação anterior continua no histórico Git; a seleção do SGBD acontece pelo profile no mesmo código mantido.

## Revisão de consultas e concorrência

JOIN/LEFT JOIN, COALESCE, CASE, GROUP BY e CTEs utilizados são expressáveis em T-SQL 2019. Não há paginação SQL manual ou concatenação de valores do cliente: a paginação continua via Spring Data/Hibernate e dialect SQLServerDialect. Os períodos são calculados em Java e enviados como DATE, com fim exclusivo onde já existia. Não há conversão das datas financeiras para UTC.

`UsuarioRepository.bloquear` mantém PESSIMISTIC_WRITE em consulta JPQL. O dialect deve traduzi-lo em locks SQL Server; isso **precisa ser comprovado na instância real**, não inferido do sucesso dos testes PostgreSQL. As escritas financeiras obtêm primeiro o lock do usuário e o mantêm até commit/rollback. Em READ COMMITTED, essa ordem pretende impedir leitura de dívida/limite desatualizado entre gravações do mesmo proprietário. Não configurar SNAPSHOT, NOLOCK ou READ UNCOMMITTED para contornar contenção.

As constraints UNIQUE de cartão/mês/ano e transação/número continuam sendo a última proteção contra duplicidade. FKs não ganharam cascatas, evitando caminhos múltiplos de exclusão no SQL Server. Operações SQL externas que não respeitem o bloqueio da aplicação não estão protegidas pelas validações financeiras de serviço; devem respeitar o mesmo protocolo.

Os testes paralelos iniciam duas threads com transações independentes e verificam limite, pagamento e fatura única. O teste de rollback força erro após flush de compra/faturas/parcelas e consulta as três tabelas depois da transação. O teste de constraints faz inserções SQL diretas e exige códigos reais 547/515/2601/2627, em vez de aceitar qualquer falha de conexão como rejeição válida.

## Comparação resumida

| Aspecto | PostgreSQL preservado | SQL Server |
|---|---|---|
| Seleção | padrão, sem profile sqlserver | profile sqlserver |
| Autenticação | configuração anterior | identidade Windows do Java, DLL oficial |
| Schema | public | dbo |
| Identificador | UUID/gen_random_uuid | UNIQUEIDENTIFIER/NEWID |
| Booleano | BOOLEAN | BIT |
| Data financeira | DATE/LocalDate | DATE/LocalDate |
| Criação do usuário | TIMESTAMP | DATETIME2(6)/SYSDATETIME |
| Moeda persistida | NUMERIC(15,2) | DECIMAL(15,2) |
| Máximo entre dívida e zero | GREATEST na versão anterior | CASE portável no código compartilhado |
| Lock | PESSIMISTIC_WRITE via dialect PostgreSQL | mesmo contrato JPA via SQLServerDialect, validação real pendente |
| Tabelas | script V1 original | Flyway com V1 em diretório independente |

## Validação executada

Executado Maven `test` com JDK 21 e dependências reais do projeto: **41 testes locais aprovados**, zero falhas/erros. Esses testes não usam SQL Server: 22 cálculos financeiros, nove serviços de transação, seis testes de controller, um teste HTTP de health e três testes de configuração da autenticação. O health de banco nos testes locais usa mock e não comprova conectividade.

Executado `npm run build`: **aprovado**, bundle Angular produzido. Não comprova o fluxo completo com SQL Server. Sintaxe do executor PowerShell verificada pelo parser local. Revisão estrutural comparou as 12 tabelas, PKs, FKs, índices e colunas monetárias entre os dois scripts; isso não substitui executar o DDL.

Na última execução Maven: 120 casos identificados, 41 aprovados e 79 ignorados (43 SQL Server e 36 PostgreSQL), zero falhas/erros. As suites SQL Server estão compiladas, mas ignoradas por ausência de habilitação/instância. As suites PostgreSQL também foram ignoradas para proteger o banco original. Relatórios locais em `backend/target/surefire-reports`; a contagem de casos parametrizados pode aumentar quando a integração for habilitada.

O build inicialmente encontrou restrições de acesso ao cache Maven e aos diretórios do Angular; ambos foram repetidos com autorização e passaram. Uma tentativa de `clean` não conseguiu remover o JAR aberto por outro processo; não encerramos esse processo, e a execução posterior de `test` passou. Não foi feito `package` para sobrescrever o JAR em uso; após parar esse processo, gere o novo artefato com `mvnw.cmd package` se optar por execução via JAR. Os comandos `spring-boot:run` usam as classes atuais.

## Testes de aceite pendentes

| Teste | Esperado | Obtido nesta máquina | Situação |
|---|---|---|---|
| 1 — Conectividade | health informa banco, dbo e identidade Windows | Sem SQL Server disponível | PENDENTE |
| Schema versionado | 12 tabelas + histórico Flyway, validação JPA passa | DDL revisado, não executado | PENDENTE |
| 2 — CRUD | criar, consultar, atualizar, excluir/desativar conforme contrato; conferir SQL | Suíte compilada | PENDENTE |
| 3 — Saldo | 1000 + 500 - 150 = 1350,00 | Suíte compilada | PENDENTE |
| 4 — Parcelamento | 33,34 + 33,33 + 33,33 = 100,00 | Regra unitária passa; integração não executada | PENDENTE |
| 5 — Transferências | origem 800,00, destino 200,00; sem receita/despesa extra | Suíte compilada | PENDENTE |
| 6 — Faturas | parcial/total reduz dívida/conta; sem despesa adicional | Suíte compilada | PENDENTE |
| 7 — Integridade | categoria/proprietário/valor/limite/pagamento/duplicidade rejeitados | Suíte compilada | PENDENTE |
| 8 — Concorrência | pagamentos 80+80 sobre dívida 100: um sucesso; compras respeitam limite; fatura única | Suíte paralela compilada | PENDENTE |
| 8 — Rollback | erro após flush deixa zero compras/faturas/parcelas novas | Suíte compilada | PENDENTE |
| Centavos agregados | dívida 100,01 e saldo 1000,00 após movimentos de centavos | Suíte compilada | PENDENTE |
| Unicode | nome com 100 caracteres acentuados preservado | Suíte compilada | PENDENTE |
| Angular com SQL Server | fluxos existentes funcionam com endpoints preservados | Build aprovado; execução integrada pendente | PENDENTE |
| Outra máquina | seguir somente o guia e repetir testes | Guia escrito; ainda sem instalação independente | PENDENTE |

Após provisionar SQL Server, seguir `SETUP_SQLSERVER_WINDOWS.md` e executar `backend/scripts/Test-SqlServer.ps1`. Ele registra cenário, expectativa das asserções, resultado e aprovação, além de guardar log completo e XML de falhas com os valores esperados/obtidos. Só atualizar a situação acima com essas evidências reais. A tarefa não atende ainda ao aceite final de demonstração integrada.

## Script unico de instalacao

Os dois SQL auxiliares foram substituidos por `database/sqlserver/criar_banco_completo.sql`, que cria banco e 12 tabelas em uma execucao. A primeira inicializacao deve registrar baseline Flyway 1 conforme o guia atualizado. V1 do backend preservado; execucao real do script ainda pendente.

## Simplificacao solicitada do SQL

O script completo agora segue diretamente o exemplo PostgreSQL do usuario: CREATE DATABASE/GO/USE e as mesmas declaracoes CREATE TABLE/CREATE INDEX, substituindo apenas UUID, gen_random_uuid(), TIMESTAMP, BOOLEAN e TRUE pelos equivalentes SQL Server. Os tamanhos VARCHAR originais foram restaurados; a collation e a pagina de codigos sao as padroes da instancia, sem COLLATE explicito neste script. Isso substitui a descricao anterior de collation UTF-8 e capacidade ampliada somente para o script manual; V1 Flyway permanece inalterado. A equivalencia Unicode e a comparacao de textos precisam ser reavaliadas na instancia real. Script destinado a banco novo, ainda nao executado em SQL Server.
