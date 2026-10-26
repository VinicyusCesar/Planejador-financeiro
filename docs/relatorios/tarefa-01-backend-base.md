# Tarefa 01 — Configuração inicial do Backend

Data: 26/10/2026, horário de São Paulo.

## 1. Resumo

O backend existente foi organizado em uma aplicação Spring Boot com camadas de controller, service e repository. A versão Spring Boot **4.1.2**, o Java **21**, a classe principal e o Maven Wrapper foram preservados.

Foram implementados `GET /api/health` e `GET /api/health/database`, DTOs de resposta, tratamento de falhas de banco com HTTP 503 e CORS restrito ao Angular local. A configuração mantém o banco `planejador_financeiro`, usa o schema `public`, recebe a senha por variável de ambiente e desabilita migrações e inicialização SQL automática.

**Resultado:** compilação, testes e empacotamento aprovados. Foram executados **7 testes, sem falhas, erros ou testes ignorados**. A inicialização com servidor HTTP funcionou no teste isolado. A conexão autenticada com o PostgreSQL e a inicialização normal permanecem pendentes porque `DB_PASSWORD` não está disponível neste ambiente. O servidor PostgreSQL está ativo, mas isso não comprova acesso ao banco.

Nenhuma funcionalidade financeira foi iniciada. Não houve execução de scripts de schema, escrita de dados, acesso ao banco `acervo_lu`, alteração de frontend, uso de Docker, commit ou push.

## 2. Arquivos

Os caminhos abaixo são relativos à raiz do repositório. “Modificado” descreve arquivos que já existiam no workspace antes desta tarefa, mesmo quando ainda não eram rastreados pelo Git.

### Criados

| Arquivo | Finalidade |
| --- | --- |
| `backend/README.md` | Requisitos, dependências, variáveis de ambiente, execução no VS Code, endpoints, fluxo interno e limites da validação. |
| `backend/src/main/java/com/planejador/config/CorsConfig.java` | Permitir a origem `http://localhost:4200` para requisições GET em `/api/**`. |
| `backend/src/main/java/com/planejador/controller/HealthController.java` | Disponibilizar os dois endpoints de verificação. |
| `backend/src/main/java/com/planejador/service/HealthService.java` | Encaminhar a verificação de banco ao repositório. |
| `backend/src/main/java/com/planejador/repository/DatabaseHealthRepository.java` | Centralizar a consulta de leitura usando `JdbcTemplate`. |
| `backend/src/main/java/com/planejador/dto/HealthResponse.java` | Representar o sucesso da aplicação em JSON. |
| `backend/src/main/java/com/planejador/dto/DatabaseHealthResponse.java` | Representar sucesso, banco, schema e usuário em JSON. |
| `backend/src/main/java/com/planejador/dto/ErrorResponse.java` | Representar a falha com uma mensagem genérica. |
| `backend/src/main/java/com/planejador/exception/HealthExceptionHandler.java` | Converter `DataAccessException` em HTTP 503 sem expor detalhes internos na resposta. |
| `backend/src/test/java/com/planejador/controller/HealthControllerTests.java` | Testar respostas, erro de banco, CORS e preflight sem PostgreSQL local. |
| `docs/relatorios/tarefa-01-backend-base.md` | Registrar implementação, evidências e pendências da tarefa. |

### Modificados

| Arquivo | Finalidade |
| --- | --- |
| `backend/pom.xml` | Preencher identificação, explicitar Starter Test e manter Web MVC Test; remover Lombok, processamento de anotações e starters de testes especializados sem uso. |
| `backend/src/main/resources/application.yaml` | Configurar host/porta/usuário por ambiente, schema público, timeouts, Flyway desabilitado, SQL init desabilitado e Open Session in View desabilitado; manter `ddl-auto=validate` e senha por ambiente. |
| `backend/src/test/java/com/planejador/BackendApplicationTests.java` | Adaptar o teste existente para iniciar servidor em porta aleatória e consultar `/api/health` por HTTP, sem depender de PostgreSQL. |
| `backend/.gitignore` | Ignorar arquivos locais de ambiente e logs. |

### Removidos e preservados

Nenhum arquivo foi removido. `BackendApplication.java`, o README da raiz, `.gitattributes`, os scripts do Maven Wrapper e sua configuração foram preservados.

`backend/src/main/resources/db/migration/V1.sql` foi lido integralmente antes das alterações e permaneceu intacto. Ele define as tabelas `usuario`, `conta`, `categoria`, `cartao_credito`, `transacao`, `fatura`, `parcela`, `pagamento_fatura`, `transferencia`, `orcamento`, `item_orcamento` e `meta_financeira`, além das restrições e índices.

SHA-256 antes e depois:

```text
2BC31F5245FD4C4529B0534D5A12E5C4980E44A04B2EF71D564BDEE99A626BD7
```

O Git também confirmou ausência de diferenças no SQL. Na inspeção inicial, apenas o README da raiz e esse SQL eram rastreados; os arquivos do Spring Initializr já existiam como arquivos não rastreados. Nenhum deles foi recriado do zero ou commitado.

Os arquivos em `backend/target/`, incluindo JAR, relatórios do Surefire e log da tentativa de inicialização, são artefatos locais ignorados pelo Git.

## 3. Dependências

| Dependência | Responsabilidade e decisão |
| --- | --- |
| `spring-boot-starter-webmvc` | Spring MVC, Tomcat e serialização JSON. Mantido, compatível com Spring Boot 4.1.2. |
| `spring-boot-starter-data-jpa` | Spring Data JPA e Hibernate para a persistência futura. Também disponibiliza JDBC e HikariCP transitivamente. |
| `org.postgresql:postgresql` | Driver JDBC PostgreSQL, com escopo runtime. |
| `spring-boot-starter-validation` | Infraestrutura de validação Jakarta para as próximas etapas. |
| `spring-boot-devtools` | Apoio ao desenvolvimento, com escopo runtime e dependência opcional. |
| `spring-boot-starter-test` | Ferramentas comuns de testes, incluindo JUnit, Mockito e AssertJ; escopo test. |
| `spring-boot-starter-webmvc-test` | Infraestrutura específica de testes MVC e `@WebMvcTest`; escopo test. |
| `spring-boot-starter-flyway` | Dependência já existente, mantida com execução desabilitada. |
| `org.flywaydb:flyway-database-postgresql` | Suporte Flyway ao PostgreSQL já existente; permanece inativo. |

Todas as versões são gerenciadas pelo parent Spring Boot 4.1.2. Não foi necessário alterar a versão do Spring Boot nem adicionar uma dependência JDBC separada.

Lombok não era usado por nenhuma classe e foi removido junto da configuração de processadores de anotações. Os starters `data-jpa-test`, `flyway-test` e `validation-test` foram substituídos pelo starter comum de testes, mantendo apenas o módulo MVC específico necessário. Nenhuma biblioteca de autenticação, banco em memória, microserviço ou dependência de infraestrutura adicional foi incluída.

Referências consultadas: [testes no Spring Boot](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html), [SQL e JDBC](https://docs.spring.io/spring-boot/reference/data/sql.html) e [Maven 3.10.0](https://maven.apache.org/docs/3.10.0/release-notes.html).

## 4. Integração PostgreSQL

O Spring Boot cria o DataSource/HikariCP a partir das propriedades de `application.yaml`. O driver PostgreSQL utiliza a URL:

```text
jdbc:postgresql://${DB_HOST:127.0.0.1}:${DB_PORT:5432}/planejador_financeiro?currentSchema=public&connectTimeout=5&socketTimeout=5
```

O usuário vem de `DB_USERNAME`, com padrão `postgres`; a senha vem exclusivamente de `DB_PASSWORD`, sem valor padrão. O host padrão é `127.0.0.1` e a porta padrão é `5432`. O banco é `planejador_financeiro` e o schema é `public`, tanto na conexão JDBC quanto na configuração do Hibernate. O pool aguarda até 5 segundos pela conexão, e o driver tem timeouts de conexão e leitura de 5 segundos.

As proteções contra execução automática de DDL/scripts são:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        default_schema: public
  flyway:
    enabled: false
  sql:
    init:
      mode: never
```

O endpoint do banco executa somente uma consulta SELECT às funções `current_database()`, `current_schema()` e `current_user`, sem consultar ou modificar registros financeiros. Em sucesso, devolve `success`, `database`, `schema` e `user`. Em falha de acesso, retorna HTTP 503 com mensagem genérica.

**Verificação real realizada:** `pg_isready` confirmou que o servidor está aceitando conexões. A tentativa `psql --no-password` falhou com `fe_sendauth: no password supplied`. Não havia `DB_PASSWORD`, `PGPASSWORD` nem arquivo padrão `pgpass.conf` disponível. A inicialização normal do JAR também falhou na autenticação, com SQLState `28P01`, impedindo o Hibernate de obter metadados JDBC.

Portanto, não foi possível confirmar a consulta autenticada, a existência efetiva das 12 tabelas no servidor ou o retorno real de `/api/health/database`. A versão PostgreSQL 18.6 é a versão prevista na especificação; não foi confirmada por uma consulta autenticada. As respostas de sucesso usadas nos testes são simulações explícitas.

Não foram implementadas entidades JPA nesta etapa. Assim, `validate` está configurado para os futuros mapeamentos e não representa, por si só, uma auditoria das 12 tabelas existentes.

## 5. Testes

### Comandos e resultados

Comandos Maven executados dentro de `backend`:

| Comando | Resultado |
| --- | --- |
| `java -version` | Java 21.0.12.1 disponível. |
| `mvn -version` | Maven global não está no PATH; foi utilizado o Wrapper existente. |
| `.\mvnw.cmd --version` | Maven 3.10.0 executando com Java 21.0.12.1. |
| `.\mvnw.cmd -B verify` — primeira execução | Código principal compilou; compilação dos testes falhou por import incorreto de `LocalServerPort`. Corrigido antes da execução final. |
| `.\mvnw.cmd -B verify` — execução final | **BUILD SUCCESS**; compilação, 7 testes aprovados e JAR executável gerado em `target/backend-0.0.1-SNAPSHOT.jar`. |
| `java -jar .\target\backend-0.0.1-SNAPSHOT.jar` | Processo encerrou com código 1: autenticação PostgreSQL indisponível; não houve inicialização normal concluída. |

Comandos adicionais executados a partir da raiz:

```powershell
& 'E:\postgres\18\bin\pg_isready.exe' -h 127.0.0.1 -p 5432 -d planejador_financeiro
& 'E:\postgres\18\bin\psql.exe' --no-password -h 127.0.0.1 -p 5432 -U postgres -d planejador_financeiro -c 'SELECT current_database(), current_schema(), current_user;'
Get-FileHash -LiteralPath 'backend/src/main/resources/db/migration/V1.sql' -Algorithm SHA256
git diff --exit-code -- backend/src/main/resources/db/migration/V1.sql
git diff --check
```

Resultados: servidor aceitando conexões; consulta recusada por ausência de senha; hash do SQL inalterado; nenhum diff no schema e nenhuma falha de whitespace nos arquivos rastreados. Como os arquivos de implementação ainda não são rastreados, `git diff --check` tem alcance limitado a arquivos rastreados; a implementação foi conferida também por leitura e compilação.

### Cobertura dos testes automatizados

`BackendApplicationTests`: **1 teste** inicia a aplicação Spring em porta aleatória e faz uma requisição HTTP real com o cliente HTTP do Java. Confirma HTTP 200, conteúdo JSON e status UP em `/api/health`. Somente nesse teste, a autoconfiguração do DataSource é excluída e o repositório é substituído por um mock. Isso não valida inicialização com Hibernate/PostgreSQL.

`HealthControllerTests`: **6 testes MVC** verificam:

1. Resposta de `/api/health` sem interação com o repositório.
2. JSON de sucesso do banco usando resultado simulado do repositório.
3. HTTP 503 em falha simulada, sem a mensagem interna da exceção no corpo.
4. Liberação da origem `http://localhost:4200`.
5. Preflight OPTIONS autorizando GET, sem consulta ao banco.
6. Rejeição de outra origem sem cabeçalho de liberação CORS.

Nesses testes MVC, controller, serviço, CORS e tratamento de exceções são reais; apenas o repositório é simulado. Os testes não usam PostgreSQL, banco em memória ou dados fictícios persistidos.

### Validações pendentes

- Inicialização normal bem-sucedida com credenciais válidas.
- Consulta JDBC real e HTTP 200 real de `/api/health/database`.
- Confirmação do banco, schema e usuário retornados pelo PostgreSQL.

O procedimento para concluir essas verificações está no [README do backend](../../backend/README.md). Não foram feitas mudanças na aplicação para contornar autenticação ou desativar a validação JPA na execução normal.

## 6. Dificuldades

- **Maven fora do PATH:** solucionado utilizando `mvnw.cmd`, que já estava no projeto. Nenhuma instalação global foi necessária.
- **Import de teste incompatível:** a primeira execução encontrou um import incorreto de `LocalServerPort`. A classe foi localizada no JAR instalado e o import corrigido para `org.springframework.boot.test.web.server.LocalServerPort`; a execução final passou.
- **Flyway inicialmente habilitado:** foi desabilitado antes de qualquer execução da aplicação, pois o schema já foi criado manualmente. Scripts SQL automáticos também foram desabilitados.
- **Credencial PostgreSQL ausente:** pendência de ambiente. O serviço está ativo, mas não foi possível autenticar. A falha secundária do Hibernate ao determinar o dialect decorre da ausência de metadados JDBC após a falha de autenticação; não foi mascarada com configuração de dialect ou alteração de `ddl-auto`.
- **Avisos não impeditivos:** o Maven emitiu avisos HTTP 401 ao consultar metadados de repositórios adicionais trazidos pela cadeia de dependências Flyway. A resolução necessária e o build foram concluídos. Mockito/Java também emitiram avisos sobre carregamento dinâmico de agente durante os testes; os 7 testes passaram no JDK 21.

Não há erro de compilação ou teste automatizado pendente. A única validação operacional pendente é a execução autenticada com o PostgreSQL local.

## 7. Funcionamento interno

```text
Cliente HTTP
    → Controller
    → Service
    → Repository / JdbcTemplate
    → DataSource / HikariCP / driver JDBC
    → PostgreSQL
    → resultado da consulta / DTO
    → resposta JSON
```

O Tomcat recebe a requisição. O Spring MVC aplica as regras CORS quando houver uma origem e localiza o método no `HealthController`.

Em `/api/health`, o controller retorna `HealthResponse(true, "UP")` diretamente. Nenhuma consulta ao banco é necessária para essa requisição.

Em `/api/health/database`, o controller chama `HealthService.checkDatabase()`. O serviço chama `DatabaseHealthRepository.checkConnection()`, que executa a consulta por `JdbcTemplate`. O template obtém uma conexão do DataSource, gerencia os recursos JDBC e mapeia a linha retornada para `DatabaseHealthResponse`. O Spring serializa esse record em JSON e responde HTTP 200.

Se o driver não conseguir executar a consulta, o Spring traduz a falha JDBC para `DataAccessException`. O `HealthExceptionHandler`, restrito ao controller de health, converte a exceção em `ErrorResponse(false, mensagem)` e HTTP 503. A mensagem técnica, a senha e o stack trace não são incluídos nessa resposta.

O tratamento HTTP só pode atuar depois de a aplicação iniciar. Como o Hibernate precisa acessar o banco na inicialização normal, credenciais ausentes ou inválidas podem impedir a subida completa, antes de os endpoints estarem disponíveis. A configuração mantém esse comportamento e não utiliza inicialização preguiçosa para ocultá-lo.

Foram criados somente os pacotes com classes necessárias. O pacote `model` será introduzido quando houver entidades. Não foram adicionadas interfaces artificiais, regras financeiras ou classes vazias para preencher a estrutura.

## 8. Próximos passos

Antes de validar a integração local, definir `DB_PASSWORD` no terminal conforme o README, iniciar o backend e consultar os dois endpoints. Confirmar no endpoint do banco os valores `planejador_financeiro`, `public` e o usuário configurado.

Para a Tarefa 02, estão preparados o projeto Maven/Java 21, o servidor HTTP, a configuração JPA/JDBC, as camadas, o CORS de desenvolvimento e a base de testes. Ainda precisam ser definidos e implementados os mapeamentos das entidades, os repositórios de domínio, as validações e as funcionalidades financeiras que forem autorizadas, respeitando o schema existente.

Critérios desta entrega:

- [x] Spring Boot organizado, preservando o projeto existente.
- [x] Dependências compatíveis configuradas.
- [x] Compilação e empacotamento concluídos.
- [x] `/api/health` funcional nos testes MVC e por HTTP com servidor real em teste isolado.
- [x] Configuração PostgreSQL preparada com senha por ambiente e schema `public`.
- [x] `/api/health/database` implementado e contrato HTTP testado.
- [x] Schema original preservado.
- [x] README com instruções de execução.
- [x] Relatório final criado.
- [ ] Inicialização normal e consulta real autenticada confirmadas: dependem de `DB_PASSWORD` válida.

**A Tarefa 02 não foi iniciada e depende de revisão e autorização do usuário.**
