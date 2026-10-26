# Backend do Planejador Financeiro

Aplicação acadêmica em Java 21 e Spring Boot 4.1.2. Nesta etapa, disponibiliza apenas verificações da aplicação e da conexão com o PostgreSQL.

## Requisitos e dependências

- JDK 21 (compilação verificada com Java 21.0.12.1).
- PostgreSQL local; versão prevista no projeto: 18.6. O banco `planejador_financeiro` e suas 12 tabelas devem existir no schema `public`.
- Maven Wrapper incluído (`mvnw.cmd` no Windows, Maven 3.10.0). Não é necessário instalar Maven globalmente. A primeira execução precisa de acesso à internet para baixar dependências.
- VS Code com suporte a Java 21; Extension Pack for Java e Spring Boot Extension Pack são opcionais para facilitar a edição e a execução.

| Dependência | Responsabilidade |
| --- | --- |
| Spring Web MVC | Servidor HTTP e endpoints REST com respostas JSON. |
| Spring Data JPA / Hibernate | Infraestrutura de persistência para as próximas etapas e configuração de validação dos mapeamentos. Inclui JDBC, `JdbcTemplate` e HikariCP transitivamente. |
| PostgreSQL Driver | Comunicação JDBC com PostgreSQL. |
| Spring Validation | Suporte à validação de entradas nas próximas etapas. |
| Spring Boot DevTools | Ferramentas de desenvolvimento local; dependência opcional em runtime. |
| Spring Boot Starter Test e Web MVC Test | JUnit, Mockito, AssertJ e testes HTTP/MVC. |
| Flyway e suporte PostgreSQL | Dependências preexistentes mantidas, com execução desabilitada. |

As versões das dependências são gerenciadas pelo parent Spring Boot 4.1.2. O projeto usa o starter `spring-boot-starter-webmvc`, compatível com essa versão.

## Variáveis de ambiente

| Variável | Valor padrão | Uso |
| --- | --- | --- |
| `DB_PASSWORD` | Sem padrão; obrigatória na execução normal | Senha do usuário PostgreSQL. |
| `DB_USERNAME` | `postgres` | Usuário de desenvolvimento. |
| `DB_HOST` | `127.0.0.1` | Host do PostgreSQL. |
| `DB_PORT` | `5432` | Porta do PostgreSQL. |

O banco é fixado em `planejador_financeiro` e o schema em `public`. Nenhuma senha deve ser escrita em arquivos versionados. Arquivos `.env` são ignorados pelo Git, mas **não são carregados automaticamente** pela aplicação.

No terminal PowerShell integrado do VS Code, entre na pasta `backend` e informe a senha sem incluí-la no histórico de comandos:

```powershell
cd backend
$taskDbPassword = Read-Host 'Senha do PostgreSQL para o usuário postgres' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $taskDbPassword).Password
Remove-Variable taskDbPassword
```

Se necessário, defina os demais parâmetros na mesma sessão:

```powershell
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '5432'
$env:DB_USERNAME = 'postgres'
```

## Compilar, testar e iniciar pelo VS Code

1. Abra a pasta do repositório no VS Code e utilize JDK 21 para o projeto Maven em `backend/pom.xml`.
2. Abra **Terminal → Novo Terminal** e configure as variáveis conforme indicado acima.
3. Dentro de `backend`, execute:

```powershell
java -version
.\mvnw.cmd --version
.\mvnw.cmd -B verify
.\mvnw.cmd spring-boot:run
```

A aplicação atende em `http://localhost:8080`. A classe principal é `com.planejador.BackendApplication`. Interrompa com `Ctrl+C`. Para remover a senha da sessão ao terminar:

```powershell
Remove-Item Env:DB_PASSWORD
```

Para executar o JAR gerado pelo `verify`, use na mesma sessão configurada:

```powershell
java -jar .\target\backend-0.0.1-SNAPSHOT.jar
```

Os testes automatizados não precisam de PostgreSQL nem de `DB_PASSWORD`. O teste de inicialização desativa a autoconfiguração do DataSource somente no teste e substitui o repositório por um mock. Os testes MVC usam o serviço real e um repositório simulado. Isso verifica o contrato HTTP, sem comprovar uma conexão real.

## Configuração e preservação do banco

O arquivo `src/main/resources/application.yaml` mantém:

- `spring.jpa.hibernate.ddl-auto: validate`;
- `spring.jpa.properties.hibernate.default_schema: public` e `currentSchema=public` na URL JDBC;
- `spring.flyway.enabled: false`;
- `spring.sql.init.mode: never`;
- `spring.jpa.open-in-view: false`;
- timeout de conexão do pool de 5 segundos e timeouts JDBC de conexão/leitura de 5 segundos.

O backend não executa `V1.sql`, não cria tabelas e não altera os dados existentes. O arquivo SQL permanece como referência do schema manual. Como ainda não existem entidades JPA, `validate` não verifica a definição das 12 tabelas; passará a validar os mapeamentos quando forem implementados.

A execução normal precisa de credenciais válidas e de PostgreSQL disponível para inicializar o Hibernate. O endpoint da aplicação não consulta o banco a cada requisição, mas só fica acessível depois de a aplicação iniciar. Se o banco falhar após a inicialização, a verificação do banco responde com HTTP 503.

## Testar os endpoints e a conexão

Com a aplicação iniciada, abra outro terminal:

```powershell
Invoke-RestMethod http://localhost:8080/api/health | ConvertTo-Json
Invoke-RestMethod http://localhost:8080/api/health/database | ConvertTo-Json
```

`GET /api/health` responde HTTP 200:

```json
{"success": true, "status": "UP"}
```

`GET /api/health/database` executa somente:

```sql
SELECT current_database() AS database_name,
       current_schema() AS schema_name,
       current_user AS user_name;
```

Exemplo de resposta esperada, após autenticação real, com HTTP 200:

```json
{"success": true, "database": "planejador_financeiro", "schema": "public", "user": "postgres"}
```

Em falhas de acesso ao banco, a resposta é HTTP 503, sem detalhes da exceção:

```json
{"success": false, "message": "Não foi possível conectar ao banco de dados."}
```

Para conferir o servidor e realizar a mesma consulta diretamente, se o diretório `bin` do PostgreSQL estiver no `PATH`:

```powershell
pg_isready -h 127.0.0.1 -p 5432 -d planejador_financeiro
psql -h 127.0.0.1 -p 5432 -U postgres -d planejador_financeiro -W -c 'SELECT current_database(), current_schema(), current_user;'
```

`pg_isready` confirma apenas que o servidor aceita conexões. A consulta autenticada ou o endpoint do banco é necessária para confirmar o acesso ao banco correto. Caso esses executáveis não estejam no `PATH`, use o caminho completo da instalação local.

## Organização e fluxo interno

```text
com.planejador
├── BackendApplication.java
├── config/CorsConfig.java
├── controller/HealthController.java
├── service/HealthService.java
├── repository/DatabaseHealthRepository.java
├── dto/HealthResponse.java
├── dto/DatabaseHealthResponse.java
├── dto/ErrorResponse.java
└── exception/HealthExceptionHandler.java
```

Fluxo de `GET /api/health/database`:

```text
Requisição HTTP → HealthController → HealthService
    → DatabaseHealthRepository → JdbcTemplate → DataSource/HikariCP
    → Driver JDBC → PostgreSQL → resultado da consulta
    → DTO → resposta JSON (HTTP 200)
```

O controller recebe a requisição e chama o serviço. O serviço encaminha a verificação ao repositório, onde fica o SQL. O `JdbcTemplate` obtém uma conexão do pool e converte a linha retornada em `DatabaseHealthResponse`. O Spring serializa esse record em JSON. Se ocorrer uma `DataAccessException`, `HealthExceptionHandler` devolve o erro genérico com HTTP 503.

O pacote `model` será criado quando houver entidades reais. Não há CRUDs, cálculos financeiros ou frontend nesta etapa. O CORS permite somente a origem `http://localhost:4200` e o método `GET` em `/api/**`, incluindo o preflight correspondente. Novos métodos poderão ser liberados quando forem implementados.

## Validação desta tarefa

Consulte [o relatório da Tarefa 01](../docs/relatorios/tarefa-01-backend-base.md) para os comandos executados, os resultados e a limitação de autenticação PostgreSQL encontrada neste ambiente.

Referências: [testes no Spring Boot](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html) e [JDBC e SQL no Spring Boot](https://docs.spring.io/spring-boot/reference/data/sql.html).
