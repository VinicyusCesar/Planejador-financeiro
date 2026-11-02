param(
    [string] $PostgresBin = 'E:\postgres\18\bin',
    [ValidateRange(1024, 65535)] [int] $PostgresPort = 55434,
    [ValidateRange(1024, 65535)] [int] $BackendPort = 18082,
    [switch] $Stop
)

$ErrorActionPreference = 'Stop'
$taskFrontend = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$taskRuntime = Join-Path $taskFrontend '.runtime'
$taskManifestPath = Join-Path $taskRuntime 'stack.json'

function Stop-TestStack($taskStack) {
    $taskResolvedData = [IO.Path]::GetFullPath($taskStack.data)
    if (-not $taskResolvedData.StartsWith($taskRuntime + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'O cluster de teste deve estar dentro de frontend/.runtime.'
    }
    if ([int]$taskStack.postgresPort -eq 5432 -or [int]$taskStack.backendPort -eq 8080) {
        throw 'As portas de desenvolvimento não podem ser usadas pelo teste.'
    }
    $taskPortPattern = '(?:^|\s)--server\.port=' + $taskStack.backendPort + '(?:\s|$)'
    Get-CimInstance Win32_Process -Filter "Name = 'java.exe'" | Where-Object {
        $_.CommandLine -like '*backend-0.0.1-SNAPSHOT.jar*' -and
        $_.CommandLine -match $taskPortPattern -and
        $_.CommandLine.Contains('--spring.datasource.url=' + $taskStack.jdbc)
    } | ForEach-Object { Stop-Process -Id $_.ProcessId -ErrorAction SilentlyContinue }
    if (Test-Path -LiteralPath (Join-Path $taskResolvedData 'postmaster.pid')) {
        & (Join-Path $taskStack.postgresBin 'pg_ctl.exe') stop -D $taskResolvedData -m fast -w -t 20
        if ($LASTEXITCODE -ne 0) { throw 'Não foi possível encerrar o cluster temporário.' }
    }
}

if ($Stop) {
    if (Test-Path -LiteralPath $taskManifestPath) {
        Stop-TestStack (Get-Content -LiteralPath $taskManifestPath -Raw | ConvertFrom-Json)
        Write-Output 'Backend e PostgreSQL de teste encerrados.'
    }
    exit 0
}
if ($PostgresPort -eq 5432 -or $BackendPort -eq 8080 -or $PostgresPort -eq $BackendPort) {
    throw 'Use portas distintas e exclusivas para teste. 5432 e 8080 são reservadas ao desenvolvimento.'
}
foreach ($taskPort in @($PostgresPort, $BackendPort)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $taskPort -ErrorAction SilentlyContinue) {
        throw "A porta $taskPort já está ocupada. Encerre o teste anterior ou escolha outra porta."
    }
}
foreach ($taskExecutable in @('initdb.exe', 'pg_ctl.exe', 'createdb.exe', 'psql.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin $taskExecutable))) { throw "Ausente: $taskExecutable" }
}
New-Item -ItemType Directory -Path $taskRuntime -Force | Out-Null
$taskRun = Join-Path $taskRuntime ('postgres-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $taskRun | Out-Null
$taskData = Join-Path $taskRun 'data'
$taskBackend = Join-Path (Split-Path -Parent $taskFrontend) 'backend'
$taskJar = Join-Path $taskBackend 'target\backend-0.0.1-SNAPSHOT.jar'
$taskStack = [pscustomobject]@{
    data = $taskData; postgresBin = $PostgresBin; postgresPort = $PostgresPort; backendPort = $BackendPort
    jdbc = "jdbc:postgresql://127.0.0.1:$PostgresPort/planejador_financeiro_test?currentSchema=public"
}
try {
    & (Join-Path $PostgresBin 'initdb.exe') -D $taskData -U backend_test --auth=trust --encoding=UTF8 --locale=C
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar o cluster isolado.' }
    $taskPgArgs = @('start', '-D', ('"' + $taskData + '"'), '-l', ('"' + (Join-Path $taskRun 'postgres.log') + '"'),
        '-o', ('"-h 127.0.0.1 -p ' + $PostgresPort + '"'), '-w', '-t', '20')
    $taskLauncher = Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList $taskPgArgs `
        -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $taskRun 'pg-start.out') `
        -RedirectStandardError (Join-Path $taskRun 'pg-start.err')
    if (-not $taskLauncher.WaitForExit(25000)) { throw 'Tempo excedido ao iniciar PostgreSQL de teste.' }
    $taskLauncher.WaitForExit()
    & (Join-Path $PostgresBin 'pg_isready.exe') -h 127.0.0.1 -p $PostgresPort -d postgres
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL isolado não confirmou disponibilidade.' }
    & (Join-Path $PostgresBin 'createdb.exe') -h 127.0.0.1 -p $PostgresPort -U backend_test planejador_financeiro_test
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao criar banco de teste.' }
    & (Join-Path $PostgresBin 'psql.exe') --no-password -v ON_ERROR_STOP=1 -h 127.0.0.1 -p $PostgresPort `
        -U backend_test -d planejador_financeiro_test -f (Join-Path $taskBackend 'src\main\resources\db\migration\V1.sql')
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao preparar o schema isolado.' }
    if (-not (Test-Path -LiteralPath $taskJar)) {
        Push-Location $taskBackend
        try { & .\mvnw.cmd -B -DskipTests package; if ($LASTEXITCODE -ne 0) { throw 'Falha na geração do JAR.' } }
        finally { Pop-Location }
    }
    $taskJava = if ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { (Get-Command java).Source }
    $taskJavaArgs = @('-jar', ('"' + $taskJar + '"'), ('--spring.datasource.url=' + $taskStack.jdbc),
        '--spring.datasource.username=backend_test', '--spring.datasource.password=', ('--server.port=' + $BackendPort), '--debug=false')
    Start-Process -FilePath $taskJava -ArgumentList $taskJavaArgs -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $taskRun 'backend.out') -RedirectStandardError (Join-Path $taskRun 'backend.err') | Out-Null
    $taskReady = $false
    $taskDeadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        try {
            $taskHealth = Invoke-RestMethod "http://127.0.0.1:$BackendPort/api/health/database" -TimeoutSec 2
            $taskReady = $taskHealth.success -and $taskHealth.database -eq 'planejador_financeiro_test'
        } catch { Start-Sleep -Milliseconds 500 }
    } while (-not $taskReady -and [DateTime]::UtcNow -lt $taskDeadline)
    if (-not $taskReady) { throw 'O backend não confirmou o banco de teste no prazo.' }
    $taskStack | ConvertTo-Json | Set-Content -LiteralPath $taskManifestPath -Encoding UTF8
    Write-Output "API de teste: http://127.0.0.1:$BackendPort/api"
    Write-Output 'Instância isolada pronta. Use -Stop ao terminar.'
} catch {
    Stop-TestStack $taskStack
    throw
}
