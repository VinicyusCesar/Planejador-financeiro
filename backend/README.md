# Backend do Planejador Financeiro

API REST acadêmica em Java 21 e Spring Boot 4.1.2, com mapeamento das 12 tabelas PostgreSQL, cadastros, transações, parcelamento, faturas, pagamentos, transferências, orçamento, metas e dashboards.

## Requisitos e dependências

- JDK 21; validado com Java 21.0.12.1.
- PostgreSQL 18.6 local, com o banco `planejador_financeiro` e o schema `public` previamente criados.
- Maven Wrapper incluído, versão 3.10.0; não exige Maven global.
- VS Code com suporte a Java e Maven. A primeira compilação precisa de internet.

O parent Spring Boot gerencia as versões de Spring Web MVC, Spring Data JPA/Hibernate, Validation, driver PostgreSQL, DevTools e ferramentas JUnit/Mockito/MockMvc. JDBC e HikariCP chegam transitivamente pelo starter JPA. Flyway permanece instalado e desabilitado. Não foram adicionadas dependências Maven na Tarefa 02.

## Configuração PostgreSQL

| Variável | Padrão | Finalidade |
| --- | --- | --- |
| DB_PASSWORD | Obrigatória na execução normal, sem padrão | Senha PostgreSQL. |
| DB_USERNAME | postgres | Usuário do banco. |
| DB_HOST | 127.0.0.1 | Host local. |
| DB_PORT | 5432 | Porta PostgreSQL. |

O banco permanece `planejador_financeiro`. A URL define `currentSchema=public` e o Hibernate também usa o schema public. A configuração mantém `ddl-auto=validate`, `spring.flyway.enabled=false` e `spring.sql.init.mode=never`.

O backend não executa o schema automaticamente. `src/main/resources/db/migration/V1.sql` foi preservado. Com as 12 entidades implementadas, o Hibernate valida seus mapeamentos ao iniciar. A divergência contra o banco real não pôde ser investigada sem senha; a validação passou em PostgreSQL 18.6 isolado com cópia do SQL original.

No terminal PowerShell integrado do VS Code:

~~~powershell
cd backend
$taskDbPassword = Read-Host 'Senha do PostgreSQL' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $taskDbPassword).Password
Remove-Variable taskDbPassword
~~~

Se necessário:

~~~powershell
$env:DB_USERNAME = 'postgres'
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '5432'
~~~

Não grave senhas em arquivos versionados. Arquivos .env são ignorados pelo Git e não são carregados automaticamente.

## Compilar e executar pelo VS Code

Abra a raiz do repositório, selecione JDK 21 para o projeto Maven e use o terminal integrado, dentro de backend:

~~~powershell
java -version
.\mvnw.cmd --version
.\mvnw.cmd -B verify
.\mvnw.cmd spring-boot:run
~~~

Alternativamente, depois do verify:

~~~powershell
java -jar .\target\backend-0.0.1-SNAPSHOT.jar
~~~

A aplicação escuta em `http://127.0.0.1:8080`. CORS permite `http://localhost:4200` para GET, POST, PUT, PATCH e DELETE, com Content-Type e X-Usuario-Id. A classe principal continua `com.planejador.BackendApplication`.

Interrompa com Ctrl+C. Ao terminar, remova a senha da sessão:

~~~powershell
Remove-Item Env:DB_PASSWORD
~~~

## Testes sem PostgreSQL

~~~powershell
.\mvnw.cmd -B verify
~~~

Os testes unitários, MVC e o servidor isolado de health não precisam de banco ou senha. As duas classes de integração PostgreSQL são ignoradas quando TEST_DB_URL não aponta explicitamente para um banco local de teste permitido.

O health da aplicação não faz consulta a cada chamada, mas a inicialização normal do Hibernate depende de PostgreSQL e credenciais válidas.

## Testes com PostgreSQL isolado, sem Docker

O script cria um cluster temporário dentro de backend/target, escutando apenas em loopback em outra porta. Cria somente nesse cluster o banco planejador_financeiro_test, aplica uma cópia do V1.sql, executa verify e encerra os processos ao final. Nenhuma conexão de teste usa a porta 5432.

No Windows, com o caminho correto dos binários da instalação:

~~~powershell
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File .\scripts\Test-Postgres.ps1 -PostgresBin 'E:\postgres\18\bin' -SmokeHttp
~~~

RemoteSigned vale apenas para esse processo e permite executar o script local sem alterar a política global do Windows. Os parâmetros opcionais `-Port 55433` e `-HttpPort 18081` resolvem conflitos de portas; os padrões são 55432 e 18080. O script recusa a porta PostgreSQL 5432 ou uma porta já ocupada.

`-SmokeHttp` também inicia o JAR gerado e consulta os dois endpoints de saúde por HTTP com banco real de teste. A autenticação trust é exclusiva do cluster temporário em loopback; nenhuma senha de desenvolvimento é alterada. Artefatos e logs permanecem em target; não use Maven clean enquanto o cluster estiver em execução.

Para usar um banco de teste já preparado, exclusivamente isolado:

~~~powershell
$env:TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:55432/planejador_financeiro_test?currentSchema=public'
$env:TEST_DB_USERNAME = 'backend_test'
# Se o banco isolado exigir senha, defina TEST_DB_PASSWORD com leitura protegida.
.\mvnw.cmd -B verify
Remove-Item Env:TEST_DB_URL
Remove-Item Env:TEST_DB_USERNAME
~~~

Os testes aceitam apenas o host literal 127.0.0.1, porta diferente de 5432 e nome exato planejador_financeiro_test. O schema deve estar preparado e as 12 tabelas serão validadas por Hibernate. Testes comuns usam rollback; os testes de concorrência gravam fixtures somente no banco descartável.

## Testar a API

~~~powershell
Invoke-RestMethod http://127.0.0.1:8080/api/health
Invoke-RestMethod http://127.0.0.1:8080/api/health/database
~~~

A segunda rota retorna sucesso, banco, schema e usuário reais da sessão. Banco indisponível após inicialização gera HTTP 503 sem detalhes internos.

Cadastre ou selecione um usuário em `/api/usuarios`. Para os recursos financeiros, envie seu ID:

~~~powershell
$taskHeaders = @{ 'X-Usuario-Id' = 'UUID_DO_USUARIO' }
Invoke-RestMethod http://127.0.0.1:8080/api/contas -Headers $taskHeaders
Invoke-RestMethod 'http://127.0.0.1:8080/api/dashboard/resumo-mensal?ano=2026&mes=10' -Headers $taskHeaders
~~~

As rotas de recursos são `/api/usuarios`, `/api/contas`, `/api/categorias`, `/api/transacoes`, `/api/cartoes`, `/api/faturas`, `/api/transferencias`, `/api/orcamentos`, `/api/metas` e `/api/dashboard`.

Consulte o [contrato completo da API](../docs/api-backend.md) para todos os métodos, filtros e exemplos JSON.

## Funcionamento interno e regras

~~~text
Angular → Controller → Service → Repository → PostgreSQL
                      ↓
                DTO → resposta JSON
~~~

Controllers recebem DTOs e aplicam Bean Validation. Services conferem propriedade, regras financeiras e transações. Os repositories JPA persistem as 12 entidades; ConsultasFinanceirasRepository agrega os indicadores com SQL. As entidades nunca são serializadas diretamente.

- Saldo bancário inclui operações liquidadas, transferências e pagamentos de fatura; não inclui a compra no crédito.
- Competência usa data da compra e valor total, mesmo em crédito parcelado. Pagamentos e transferências internas não viram novas despesas.
- Parcelas distribuem centavos sem alterar o total e usam faturas consecutivas.
- Bloqueios pessimistas por usuário protegem limite, pagamentos e criação de faturas concorrentes.
- Categorias só são excluídas sem dependências. Contas/cartões são desativados; transações/transferências são canceladas.
- Pagamentos existentes bloqueiam alteração de compras e novas compras naquela fatura, inclusive após pagamento parcial.
- O calendário de cartões com faturas não pode ser alterado; saldos negativos são permitidos.
- Metas não têm aportes no schema: progresso permanece indisponível, sem valor fictício.
- X-Usuario-Id identifica o proprietário no ambiente local e não substitui autenticação. Não publique a aplicação sem desenvolver essa proteção.

Detalhes: [regras financeiras](../docs/regras-financeiras.md) e [consultas SQL](../docs/consultas-sql.md).

## Relatórios

- [Tarefa 01 — base do backend](../docs/relatorios/tarefa-01-backend-base.md).
- [Tarefa 02 — backend financeiro](../docs/relatorios/tarefa-02-backend-completo.md), com funcionalidades entregues, testes executados, fluxos e limitações.
