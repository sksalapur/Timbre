# Generate a persistent Android signing keystore and output GitHub Secrets instructions
param(
    [string]$KeystoreName = "truebit-release.jks",
    [string]$Alias = "truebit",
    [string]$Password = "TrueBitPassword123"
)

$ErrorActionPreference = "Stop"

Write-Host "🔐 Generating Android Release Keystore..." -ForegroundColor Cyan

# Locate keytool
$keytool = Get-Command "keytool" -ErrorAction SilentlyContinue
if (-not $keytool) {
    # Check JAVA_HOME
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\keytool.exe")) {
        $keytool = "$env:JAVA_HOME\bin\keytool.exe"
    } else {
        Write-Error "keytool not found! Please ensure Java JDK is installed and added to PATH."
    }
}

& $keytool -genkeypair -v `
    -keystore $KeystoreName `
    -alias $Alias `
    -keyalg RSA `
    -keysize 2048 `
    -validity 10000 `
    -storepass $Password `
    -keypass $Password `
    -dname "CN=TrueBit, OU=Audio, O=TrueBit, L=HQ, S=Audio, C=US"

if (Test-Path $KeystoreName) {
    Write-Host "✅ Keystore created: $KeystoreName" -ForegroundColor Green
    
    $bytes = [System.IO.File]::ReadAllBytes((Resolve-Path $KeystoreName))
    $base64 = [Convert]::ToBase64String($bytes)
    
    Write-Host "`n📋 Add these 4 Secrets in your GitHub Repo (Settings -> Secrets and variables -> Actions):`n" -ForegroundColor Yellow
    Write-Host "KEYSTORE_BASE64: $base64" -ForegroundColor White
    Write-Host "KEY_ALIAS: $Alias" -ForegroundColor White
    Write-Host "KEYSTORE_PASSWORD: $Password" -ForegroundColor White
    Write-Host "KEY_PASSWORD: $Password" -ForegroundColor White
    
    # Save base64 to txt file for convenience
    $base64 | Set-Content "keystore_base64.txt"
    Write-Host "`nSaved base64 string to keystore_base64.txt (do not commit this file to git!)." -ForegroundColor Green
}
