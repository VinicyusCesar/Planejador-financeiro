# Executar com SQL Server e identidade Windows

Estado desta entrega: código e testes preparados; integração SQL Server pendente porque não há instância disponível nesta máquina. Consulte `docs/relatorios/migracao-sqlserver.md`. O PostgreSQL original não deve ser usado para estes passos.

## 1. Pré-requisitos

- Windows com SQL Server 2019 ou posterior (Developer para uso acadêmico ou Express). LocalDB não é o alvo deste guia: use uma instância com TCP/IP.
- JDK 21, com arquitetura conhecida (`java -XshowSettings:properties -version`, propriedade `os.arch`).
- Maven: wrapper `backend/mvnw.cmd` ou Maven instalado compatível com o projeto; Spring Boot existente 4.1.2.
- Node.js compatível com Angular 21 (por exemplo Node 22.12+ da série 22 ou 24) e npm. Instalar as dependências com `npm ci`.
- VS Code, Extension Pack for Java e extensão oficial SQL Server (mssql) da Microsoft.
- Driver Microsoft JDBC resolvido pelo Maven: **13.4.0.jre11**, versão gerenciada pelo BOM Spring Boot 4.1.2 deste projeto, compatível com JDK 21. Flyway SQL Server 12.4.0, também gerenciado pelo BOM. Se atualizar o BOM, confira novamente driver e DLL.

## 2. Identificar servidor, instância e porta

No PowerShell, `Get-Service -Name '*SQL*'` identifica serviços como `MSSQLSERVER` (instância padrão) e `MSSQL$SQLEXPRESS` (nomeada). No SQL Server Configuration Manager, abra SQL Server Network Configuration → Protocols for sua instância → TCP/IP. Habilite TCP/IP, confira IP Addresses → IPAll → TCP Port/TCP Dynamic Ports e reinicie o serviço se alterar a configuração.

**Não presuma 1433.** Consulte também o ERRORLOG, procurando “Server is listening on”, ou, em uma conexão TCP já aberta, execute:

```sql
SELECT @@SERVERNAME servidor, SERVERPROPERTY('InstanceName') instancia,
       CONNECTIONPROPERTY('local_net_address') endereco,
       CONNECTIONPROPERTY('local_tcp_port') porta;
```

Uma conexão Shared Memory pode retornar porta nula. Prefira `tcp:servidor,PORTA` no VS Code. Confirme com `Test-NetConnection servidor -Port PORTA`. Em acesso remoto, permita a porta TCP real no firewall. Instâncias nomeadas podem usar SQL Server Browser/UDP 1434 para descoberta; porta explícita evita essa dependência. Não é necessário habilitar Mixed Mode.

## 3. Conceder acesso à identidade Windows

Execute `whoami` no mesmo terminal que iniciará Java. Essa é a identidade usada pelo backend; serviços Windows usam a identidade configurada no serviço, que pode ser diferente da sua sessão interativa.

No VS Code, conecte com **Windows Authentication**, usando a mesma identidade Windows que executara o backend e com permissao para criar bancos. Execute o arquivo inteiro `database/sqlserver/criar_banco_completo.sql`. Ele cria o banco novo, todas as 12 tabelas e os indices. A identidade que cria o banco e sua proprietaria. O script nao solicita senha. Execute somente quando PlanejadorFinanceiro ainda nao existir; ele nao inclui comandos de exclusao.

Para testes, use uma copia do script substituindo todas as ocorrencias de `PlanejadorFinanceiro` por `PlanejadorFinanceiroTest`. Registre o baseline conforme o passo 5 antes de executar os testes.

## 4. DLL oficial e TLS

Baixe o pacote **13.4.0** na [página oficial Microsoft JDBC](https://learn.microsoft.com/sql/connect/jdbc/download-microsoft-jdbc-driver-for-sql-server). Extraia a DLL `mssql-jdbc_auth-13.4.0.x64.dll` para JVM x64 (ou o binário oficial correspondente à arquitetura da JVM). Não renomeie DLLs de outra versão. O JAR Maven não instala automaticamente essa biblioteca nativa.

Disponibilize a pasta, sem copiar a DLL ao repositório:

```powershell
$env:PATH = 'C:\Ferramentas\mssql-jdbc-13.4.0\auth\x64;' + $env:PATH
```

Alternativa ao PATH ao executar o JAR:

```powershell
java '-Djava.library.path=C:\Ferramentas\mssql-jdbc-13.4.0\auth\x64' -jar target\backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=sqlserver
```

O profile exige `integratedSecurity=true;authenticationScheme=NativeAuthentication` e bloqueia credenciais alternativas no datasource. Não configure `spring.datasource.username`, `spring.datasource.password`, usuários SQL, senhas SQL ou tokens. Não existe formulário Angular para credenciais de banco.

TLS fica ligado e **valida o certificado por padrão**. Configure no SQL Server um certificado válido para o nome DNS usado, com cadeia confiável pela JVM. Importe a CA no truststore da JVM ou use um truststore de implantação; não desabilite TLS. Somente para desenvolvimento local com certificado autoassinado, configure `SQLSERVER_TRUST_SERVER_CERTIFICATE=true`. Isso desativa a validação do certificado e não deve ser transportado para outros ambientes.

Referências: [autenticação nativa e URL](https://learn.microsoft.com/sql/connect/jdbc/building-the-connection-url), [propriedades TLS](https://learn.microsoft.com/sql/connect/jdbc/setting-the-connection-properties).

## 5. Criar tabelas versionadas e iniciar backend

Após criar o banco e conceder permissões, abra um terminal na pasta `backend`:

Se já houver um backend PostgreSQL em execução na porta 8080, encerre esse processo antes de iniciar a versão SQL Server nessa porta. Isso não exige alterar nem parar o banco PostgreSQL. O JAR antigo pode estar aberto por esse processo; pare-o também antes de gerar outro JAR com `mvnw.cmd package`.

```powershell
$env:SQLSERVER_ENDPOINT = 'localhost:PORTA_REAL'
$env:SQLSERVER_DATABASE = 'PlanejadorFinanceiro'
# Apenas se necessario no desenvolvimento local:
$env:SQLSERVER_TRUST_SERVER_CERTIFICATE = 'true'
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=sqlserver'
```

Substitua `PORTA_REAL` pelo número verificado. Para descoberta por nome, o endpoint pode ser `localhost;instanceName=SQLEXPRESS`. A variável contém apenas endpoint; segurança integrada e TLS são definidos pelo profile. Não ative `postgres-test` junto com `sqlserver`. Variáveis antigas `DB_USERNAME`/`DB_PASSWORD` não são lidas pelo profile SQL Server.

Como as tabelas ja foram criadas pelo script completo, na **primeira inicializacao** utilize:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.profiles=sqlserver' '-Dspring-boot.run.arguments=--spring.flyway.baseline-on-migrate=true --spring.flyway.baseline-version=1'
```

Isso registra a versao 1 ja instalada no Flyway. Nas proximas vezes, use o comando normal acima. Nao execute tambem o V1 manualmente: ele permanece no backend para bancos vazios. O Hibernate continua apenas validando o schema.

Para registrar o baseline no banco de testes, selecione `SQLSERVER_DATABASE=PlanejadorFinanceiroTest` e execute a mesma primeira inicializacao; pare o backend antes de executar os testes. Nao registre baseline se a criacao das tabelas falhou.

Se o wrapper falhar ao localizar a distribuição Maven, use `mvn` instalado nos mesmos comandos; o projeto não exige alterações no wrapper para mudar de SGBD.

## 6. Iniciar Angular e verificar

Em outro terminal, na pasta `frontend`:

```powershell
npm ci
npm start
```

Acesse `http://localhost:4200`. O proxy e todos os endpoints foram mantidos. Verifique:

```powershell
Invoke-RestMethod http://localhost:8080/api/health/database
```

Esperado: `success=true`, `database=PlanejadorFinanceiro`, `schema=dbo` e `user=DOMINIO\Usuario` da sessão Java. Compare com `whoami` e com `SELECT DB_NAME(), SCHEMA_NAME(), SUSER_SNAME(), ORIGINAL_LOGIN()` na mesma identidade. O contrato JSON permanece `success/database/schema/user`.

## 7. Executar testes reais

Primeiro crie `PlanejadorFinanceiroTest` e suas permissões pelo passo 3. O Flyway criará o schema quando o contexto de testes iniciar. Na raiz do projeto:

```powershell
.\backend\scripts\Test-SqlServer.ps1 -Endpoint 'localhost:PORTA_REAL' -AuthDllDirectory 'C:\Ferramentas\mssql-jdbc-13.4.0\auth\x64' -TrustLocalCertificate
```

Omita `-TrustLocalCertificate` com certificado confiável. Se precisar de Maven instalado, acrescente `-MavenCommand mvn`. O script restaura as variáveis ao terminar e não altera o PostgreSQL.

Alternativa manual, na pasta `backend`, com PATH da DLL configurado:

```powershell
$env:SQLSERVER_ENDPOINT = 'localhost:PORTA_REAL'
$env:SQLSERVER_DATABASE = 'PlanejadorFinanceiroTest'
$env:SQLSERVER_TEST_ENABLED = 'true'
.\mvnw.cmd '-Dtest=*SqlServerTests,SqlServerIntegrationTests' test
```

A suíte recusa outro nome de banco. Sem habilitação explícita, os testes SQL Server são ignorados, nunca aprovados. Asserções comparam resultados reais de endpoints/serviços e SQL direto, incluindo saldo 1350, parcelas 33,34/33,33/33,33, transferência 200, faturas, permissões, constraints, concorrência e rollback após flush em várias tabelas. `MockMvc` executa os controllers reais sobre SQL Server; não usa banco simulado.

Resultados: `backend/target/surefire-reports` (XML e TXT, valores esperados/obtidos nas falhas), `target/sqlserver-maven.log` e `target/RESULTADOS_SQLSERVER.md` (gerado pelo script com cada cenário). O quadro de expectativas está em `docs/relatorios/migracao-sqlserver.md`. Faça também a demonstração Angular sobre o backend SQL Server antes de considerar o aceite concluído.

Os comandos REST para demonstracao permanecem em `docs/api-backend.md`.

## 8. Diagnóstico sem trocar autenticação

| Erro real | Verificação e correção |
|---|---|
| `This driver is not configured for integrated authentication` / `UnsatisfiedLinkError` | DLL oficial da mesma versão, arquitetura da JVM, PATH herdado pelo Java ou `java.library.path`; reiniciar o terminal após mudar PATH |
| `Can't load AMD 64-bit .dll on a IA 32-bit platform` | Corrigir combinação JVM/DLL; arquitetura do Windows sozinho não basta |
| `Login failed for user DOMINIO\Usuario` (18456) | Comparar `whoami`, identidade do processo/serviço, login FROM WINDOWS e usuário no banco; consultar estado do erro no log SQL Server |
| `Cannot open database ... requested by the login` (4060) | Banco existente na instância correta, usuário mapeado e acesso concedido |
| Conexão recusada/timeout | Serviço iniciado, TCP/IP habilitado, porta real, reinício, firewall e teste TCP; instância nomeada/SQL Browser se aplicável |
| `PKIX path building failed` / nome do certificado | CA confiável na JVM e DNS correspondente ao certificado; exceção de confiança apenas local |
| Schema/tabela inválida | Banco correto, schema padrão dbo e V1 SQL Server aplicado; não executar a migração PostgreSQL |
| `permission denied` / falha Flyway | Permissões DDL para identidade Windows de migração; permissões DML e definição para backend |
| Deadlock 1205 / lock timeout | Inspecionar sessões e ordem de locks; registrar falha e repetir testes, sem remover o bloqueio ou ignorar erro |

Falhas de inicialização são exibidas pelo driver/Flyway nos logs. O health mantém a resposta 503 original, e o backend registra a exceção real no log. Não substitua NativeAuthentication por SQL Authentication para contornar erros.
