# Generate a persistent Android signing keystore and output GitHub Secrets instructions
param(
    [string] = "timbre-release.jks",
    [string] = "timbre",
    [string] = "TimbrePassword123"
)

Continue = "Stop"

Write-Host "🔑 Generating Android Release Keystore for Timbre..." -ForegroundColor Cyan

# Locate keytool
 = Get-Command "keytool" -ErrorAction SilentlyContinue
if (-not ) {
    if ( -and (Test-Path "\bin\keytool.exe")) {
         = "\bin\keytool.exe"
    } else {
        Write-Error "keytool not found! Please ensure Java JDK is installed and added to PATH."
    }
}

&  -genkeypair -v 
    -keystore  
    -alias  
    -keyalg RSA 
    -keysize 2048 
    -validity 10000 
    -storepass  
    -keypass  
    -dname "CN=Timbre, OU=Audio, O=Timbre, L=HQ, S=Audio, C=US"

if (Test-Path ) {
    Write-Host "✅ Keystore created: " -ForegroundColor Green
    
     = [System.IO.File]::ReadAllBytes((Resolve-Path ))
     = [Convert]::ToBase64String()
    
    Write-Host "
📋 Add these 4 Secrets in your GitHub Repo (Settings -> Secrets and variables -> Actions):
" -ForegroundColor Yellow
    Write-Host "KEYSTORE_BASE64: " -ForegroundColor White
    Write-Host "KEY_ALIAS: " -ForegroundColor White
    Write-Host "KEYSTORE_PASSWORD: " -ForegroundColor White
    Write-Host "KEY_PASSWORD: " -ForegroundColor White
    
     | Set-Content "keystore_base64.txt"
    Write-Host "
Saved base64 string to keystore_base64.txt (do not commit this file to git!)." -ForegroundColor Green
}
