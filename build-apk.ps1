$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$Project = Split-Path -Parent $MyInvocation.MyCommand.Path
$GradleVersion = '8.9'

function Fail([string]$Message) {
    Write-Host ''
    Write-Host ('[ERROR] ' + $Message) -ForegroundColor Red
    exit 1
}

function Info([string]$Message) {
    Write-Host ('[YaoShiJi] ' + $Message) -ForegroundColor Cyan
}

function Find-JavaHome {
    $Candidates = New-Object System.Collections.Generic.List[string]

    function Add-Candidate([string]$Path) {
        if ($Path -and -not $Candidates.Contains($Path)) {
            $Candidates.Add($Path)
        }
    }

    Add-Candidate $env:JAVA_HOME
    Add-Candidate $env:STUDIO_JDK

    if ($env:ProgramFiles) {
        Add-Candidate (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr')
        Add-Candidate (Join-Path $env:ProgramFiles 'Android\Android Studio\jre')
        Add-Candidate (Join-Path $env:ProgramFiles 'Microsoft\jdk-17')
    }
    if (${env:ProgramFiles(x86)}) {
        Add-Candidate (Join-Path ${env:ProgramFiles(x86)} 'Android\Android Studio\jbr')
    }
    if ($env:LOCALAPPDATA) {
        Add-Candidate (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr')
        Add-Candidate (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jre')
    }

    # Android Studio registry locations (default and custom install paths).
    $RegistryKeys = @(
        'HKLM:\SOFTWARE\Android Studio',
        'HKCU:\SOFTWARE\Android Studio',
        'HKLM:\SOFTWARE\WOW6432Node\Android Studio'
    )
    foreach ($Key in $RegistryKeys) {
        try {
            $Item = Get-ItemProperty -Path $Key -ErrorAction Stop
            foreach ($Name in @('Path','InstallPath')) {
                if ($Item.$Name) {
                    Add-Candidate (Join-Path $Item.$Name 'jbr')
                    Add-Candidate (Join-Path $Item.$Name 'jre')
                }
            }
        } catch { }
    }

    # Java already available on PATH.
    try {
        $JavaCmd = Get-Command java.exe -ErrorAction Stop
        if ($JavaCmd -and $JavaCmd.Source) {
            $Bin = Split-Path -Parent $JavaCmd.Source
            Add-Candidate (Split-Path -Parent $Bin)
        }
    } catch { }

    # Common JDK vendor directories, including versioned folders.
    $Roots = @()
    if ($env:ProgramFiles) {
        $Roots += (Join-Path $env:ProgramFiles 'Java')
        $Roots += (Join-Path $env:ProgramFiles 'Eclipse Adoptium')
        $Roots += (Join-Path $env:ProgramFiles 'Microsoft')
        $Roots += (Join-Path $env:ProgramFiles 'Zulu')
        $Roots += (Join-Path $env:ProgramFiles 'BellSoft')
    }
    if ($env:USERPROFILE) {
        $Roots += (Join-Path $env:USERPROFILE '.jdks')
    }
    foreach ($Root in $Roots) {
        if (Test-Path $Root) {
            Get-ChildItem -Path $Root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
                Add-Candidate $_.FullName
            }
        }
    }

    foreach ($Candidate in $Candidates) {
        if ($Candidate -and (Test-Path (Join-Path $Candidate 'bin\java.exe'))) {
            # AGP 8.7 needs JDK 17 or newer. Verify the major version.
            try {
                $VersionText = & (Join-Path $Candidate 'bin\java.exe') -version 2>&1 | Out-String
                if ($VersionText -match 'version "(\d+)(?:\.(\d+))?') {
                    $Major = [int]$Matches[1]
                    if ($Major -eq 1 -and $Matches[2]) { $Major = [int]$Matches[2] }
                    if ($Major -ge 17) { return $Candidate }
                }
            } catch { }
        }
    }
    return $null
}

function Install-Jdk17WithWinget {
    $Winget = Get-Command winget.exe -ErrorAction SilentlyContinue
    if (-not $Winget) { return $false }

    Info 'JDK 17 was not found. Trying to install Microsoft OpenJDK 17 with winget...'
    & $Winget.Source install --id Microsoft.OpenJDK.17 --exact --accept-source-agreements --accept-package-agreements --silent
    if ($LASTEXITCODE -ne 0) { return $false }
    return $true
}

function Find-AndroidCli([string]$SdkRoot) {
    $Candidates = @(
        (Join-Path $SdkRoot 'cmdline-tools\latest\bin\android.bat'),
        (Join-Path $SdkRoot 'cmdline-tools\latest\bin\android.exe'),
        (Join-Path $SdkRoot 'cmdline-tools\bin\android.bat'),
        (Join-Path $SdkRoot 'cmdline-tools\bin\android.exe')
    )

    foreach ($Candidate in $Candidates) {
        if (Test-Path $Candidate) { return $Candidate }
    }

    $Cmd = Get-Command android -ErrorAction SilentlyContinue
    if ($Cmd) { return $Cmd.Source }

    $CmdlineRoot = Join-Path $SdkRoot 'cmdline-tools'
    if (Test-Path $CmdlineRoot) {
        $Found = Get-ChildItem -Path $CmdlineRoot -Recurse -File -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -eq 'android.bat' -or $_.Name -eq 'android.exe' } |
            Select-Object -First 1
        if ($Found) { return $Found.FullName }
    }
    return $null
}

function Find-SdkManager([string]$SdkRoot) {
    $Candidates = @(
        (Join-Path $SdkRoot 'cmdline-tools\latest\bin\sdkmanager.bat'),
        (Join-Path $SdkRoot 'cmdline-tools\bin\sdkmanager.bat'),
        (Join-Path $SdkRoot 'tools\bin\sdkmanager.bat')
    )
    foreach ($Candidate in $Candidates) {
        if (Test-Path $Candidate) { return $Candidate }
    }
    return $null
}

Info 'Checking Android build environment...'

$Sdk = $env:ANDROID_SDK_ROOT
if (-not $Sdk) { $Sdk = $env:ANDROID_HOME }
if (-not $Sdk -and $env:LOCALAPPDATA) { $Sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }

if (-not $Sdk -or -not (Test-Path $Sdk)) {
    Fail 'Android SDK was not found. Install Android Studio first, then install Android 15 (API 35) in Tools > SDK Manager.'
}
Info ('Android SDK: ' + $Sdk)

$EscapedSdk = $Sdk.Replace('\', '\\')
('sdk.dir=' + $EscapedSdk) | Set-Content -Path (Join-Path $Project 'local.properties') -Encoding ASCII

$AndroidJar = Join-Path $Sdk 'platforms\android-35\android.jar'
$BuildToolsDir = Join-Path $Sdk 'build-tools\35.0.0'
$PlatformToolsDir = Join-Path $Sdk 'platform-tools'

if (-not (Test-Path $AndroidJar) -or -not (Test-Path $BuildToolsDir) -or -not (Test-Path $PlatformToolsDir)) {
    Info 'Required SDK packages are missing. Trying automatic installation...'

    $AndroidCli = Find-AndroidCli $Sdk
    if ($AndroidCli) {
        Info ('Using Android CLI: ' + $AndroidCli)
        $SdkArg = '--sdk=' + $Sdk
        & $AndroidCli $SdkArg 'sdk' 'install' 'platforms/android-35' 'build-tools/35.0.0' 'platform-tools'
        if ($LASTEXITCODE -ne 0) {
            Fail 'Android CLI package installation failed. Open Android Studio > Tools > SDK Manager and install Android 15 (API 35), Build-Tools 35.0.0, and Platform-Tools.'
        }
    }
    else {
        $SdkManager = Find-SdkManager $Sdk
        if ($SdkManager) {
            Info ('Android CLI not found. Falling back to: ' + $SdkManager)
            & $SdkManager 'platforms/android-35' 'build-tools/35.0.0' 'platform-tools'
            if ($LASTEXITCODE -ne 0) {
                Fail 'SDK package installation failed. Open Android Studio > Tools > SDK Manager and install Android 15 (API 35), Build-Tools 35.0.0, and Platform-Tools.'
            }
        }
        else {
            Fail 'No Android SDK package manager was found. Open Android Studio > Tools > SDK Manager and install the required packages manually.'
        }
    }
}

if (-not (Test-Path $AndroidJar)) {
    Fail 'Android 15 platform is still missing after installation. In Android Studio SDK Manager, install Android 15 (API 35).'
}
if (-not (Test-Path $BuildToolsDir)) {
    Fail 'Build Tools 35.0.0 are still missing after installation. In Android Studio SDK Manager > SDK Tools, enable Show Package Details and install 35.0.0.'
}
if (-not (Test-Path $PlatformToolsDir)) {
    Fail 'Platform-Tools are still missing after installation. Install Android SDK Platform-Tools in SDK Manager.'
}
Info 'Required Android SDK packages are ready.'

# Prefer an explicitly supplied JAVA_HOME. Windows PowerShell 5.1 can treat
# native stderr from `java -version` as an error when ErrorActionPreference=Stop,
# so do not reject a valid JDK merely because version text is written to stderr.
$JavaHome = $null
if ($env:JAVA_HOME) {
    $ExplicitJava = Join-Path $env:JAVA_HOME 'bin\java.exe'
    if (Test-Path $ExplicitJava) {
        $JavaHome = $env:JAVA_HOME
        Info ('Using JAVA_HOME from environment: ' + $JavaHome)
    }
}

if (-not $JavaHome) {
    $JavaHome = Find-JavaHome
}
if (-not $JavaHome) {
    if (Install-Jdk17WithWinget) {
        # Refresh the process environment after installation and scan again.
        $MachinePath = [Environment]::GetEnvironmentVariable('Path', 'Machine')
        $UserPath = [Environment]::GetEnvironmentVariable('Path', 'User')
        if ($MachinePath -or $UserPath) { $env:Path = ($MachinePath + ';' + $UserPath) }
        $JavaHome = Find-JavaHome
    }
}
if (-not $JavaHome) {
    Fail 'JDK 17+ was not found. Set JAVA_HOME to a JDK 17 folder (for example F:\jdk17) and run this script again.'
}

# If the JDK has a release metadata file, validate the major version without
# invoking java -version (which writes version text to stderr on Windows).
$ReleaseFile = Join-Path $JavaHome 'release'
if (Test-Path $ReleaseFile) {
    $ReleaseText = Get-Content -Path $ReleaseFile -Raw -ErrorAction SilentlyContinue
    if ($ReleaseText -match 'JAVA_VERSION="(\d+)') {
        $DetectedJavaMajor = [int]$Matches[1]
        if ($DetectedJavaMajor -lt 17) {
            Fail ('JAVA_HOME points to Java ' + $DetectedJavaMajor + '. JDK 17 or newer is required.')
        }
    }
}

$env:JAVA_HOME = $JavaHome
$env:Path = ((Join-Path $JavaHome 'bin') + ';' + $env:Path)
Info ('Java: ' + $JavaHome)

# Temporarily avoid Stop behavior for java -version because Java prints version
# information to stderr by design.
$SavedErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
& (Join-Path $JavaHome 'bin\java.exe') -version
$JavaExitCode = $LASTEXITCODE
$ErrorActionPreference = $SavedErrorActionPreference
if ($JavaExitCode -ne 0) { Fail 'Java could not start.' }

$ToolsDir = Join-Path $Project '.build-tools'
$GradleDir = Join-Path $ToolsDir ('gradle-' + $GradleVersion)
$GradleBat = Join-Path $GradleDir 'bin\gradle.bat'

if (-not (Test-Path $GradleBat)) {
    New-Item -ItemType Directory -Force -Path $ToolsDir | Out-Null
    $ZipPath = Join-Path $ToolsDir ('gradle-' + $GradleVersion + '-bin.zip')
    $GradleUrl = 'https://services.gradle.org/distributions/gradle-' + $GradleVersion + '-bin.zip'

    Info ('Downloading Gradle ' + $GradleVersion + '...')
    try {
        Invoke-WebRequest -Uri $GradleUrl -OutFile $ZipPath -UseBasicParsing
    }
    catch {
        Fail ('Gradle download failed. Check Internet access or proxy settings. Details: ' + $_.Exception.Message)
    }

    try {
        Expand-Archive -Path $ZipPath -DestinationPath $ToolsDir -Force
        Remove-Item $ZipPath -Force
    }
    catch {
        Fail ('Could not extract Gradle. Details: ' + $_.Exception.Message)
    }
}

if (-not (Test-Path $GradleBat)) { Fail 'Gradle executable was not found after download/extraction.' }

Push-Location $Project
try {
    Info 'Building Debug APK...'
    & $GradleBat --no-daemon --stacktrace clean assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Fail 'Gradle build failed. Copy the last 50-100 lines from this window and send them to ChatGPT.'
    }
}
finally {
    Pop-Location
}

$Apk = Join-Path $Project 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path $Apk)) { Fail 'Build finished but app-debug.apk was not found.' }

$OutputApk = Join-Path $Project 'YaoShiJi-v1.3.2-beta-debug.apk'
Copy-Item -Path $Apk -Destination $OutputApk -Force

Write-Host ''
Write-Host '[SUCCESS] APK build completed.' -ForegroundColor Green
Write-Host ('APK: ' + $OutputApk) -ForegroundColor Green
Write-Host 'Copy this APK to the phone and test installation through ZhuoYiTong.' -ForegroundColor Green
