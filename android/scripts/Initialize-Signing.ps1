param([string]$SigningDirectory = (Join-Path $env:USERPROFILE '.nicetime\signing'))
$ErrorActionPreference = 'Stop'
$keyPath = Join-Path $SigningDirectory 'nicetime-release.p12'
$passwordPath = Join-Path $SigningDirectory 'password.clixml'
if ((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $passwordPath)) {
    throw 'Signing files already exist; refusing to replace them.'
}
New-Item -ItemType Directory -Force -Path $SigningDirectory | Out-Null
$random = New-Object byte[] 32
$rng = [Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($random)
$rng.Dispose()
$env:NICETIME_STORE_PASSWORD = [Convert]::ToBase64String($random)
try {
    ConvertTo-SecureString $env:NICETIME_STORE_PASSWORD -AsPlainText -Force | Export-Clixml -LiteralPath $passwordPath
    $keytool = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\keytool.exe' } else {
        'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\keytool.exe'
    }
    & $keytool -genkeypair -keystore $keyPath -storetype PKCS12 -alias nicetime -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=NiceTime Personal Release' -storepass:env NICETIME_STORE_PASSWORD -keypass:env NICETIME_STORE_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed; preserve the password file and inspect before retrying.' }
    Write-Output "Signing key created: $keyPath"
    Write-Output 'Password protected with Windows DPAPI for this user and machine. Make an independent secure backup before relying on this key for long-term updates.'
} finally { Remove-Item Env:NICETIME_STORE_PASSWORD -ErrorAction SilentlyContinue }
