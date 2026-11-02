# Guia para apresentar o Planejador Financeiro em outra máquina

## 1. Antes de começar: MSSQL e a compatibilidade do projeto

**O backend atual foi desenvolvido para PostgreSQL. Ele ainda não está adaptado ao Microsoft SQL Server (MSSQL).** Trocar apenas o usuário, a senha ou a URL de conexão não é suficiente.

Se a outra máquina usa SQL Server, existem duas possibilidades:

| Situação | O que fazer |
| --- | --- |
| Pode instalar PostgreSQL também | Seguir as seções 2 a 8. É o caminho para executar a versão atual do projeto. Não é necessário remover o SQL Server. |
| O professor exige que o banco utilizado seja MSSQL | Fazer a adaptação descrita na seção 9 antes da apresentação. O projeto atual não atende a essa exigência sem alterações. |

Este guia não realiza a migração para MSSQL. Não execute o arquivo PostgreSQL `V1.sql` no SQL Server Management Studio (SSMS).

## 2. Garantir que o código completo esteja no repositório

Na máquina de desenvolvimento, confira:

```powershell
git status --short
```

As implementações completas do backend e do frontend precisam estar salvas em commits e enviadas ao repositório remoto antes de clonar na outra máquina. Arquivos que existem apenas localmente não aparecem no clone. Na preparação deste guia, ainda havia arquivos novos do projeto fora dos commits.

Revise os arquivos antes de fazer commit e push. Não inclua senhas, arquivos `.env`, `node_modules`, `target`, `dist` nem pastas `.runtime`.

**O banco de dados e os cadastros não são transferidos pelo Git.** A seção 7 explica como levar dados já existentes.

## 3. Instalar os programas na outra máquina — Windows

Instale:

1. Git.
2. JDK 21, com `JAVA_HOME` e `PATH` configurados para esse JDK.
3. Node.js 24, incluindo npm.
4. PostgreSQL 18, preferencialmente a versão 18.6 usada na validação do projeto, com servidor e ferramentas de linha de comando.

O [instalador oficial indicado pelo PostgreSQL para Windows](https://www.postgresql.org/download/windows/) inclui o servidor e o pgAdmin. Durante a instalação, anote a senha definida para o usuário `postgres`. Use a porta `5432` se estiver disponível.

Não é necessário instalar Maven ou Angular CLI globalmente: o projeto fornece Maven Wrapper e instala o Angular pelo npm.

Abra um novo PowerShell e confira:

```powershell
git --version
java -version
node --version
npm.cmd --version
```

O Java deve indicar versão 21 e o Node versão 24. A primeira instalação das dependências requer internet; prepare a máquina antes da aula.

## 4. Clonar e preparar o banco PostgreSQL

Substitua os valores entre `<...>` pelos dados do seu repositório:

```powershell
git clone <URL_DO_REPOSITORIO>
cd <PASTA_DO_REPOSITORIO>
```

Os próximos comandos devem ser executados na raiz do projeto, onde estão as pastas `backend` e `frontend`.

Defina o caminho das ferramentas PostgreSQL. Ajuste se a instalação estiver em outra pasta:

```powershell
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
& "$pgBin\pg_isready.exe" -h 127.0.0.1 -p 5432
```

O resultado deve indicar que o servidor está aceitando conexões. Se não estiver, confira o serviço PostgreSQL no aplicativo Serviços do Windows.

### Primeira instalação, sem dados anteriores

Execute os comandos abaixo somente para criar um banco novo. Cada comando solicitará a senha do usuário `postgres`.

```powershell
& "$pgBin\createdb.exe" -h 127.0.0.1 -p 5432 -U postgres -W planejador_financeiro
& "$pgBin\psql.exe" -h 127.0.0.1 -p 5432 -U postgres -W -d planejador_financeiro -v ON_ERROR_STOP=1 -f .\backend\src\main\resources\db\migration\V1.sql
```

Se qualquer comando falhar, corrija o erro antes de continuar. Se o banco já existir, verifique seu conteúdo; não o apague para repetir a instalação.

O backend valida as tabelas existentes. A criação automática e o Flyway estão desativados, por isso o script precisa ser aplicado manualmente uma única vez nesse banco novo.

**Se vai restaurar um backup completo, use a seção 7 no lugar da execução do `V1.sql`.**

## 5. Iniciar o backend — primeiro terminal

Na raiz do projeto:

```powershell
cd backend
$env:DB_HOST = '127.0.0.1'
$env:DB_PORT = '5432'
$env:DB_USERNAME = 'postgres'
$taskDbPassword = Read-Host 'Senha do PostgreSQL' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $taskDbPassword).Password
Remove-Variable taskDbPassword
.\mvnw.cmd -B verify
```

Se a verificação terminar com `BUILD SUCCESS`, inicie:

```powershell
.\mvnw.cmd spring-boot:run
```

Mantenha esse terminal aberto. O backend deve iniciar na porta `8080`.

Caso tenha instalado o PostgreSQL em outra porta, altere `DB_PORT` para essa porta. A senha informada é a do PostgreSQL, não a do SQL Server.

## 6. Iniciar o frontend — segundo terminal

Abra outro PowerShell na raiz do projeto:

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

Mantenha esse terminal aberto e acesse:

**http://localhost:4200**

O frontend encaminha as chamadas `/api` para o backend em `127.0.0.1:8080`. Não é necessário configurar a senha do banco no frontend.

Em um terceiro terminal, confirme a conexão real do backend com o banco:

```powershell
Invoke-RestMethod -Uri 'http://127.0.0.1:8080/api/health/database' | ConvertTo-Json -Depth 5
```

Confira que a resposta indica sucesso e identifica o banco `planejador_financeiro`, com schema `public`.

## 7. Opcional: levar os cadastros para a apresentação

Se não levar um backup, o banco recém-criado começará sem os seus cadastros.

### Na máquina de origem

Identifique primeiro qual banco contém os dados que deseja apresentar. O ambiente normal usa `planejador_financeiro`, geralmente na porta `5432`. O ambiente isolado de testes usa `planejador_financeiro_test` e outra porta; seus dados são separados.

Para exportar o banco normal, use o caminho das ferramentas PostgreSQL da máquina de origem:

```powershell
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
& "$pgBin\pg_dump.exe" -h 127.0.0.1 -p 5432 -U postgres -W -d planejador_financeiro -Fc -f .\planejador-apresentacao.dump
```

Ajuste caminho, porta, usuário e nome do banco se seus cadastros estiverem em outra instância. Confira que a exportação terminou sem erros e copie o arquivo `.dump` separadamente para a outra máquina.

### Na máquina de destino

Restaure em um banco novo e vazio. **Não execute o `V1.sql` antes nem depois deste backup completo**, pois o backup já contém tabelas e dados.

Na raiz do projeto, com o arquivo `.dump` nessa pasta:

```powershell
$pgBin = 'C:\Program Files\PostgreSQL\18\bin'
& "$pgBin\createdb.exe" -h 127.0.0.1 -p 5432 -U postgres -W planejador_financeiro
& "$pgBin\pg_restore.exe" -h 127.0.0.1 -p 5432 -U postgres -W --no-owner --no-privileges -d planejador_financeiro .\planejador-apresentacao.dump
```

Se já criou o banco com tabelas, não execute essa sequência sobre ele. Prepare um destino vazio antes de restaurar, preservando os dados existentes. Use PostgreSQL 18 no destino para acompanhar o ambiente de origem validado.

Esse backup PostgreSQL não pode ser restaurado diretamente no SQL Server.

## 8. Conferência antes da aula e solução de problemas

Faça um ensaio completo na máquina da apresentação:

- Abra o frontend e selecione ou crie um perfil.
- Cadastre uma conta e categorias de receita e despesa.
- Registre uma receita e uma despesa e confira os saldos no dashboard.
- Se for apresentar cartão, teste uma compra parcelada e o pagamento de uma fatura.
- Reabra a página e confira a persistência dos dados.
- Mantenha o serviço PostgreSQL e os dois terminais da aplicação em execução.

| Problema | O que conferir |
| --- | --- |
| `java` não encontrado ou versão incorreta | JDK 21, `JAVA_HOME`, `PATH` e novo terminal após a instalação. |
| `npm.ps1` bloqueado | Use os comandos com `npm.cmd` mostrados neste guia. |
| `psql` ou `createdb` não encontrado | Caminho correto da pasta `bin` do PostgreSQL em `$pgBin`. |
| Conexão recusada pelo banco | Serviço PostgreSQL iniciado e porta igual a `DB_PORT`. |
| Falha de autenticação | Usuário e senha PostgreSQL corretos, definidos no terminal do backend. |
| Tabela ausente na inicialização | Script aplicado ao banco correto ou restauração concluída sem erros. |
| Porta `8080` ou `4200` ocupada | Identifique o programa que usa a porta; evite iniciar duas cópias da aplicação. |
| Frontend abre, mas os cadastros falham | Backend iniciado e endpoint de saúde do banco respondendo com sucesso. |
| Dados da sua máquina não aparecem | Clone não inclui banco; restaure o backup correto e selecione o perfil correspondente. |

Nas próximas execuções, não repita a criação do banco nem a restauração. Inicie o backend e o frontend; execute `npm.cmd ci` novamente quando as dependências do projeto forem atualizadas.

## 9. Se o uso de MSSQL for obrigatório

**Esta seção é um roteiro de desenvolvimento pendente. As etapas de instalação acima não tornam o backend compatível com SQL Server.**

Antes da adaptação, obtenha a versão do servidor SQL Server, nome da instância, endereço/porta, modo de autenticação e permissão para criar o banco. Ter apenas o SSMS instalado não substitui um servidor SQL Server em execução.

A adaptação deve contemplar:

1. **Driver e dependências:** ajustar `backend/pom.xml` para usar o driver JDBC Microsoft SQL Server e revisar as dependências Flyway específicas de PostgreSQL.
2. **Conexão:** configurar uma URL `jdbc:sqlserver:` e as credenciais em um perfil próprio; revisar também a configuração de criptografia e certificados conforme o servidor. A configuração atual aceita host e porta, mas fixa o protocolo PostgreSQL.
3. **Script de criação:** produzir um script separado em T-SQL com as 12 tabelas, chaves, índices, restrições e valores padrão. Preservar o `V1.sql` original PostgreSQL.
4. **Tipos e funções:** adaptar `UUID`/`gen_random_uuid()`, `BOOLEAN` e os valores lógicos para os equivalentes SQL Server. Para datas com horário, avaliar `datetime2`: no SQL Server, `timestamp` significa `rowversion`, não data/hora, conforme a [documentação Microsoft](https://learn.microsoft.com/en-us/sql/t-sql/data-types/rowversion-transact-sql?view=sql-server-ver17).
5. **Schema:** revisar `public` em `application.yaml` e nas 12 entidades em `backend/src/main/java/com/planejador/model`. Se o destino for `dbo`, todas essas referências precisam ficar coerentes.
6. **Consultas nativas:** adaptar a consulta de saúde em `DatabaseHealthRepository.java` e revisar todas as agregações de `ConsultasFinanceirasRepository.java`, incluindo a compatibilidade de funções como `GREATEST` com a versão escolhida do SQL Server.
7. **Testes e ferramentas:** criar um ambiente isolado SQL Server, ajustar os scripts de integração atualmente específicos de PostgreSQL e validar novamente as regras financeiras, restrições e operações concorrentes. Os testes anteriores com PostgreSQL não comprovam funcionamento no MSSQL.
8. **Validação ponta a ponta:** iniciar o frontend com o backend adaptado e testar contas, categorias, transações, parcelas, faturas, pagamentos, transferências, orçamentos, metas e dashboard. Confirmar que os dados persistem e os totais permanecem corretos.

Somente depois dessa adaptação e validação será possível fornecer comandos de instalação MSSQL que correspondam ao código executável do projeto. Atualmente, o caminho disponível para apresentar a versão existente é usar PostgreSQL, inclusive em uma máquina que também tenha SQL Server instalado.
