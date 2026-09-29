$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
if (-not $env:JAVA_HOME -and (Test-Path -LiteralPath 'C:\Program Files\Java\jdk-21')) {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
}
gradle clean verifyCardWorlds --console=plain
if ($LASTEXITCODE -ne 0) { throw "Build failed with exit code $LASTEXITCODE" }
if (Get-Command py -ErrorAction SilentlyContinue) {
    py -3 tools\verify_artifact.py
    if ($LASTEXITCODE -ne 0) { throw "Artifact verification failed with exit code $LASTEXITCODE" }
}
Get-ChildItem -LiteralPath "$PSScriptRoot\build\libs" -Filter '*.jar' | Select-Object Name, Length
