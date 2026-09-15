param([string]$SigningDirectory = (Join-Path $env:USERPROFILE '.nicetime\signing'))
$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$env:NICETIME_KEYSTORE = Join-Path $SigningDirectory 'nicetime-release.p12'
$env:NICETIME_KEY_ALIAS = 'nicetime'
$secure = Import-Clixml -LiteralPath (Join-Path $SigningDirectory 'password.clixml')
$credential = New-Object System.Net.NetworkCredential('', $secure)
$env:NICETIME_STORE_PASSWORD = $credential.Password
Push-Location $projectDirectory
try {
    & .\gradlew.bat --gradle-user-home .gradle-user assembleRelease '-Pkotlin.compiler.execution.strategy=in-process'
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed' }
    Get-FileHash -Algorithm SHA256 -LiteralPath 'app\build\outputs\apk\release\app-release.apk'
} finally {
    Pop-Location
    Remove-Item Env:NICETIME_STORE_PASSWORD, Env:NICETIME_KEYSTORE, Env:NICETIME_KEY_ALIAS -ErrorAction SilentlyContinue
}
