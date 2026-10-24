param(
    [string]$Java8 = $env:JAVA8_HOME,
    [string]$Java17 = $env:JAVA17_HOME,
    [string]$Java21 = $env:JAVA21_HOME,
    [string]$Java25 = $env:JAVA25_HOME,
    [ValidateSet('forge-1.12.2','forge-1.16.5','forge-1.20.1','neoforge-1.21.1','neoforge-26.1.2','neoforge-26.2')]
    [string[]]$Versions = @('forge-1.12.2','forge-1.16.5','forge-1.20.1','neoforge-1.21.1','neoforge-26.1.2','neoforge-26.2')
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskHomes = @{8=$Java8;17=$Java17;21=$Java21;25=$Java25}
foreach ($taskEntry in $taskHomes.GetEnumerator()) {
    if ($taskEntry.Value) {
        if (-not (Test-Path -LiteralPath (Join-Path $taskEntry.Value 'bin/java.exe'))) { throw "Invalid JDK $($taskEntry.Key): $($taskEntry.Value)" }
        [Environment]::SetEnvironmentVariable("JAVA$($taskEntry.Key)_HOME", $taskEntry.Value, 'Process')
    }
}
if (-not $Java21) { throw 'Set JAVA21_HOME or pass -Java21 so all Gradle wrappers run on Java 21.' }
$env:JAVA_HOME = $Java21
Push-Location $taskRoot
try {
    & .\gradlew.bat :test --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Core tests failed.' }
    $taskDist = Join-Path $taskRoot 'dist'
    New-Item -ItemType Directory -Path $taskDist -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $taskDist 'sources') -Force | Out-Null
    foreach ($taskVersion in $Versions) {
        Write-Host "Building $taskVersion"
        $taskModule = Join-Path $taskRoot "versions/$taskVersion"
        Push-Location $taskModule
        try {
            & .\gradlew.bat build --console=plain
            if ($LASTEXITCODE -ne 0) { throw "Build failed: $taskVersion" }
        } finally { Pop-Location }
        $taskJars = Get-ChildItem -LiteralPath (Join-Path $taskModule 'build/libs') -Filter '*.jar' -File
        foreach ($taskJar in $taskJars) {
            if ($taskJar.Name -like '*-sources.jar') { Copy-Item -LiteralPath $taskJar.FullName -Destination (Join-Path $taskDist 'sources') }
            elseif ($taskJar.Name -notmatch '-(dev|deobf|all)\.jar$') { Copy-Item -LiteralPath $taskJar.FullName -Destination $taskDist }
        }
    }
    Copy-Item -LiteralPath (Join-Path $taskRoot 'README.md'),(Join-Path $taskRoot 'LICENSE') -Destination $taskDist
    New-Item -ItemType Directory -Path (Join-Path $taskDist 'docs') -Force | Out-Null
    Get-ChildItem -LiteralPath (Join-Path $taskRoot 'docs') -Filter '*.md' -File | Copy-Item -Destination (Join-Path $taskDist 'docs')
    Write-Host "Packages: $taskDist"
} finally { Pop-Location }
