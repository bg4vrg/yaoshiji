$ErrorActionPreference = 'Stop'

function Fail([string]$Message) {
    Write-Host "" 
    Write-Host "[ERROR] $Message" -ForegroundColor Red
    exit 1
}

function Info([string]$Message) {
    Write-Host "[YaoShiJi] $Message" -ForegroundColor Cyan
}

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $Root

Info "Offline Android build (no Gradle / no Maven repositories)."

# Android SDK
$sdkCandidates = @()
if ($env:ANDROID_SDK_ROOT) { $sdkCandidates += $env:ANDROID_SDK_ROOT }
if ($env:ANDROID_HOME) { $sdkCandidates += $env:ANDROID_HOME }
if ($env:LOCALAPPDATA) { $sdkCandidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk') }
$sdkCandidates += 'C:\Android\Sdk'
$Sdk = $null
foreach ($c in $sdkCandidates) {
    if ($c -and (Test-Path (Join-Path $c 'platforms\android-35\android.jar'))) {
        $Sdk = $c
        break
    }
}
if (-not $Sdk) { Fail "Android SDK API 35 was not found. Install Android 15 (API 35) in Android Studio SDK Manager." }
Info "Android SDK: $Sdk"

$BuildTools = Join-Path $Sdk 'build-tools\35.0.0'
if (-not (Test-Path $BuildTools)) { Fail "Android Build Tools 35.0.0 was not found: $BuildTools" }
$AndroidJar = Join-Path $Sdk 'platforms\android-35\android.jar'

$aapt2 = Join-Path $BuildTools 'aapt2.exe'
$zipalign = Join-Path $BuildTools 'zipalign.exe'
$d8 = Join-Path $BuildTools 'd8.bat'
$apksigner = Join-Path $BuildTools 'apksigner.bat'
foreach ($tool in @($aapt2,$zipalign,$d8,$apksigner)) {
    if (-not (Test-Path $tool)) { Fail "Required Android build tool not found: $tool" }
}

# JDK
$JavaHome = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\javac.exe'))) {
    $JavaHome = $env:JAVA_HOME
}
if (-not $JavaHome -and (Test-Path 'F:\jdk17\bin\javac.exe')) { $JavaHome = 'F:\jdk17' }
if (-not $JavaHome) { Fail "JDK 17 was not found. Set JAVA_HOME to your JDK 17 folder (for example F:\jdk17)." }
$javac = Join-Path $JavaHome 'bin\javac.exe'
$jar = Join-Path $JavaHome 'bin\jar.exe'
$keytool = Join-Path $JavaHome 'bin\keytool.exe'
foreach ($tool in @($javac,$jar,$keytool)) {
    if (-not (Test-Path $tool)) { Fail "Required JDK tool not found: $tool" }
}
Info "Java: $JavaHome"

$Work = Join-Path $Root '.manual-build'
$PersistentKeyStore = Join-Path $Root 'debug.keystore'
$LegacyKeyStore = Join-Path $Work 'debug.keystore'
# Preserve the signing key from an earlier V6 build so V1.0.1 can update the installed APK.
if ((-not (Test-Path $PersistentKeyStore)) -and (Test-Path $LegacyKeyStore)) {
    Copy-Item $LegacyKeyStore $PersistentKeyStore -Force
    Info "Migrated existing signing key to: $PersistentKeyStore"
}
if (Test-Path $Work) { Remove-Item $Work -Recurse -Force }
New-Item -ItemType Directory -Force -Path $Work | Out-Null
$CompiledRes = Join-Path $Work 'compiled-res.zip'
$Gen = Join-Path $Work 'gen'
$Classes = Join-Path $Work 'classes'
$Dex = Join-Path $Work 'dex'
New-Item -ItemType Directory -Force -Path $Gen,$Classes,$Dex | Out-Null

# AAPT2 requires a manifest package when building without AGP.
$ManifestSrc = Join-Path $Root 'app\src\main\AndroidManifest.xml'
$ManifestTmp = Join-Path $Work 'AndroidManifest.xml'
$manifestText = [System.IO.File]::ReadAllText($ManifestSrc, [System.Text.Encoding]::UTF8)
if ($manifestText -notmatch '<manifest[^>]*\spackage=') {
    $manifestText = $manifestText -replace '<manifest\s+', '<manifest package="com.yaoshiji.app" '
}
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($ManifestTmp, $manifestText, $utf8NoBom)

Info "Compiling resources..."
& $aapt2 compile --dir (Join-Path $Root 'app\src\main\res') -o $CompiledRes
if ($LASTEXITCODE -ne 0) { Fail "aapt2 resource compilation failed." }

$UnsignedBase = Join-Path $Work 'unsigned-base.apk'
Info "Linking resources and manifest..."
& $aapt2 link -o $UnsignedBase -I $AndroidJar --manifest $ManifestTmp --java $Gen --min-sdk-version 26 --target-sdk-version 35 --version-code 133 --version-name 1.3.2-beta $CompiledRes
if ($LASTEXITCODE -ne 0) { Fail "aapt2 link failed." }

Info "Compiling Java sources..."
$javaFiles = @(Get-ChildItem (Join-Path $Root 'app\src\main\java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$rFiles = @(Get-ChildItem $Gen -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$allJava = @($javaFiles + $rFiles)
if ($allJava.Count -eq 0) { Fail "No Java source files were found." }
$javacArgs = @('-encoding','UTF-8','-source','17','-target','17','-classpath',$AndroidJar,'-d',$Classes) + $allJava
& $javac @javacArgs
if ($LASTEXITCODE -ne 0) { Fail "javac compilation failed." }

$ClassesJar = Join-Path $Work 'classes.jar'
& $jar cf $ClassesJar -C $Classes .
if ($LASTEXITCODE -ne 0) { Fail "Could not package compiled Java classes." }

Info "Converting bytecode to DEX..."
& $d8 --lib $AndroidJar --min-api 26 --output $Dex $ClassesJar
if ($LASTEXITCODE -ne 0) { Fail "D8 failed." }
$ClassesDex = Join-Path $Dex 'classes.dex'
if (-not (Test-Path $ClassesDex)) { Fail "D8 completed but classes.dex was not created." }

$UnsignedDex = Join-Path $Work 'unsigned-with-dex.apk'
Copy-Item $UnsignedBase $UnsignedDex -Force
& $jar uf $UnsignedDex -C $Dex classes.dex
if ($LASTEXITCODE -ne 0) { Fail "Could not add classes.dex to APK." }

$Aligned = Join-Path $Work 'aligned.apk'
Info "Zip-aligning APK..."
& $zipalign -f 4 $UnsignedDex $Aligned
if ($LASTEXITCODE -ne 0) { Fail "zipalign failed." }

$KeyStore = $PersistentKeyStore
if (-not (Test-Path $KeyStore)) {
    Info "Creating debug signing key..."
    & $keytool -genkeypair -keystore $KeyStore -storepass android -alias androiddebugkey -keypass android -dname 'CN=Android Debug,O=Android,C=US' -keyalg RSA -keysize 2048 -validity 10000
    if ($LASTEXITCODE -ne 0) { Fail "Could not create debug signing key." }
}

$OutApk = Join-Path $Root 'YaoShiJi-v1.3.2-beta-debug.apk'
if (Test-Path $OutApk) { Remove-Item $OutApk -Force }
Info "Signing APK..."
& $apksigner sign --ks $KeyStore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out $OutApk $Aligned
if ($LASTEXITCODE -ne 0) { Fail "APK signing failed." }

Info "Verifying APK signature..."
& $apksigner verify --verbose $OutApk
if ($LASTEXITCODE -ne 0) { Fail "APK signature verification failed." }

Write-Host ""
Write-Host "[SUCCESS] APK build completed." -ForegroundColor Green
Write-Host "APK: $OutApk" -ForegroundColor Green
Write-Host "This build did not use Gradle, AGP, Maven Central, or Google Maven." -ForegroundColor Green
