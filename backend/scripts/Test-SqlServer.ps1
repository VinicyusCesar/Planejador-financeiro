param(
    [Parameter(Mandatory=$true)][string]$Endpoint,
    [Parameter(Mandatory=$true)][string]$AuthDllDirectory,
    [switch]$TrustLocalCertificate,
    [string]$MavenCommand = '.\mvnw.cmd'
)
$ErrorActionPreference = 'Stop'
if (-not (Get-ChildItem -LiteralPath $AuthDllDirectory -Filter 'mssql-jdbc_auth-13.4.0.*.dll')) {
    throw 'DLL oficial 13.4.0 nao encontrada. Verifique tambem a arquitetura da JVM.'
}
$variaveis = @('SQLSERVER_ENDPOINT','SQLSERVER_DATABASE','SQLSERVER_TEST_ENABLED','SQLSERVER_TRUST_SERVER_CERTIFICATE','PATH')
$anteriores = @{}
foreach ($nome in $variaveis) { $anteriores[$nome] = [Environment]::GetEnvironmentVariable($nome,'Process') }
Push-Location (Join-Path $PSScriptRoot '..')
try {
    $env:SQLSERVER_ENDPOINT = $Endpoint
    $env:SQLSERVER_DATABASE = 'PlanejadorFinanceiroTest'
    $env:SQLSERVER_TEST_ENABLED = 'true'
    $env:SQLSERVER_TRUST_SERVER_CERTIFICATE = $TrustLocalCertificate.IsPresent.ToString().ToLowerInvariant()
    $env:PATH = $AuthDllDirectory + [IO.Path]::PathSeparator + $env:PATH
    New-Item -ItemType Directory -Force -Path 'target' | Out-Null
    Get-Command $MavenCommand -ErrorAction Stop | Out-Null
    $inicio = Get-Date
    # Windows PowerShell trata stderr nativo como ErrorRecord, inclusive warnings Java.
    $ErrorActionPreference = 'Continue'
    & $MavenCommand '-Dtest=*SqlServerTests,SqlServerIntegrationTests' test 2>&1 | Tee-Object -FilePath 'target/sqlserver-maven.log'
    $codigo = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    $linhas = @('# Testes reais SQL Server', '', "Execucao: $inicio", '', '| Cenario | Esperado | Obtido | Situacao |', '|---|---|---|---|')
    $relatorios = @(Get-ChildItem -Path 'target/surefire-reports/TEST-*SqlServer*.xml' -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -ge $inicio })
    foreach ($arquivo in $relatorios) {
        [xml]$xml = Get-Content -LiteralPath $arquivo.FullName -Raw
        foreach ($caso in $xml.testsuite.testcase) {
            $situacao = if ($caso.skipped) { 'PENDENTE' } elseif ($caso.failure -or $caso.error) { 'REPROVADO' } else { 'APROVADO' }
            $obtido = if ($caso.failure -or $caso.error) { 'Ver XML e log com erro real' } elseif ($caso.skipped) { 'Nao executado' } else { 'Todas as assercoes satisfeitas' }
            $linhas += "| $($caso.name) | Assercoes do cenario em Java | $obtido | $situacao |"
        }
    }
    if (-not $relatorios.Count) { $linhas += '| Inicializacao | Executar suite no SQL Server | Nenhum relatorio novo; consultar log | PENDENTE |' }
    $linhas | Set-Content -LiteralPath 'target/RESULTADOS_SQLSERVER.md' -Encoding UTF8
    if ($codigo -ne 0) { throw "Testes falharam (Maven $codigo). Consulte target/sqlserver-maven.log e surefire-reports." }
} finally {
    Pop-Location
    foreach ($nome in $variaveis) { [Environment]::SetEnvironmentVariable($nome,$anteriores[$nome],'Process') }
}
