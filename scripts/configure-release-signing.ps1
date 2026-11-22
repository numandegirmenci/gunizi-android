# Run locally on your own Windows computer. Requires JDK keytool and GitHub CLI.
# Never commit a signing private key or signing password.
$ErrorActionPreference = "Stop"
$repo = "numandegirmenci/gunizi-android"
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) { throw "Java JDK 17 / keytool gerekli." }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw "GitHub CLI gerekli: https://cli.github.com/" }
& gh auth status *> $null
if ($LASTEXITCODE -ne 0) { throw "Once gh auth login calistir." }
$folder = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'GunIzi-Signing-PRIVATE'
New-Item -ItemType Directory -Force -Path $folder | Out-Null
$keystore = Join-Path $folder 'gunizi-release.p12'
$alias = 'gunizi-release'
Write-Host "Anahtar bilgisayariniza kaydedilecek: $keystore"
Write-Host 'Anahtar dosyasini ve sifrenizi GUVENLI BIR YERDE yedekleyin.'
$secure = Read-Host "Imza sifrenizi girin (en az 12 karakter)" -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
if ($password.Length -lt 12) { throw "En az 12 karakterlik sifre gerekli." }
if (-not (Test-Path $keystore)) {
    & keytool -genkeypair -noprompt -storetype PKCS12 -keystore $keystore `
      -alias $alias -keyalg RSA -keysize 3072 -validity 10000 `
      -dname 'CN=GunIzi,OU=Android,O=Independent,L=Istanbul,C=TR' `
      -storepass $password -keypass $password
    if ($LASTEXITCODE -ne 0) { throw 'Keystore uretilmedi.' }
} else {
    Write-Host 'Var olan anahtar kullanilacak. Yeni anahtar olusturulmayacak.'
    & keytool -list -storetype PKCS12 -keystore $keystore -storepass $password -alias $alias *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Anahtar dosyasi veya sifre yanlis.' }
}
[Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore)) |
    & gh secret set GUNIZI_KEYSTORE_BASE64 --repo $repo
if ($LASTEXITCODE -ne 0) { throw 'Keystore secret hata.' }
$password | & gh secret set GUNIZI_KEYSTORE_PASSWORD --repo $repo
if ($LASTEXITCODE -ne 0) { throw 'Store password secret hata.' }
$alias | & gh secret set GUNIZI_KEY_ALIAS --repo $repo
if ($LASTEXITCODE -ne 0) { throw 'Alias secret hata.' }
$password | & gh secret set GUNIZI_KEY_PASSWORD --repo $repo
if ($LASTEXITCODE -ne 0) { throw 'Key password secret hata.' }
$password = $null
Write-Host 'GIT HUB SECRET KURULUMU TAMAMLANDI' -ForegroundColor Green
Write-Host "Keystore dosyanizi kaybetmeyin: $keystore"
