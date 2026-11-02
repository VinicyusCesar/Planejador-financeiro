param(
    [Parameter(Mandatory = $true)]
    [string] $PostgresBin,
    [ValidateRange(1024, 65535)]
    [int] $Port = 55432,
    [switch] $SmokeHttp,
    [ValidateRange(1024, 65535)]
    [int] $HttpPort = 18080
)

$ErrorActionPreference = 'Stop'
if ($Port -eq 5432) { throw 'A porta 5432 não pode ser usada pelos testes isolados.' }
if ($Port -eq $HttpPort) { throw 'As portas HTTP e PostgreSQL devem ser diferentes.' }
$taskBackendRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$taskTargetRoot = Join-Path $taskBackendRoot 'target'
$taskRunRoot = Join-Path $taskTargetRoot ('postgres-test-' + [Guid]::NewGuid().ToString('N'))
$taskDataPath = Join-Path $taskRunRoot 'data'
if (-not ([IO.Path]::GetFullPath($taskDataPath)).StartsWith(
        $taskTargetRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'O diretório do cluster deve permanecer dentro de backend/target.'
}
foreach ($taskExecutable in @('initdb.exe', 'pg_ctl.exe', 'createdb.exe', 'psql.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin $taskExecutable))) {
        throw "Executável PostgreSQL ausente: $taskExecutable"
    }
}
if (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue) {
    throw "A porta de teste $Port já está em uso. Escolha outra porta."
}
if ($SmokeHttp -and (Get-NetTCPConnection -State Listen -LocalPort $HttpPort -ErrorAction SilentlyContinue)) {
    throw "A porta HTTP de teste $HttpPort já está em uso."
}

New-Item -ItemType Directory -Path $taskRunRoot | Out-Null
$taskPreviousEnvironment = @{}
foreach ($taskKey in @('TEST_DB_URL', 'TEST_DB_USERNAME', 'TEST_DB_PASSWORD')) {
    $taskPreviousEnvironment[$taskKey] = [Environment]::GetEnvironmentVariable($taskKey, 'Process')
}
$taskApplication = $null
$taskMavenExit = 1
Push-Location $taskBackendRoot
try {
    & (Join-Path $PostgresBin 'initdb.exe') -D $taskDataPath -U backend_test --auth=trust --encoding=UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao inicializar o cluster isolado.' }

    $taskPgArguments = @('start', '-D', ('"' + $taskDataPath + '"'), '-l',
        ('"' + (Join-Path $taskRunRoot 'postgres.log') + '"'), '-o',
        ('"-h 127.0.0.1 -p ' + $Port + '"'), '-w', '-t', '20')
    $taskPgLauncher = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') `
        -ArgumentList $taskPgArguments -WindowStyle Hidden -PassThru
    # WaitForExit aguarda apenas pg_ctl; Start-Process -Wait aguardaria também o servidor filho.
    if (-not $taskPgLauncher.WaitForExit(25000)) { throw 'Tempo excedido ao iniciar o PostgreSQL isolado.' }
    if ($taskPgLauncher.ExitCode -ne 0) { throw 'Falha ao iniciar o PostgreSQL isolado.' }

    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $Port -U backend_test planejador_financeiro_test
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar o banco isolado.' }
    & (Join-Path $PostgresBin 'psql.exe') --no-password -v ON_ERROR_STOP=1 -h 127.0.0.1 -p $Port `
        -U backend_test -d planejador_financeiro_test -f .\src\main\resources\db\migration\V1.sql
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar as tabelas de teste.' }

    $env:TEST_DB_URL = "jdbc:postgresql://127.0.0.1:$Port/planejador_financeiro_test?currentSchema=public"
    $env:TEST_DB_USERNAME = 'backend_test'
    [Environment]::SetEnvironmentVariable('TEST_DB_PASSWORD', $null, 'Process')
    & .\mvnw.cmd -B verify
    $taskMavenExit = $LASTEXITCODE
    if ($taskMavenExit -ne 0) { throw "Maven encerrou com código $taskMavenExit." }

    if ($SmokeHttp) {
        $taskJavaArguments = @('-jar', '.\target\backend-0.0.1-SNAPSHOT.jar',
            ('--spring.datasource.url=' + $env:TEST_DB_URL), '--spring.datasource.username=backend_test',
            '--spring.datasource.password=', ('--server.port=' + $HttpPort), '--debug=false')
        $taskApplication = Start-Process -FilePath 'java' -ArgumentList $taskJavaArguments -WindowStyle Hidden `
            -RedirectStandardOutput (Join-Path $taskRunRoot 'backend-out.log') `
            -RedirectStandardError (Join-Path $taskRunRoot 'backend-err.log') -PassThru
        $taskReady = $false
        $taskDeadline = [DateTime]::UtcNow.AddSeconds(40)
        do {
            if ($taskApplication.HasExited) { throw 'O JAR encerrou antes de disponibilizar os endpoints.' }
            try {
                $taskHealth = Invoke-RestMethod "http://127.0.0.1:$HttpPort/api/health" -TimeoutSec 2
                $taskReady = $taskHealth.success -eq $true
            } catch {
                Start-Sleep -Milliseconds 500
            }
        } while (-not $taskReady -and [DateTime]::UtcNow -lt $taskDeadline)
        if (-not $taskReady) { throw 'A aplicação não iniciou no prazo esperado.' }
        $taskDatabaseHealth = Invoke-RestMethod "http://127.0.0.1:$HttpPort/api/health/database" -TimeoutSec 5
        if (-not $taskDatabaseHealth.success -or $taskDatabaseHealth.database -ne 'planejador_financeiro_test') {
            throw 'A verificação HTTP não confirmou o banco isolado.'
        }
        Write-Output ('HTTP /api/health: ' + ($taskHealth | ConvertTo-Json -Compress))
        Write-Output ('HTTP /api/health/database: ' + ($taskDatabaseHealth | ConvertTo-Json -Compress))
    }
    Write-Output "Testes concluídos. Artefatos locais: $taskRunRoot"
} finally {
    # Alguns launchers Oracle iniciam outra JVM. Encerre somente processos com
    # os argumentos exclusivos desta aplicação de teste, inclusive seus filhos.
    if ($SmokeHttp -and $null -ne $taskApplication) {
        Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
            $_.CommandLine -like '*backend-0.0.1-SNAPSHOT.jar*' -and
            $_.CommandLine -like ('*--server.port=' + $HttpPort + '*') -and
            $_.CommandLine -like ('*127.0.0.1:' + $Port + '/planejador_financeiro_test*')
        } | ForEach-Object { Stop-Process -Id $_.ProcessId -ErrorAction SilentlyContinue }
    }
    if ($null -ne $taskApplication -and -not $taskApplication.HasExited) {
        Stop-Process -Id $taskApplication.Id
        $taskApplication.WaitForExit()
    }
    if (Test-Path -LiteralPath (Join-Path $taskDataPath 'postmaster.pid')) {
        & (Join-Path $PostgresBin 'pg_ctl.exe') stop -D $taskDataPath -m fast -w -t 20
    }
    foreach ($taskKey in $taskPreviousEnvironment.Keys) {
        [Environment]::SetEnvironmentVariable($taskKey, $taskPreviousEnvironment[$taskKey], 'Process')
    }
    Pop-Location
}
Write-Output 'Cluster e aplicação de teste encerrados; ambiente restaurado.'
exit $taskMavenExit
