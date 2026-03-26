param(
    [string]$AppVersion = $env:APP_VERSION,
    [string]$JavaFxLib = $env:JAVAFX_LIB,
    [string]$JavaFxClassifier = $env:JAVAFX_CLASSIFIER,
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = "Stop"

function Get-PlatformInfo {
    param([string]$Classifier)

    switch ($Classifier) {
        "win" {
            return @{
                Family = "windows"
                BundleName = "Connect Four FX"
                ArchiveSuffix = "portable"
                JavaFxPathValue = '$APPDIR\javafx'
            }
        }
        "mac" {
            return @{
                Family = "macos"
                BundleName = "Connect Four FX.app"
                ArchiveSuffix = "macos-x64"
                JavaFxPathValue = '$APPDIR/javafx'
            }
        }
        "mac-aarch64" {
            return @{
                Family = "macos"
                BundleName = "Connect Four FX.app"
                ArchiveSuffix = "macos-arm64"
                JavaFxPathValue = '$APPDIR/javafx'
            }
        }
        default {
            throw "Unsupported JAVAFX_CLASSIFIER '$Classifier'. Use win, mac, or mac-aarch64."
        }
    }
}

function Normalize-Version {
    param([string]$RawVersion)

    if ([string]::IsNullOrWhiteSpace($RawVersion)) {
        $RawVersion = "1.0.0"
    }

    $parts = [regex]::Matches($RawVersion, '\d+') | ForEach-Object { $_.Value }
    if ($parts.Count -eq 0) {
        throw "APP_VERSION must contain at least one number, for example 1.0.0 or v1.0.0."
    }

    return [string]::Join('.', $parts)
}

function Resolve-JavaCommand {
    param(
        [string]$JavaHomePath,
        [string]$CommandName
    )

    $exeName = if ($IsWindows -or $env:OS -eq "Windows_NT") { "$CommandName.exe" } else { $CommandName }

    if (-not [string]::IsNullOrWhiteSpace($JavaHomePath)) {
        $candidate = Join-Path $JavaHomePath "bin/$exeName"
        if (Test-Path $candidate) {
            return $candidate
        }
    }

    $fallback = Get-Command $CommandName -ErrorAction SilentlyContinue
    if ($null -ne $fallback) {
        return $fallback.Source
    }

    throw "Could not find '$CommandName'. Set JAVA_HOME to a JDK 17+ install and try again."
}

function Resolve-JavaFxJar {
    param(
        [string]$SearchDir,
        [string]$SdkName,
        [string]$Classifier,
        [string]$BaseName
    )

    if ([string]::IsNullOrWhiteSpace($SearchDir) -or -not (Test-Path $SearchDir)) {
        return $null
    }

    $sdkCandidate = Join-Path $SearchDir $SdkName
    if (Test-Path $sdkCandidate) {
        return (Resolve-Path $sdkCandidate).Path
    }

    $pattern = "$BaseName-*-$Classifier.jar"
    $artifact = Get-ChildItem -Path $SearchDir -Filter $pattern -File | Sort-Object Name | Select-Object -First 1
    if ($null -ne $artifact) {
        return $artifact.FullName
    }

    return $null
}

function Get-JavaFxJars {
    param(
        [string]$SearchDir,
        [string]$Classifier
    )

    $base = Resolve-JavaFxJar -SearchDir $SearchDir -SdkName "javafx.base.jar" -Classifier $Classifier -BaseName "javafx-base"
    $graphics = Resolve-JavaFxJar -SearchDir $SearchDir -SdkName "javafx.graphics.jar" -Classifier $Classifier -BaseName "javafx-graphics"
    $controls = Resolve-JavaFxJar -SearchDir $SearchDir -SdkName "javafx.controls.jar" -Classifier $Classifier -BaseName "javafx-controls"

    if ($base -and $graphics -and $controls) {
        return @($base, $graphics, $controls)
    }

    return $null
}

function Rewrite-LauncherConfig {
    param(
        [string]$ConfigPath,
        [string]$JavaFxPathValue
    )

    if (-not (Test-Path $ConfigPath)) {
        throw "Could not find launcher config at '$ConfigPath'."
    }

    $lines = Get-Content -Path $ConfigPath
    $filtered = New-Object System.Collections.Generic.List[string]

    foreach ($line in $lines) {
        if ($line -like "app.classpath=*javafx-*") { continue }
        if ($line -eq "java-options=--module-path=$JavaFxPathValue") { continue }
        if ($line -eq "java-options=--add-modules=javafx.controls") { continue }
        [void]$filtered.Add($line)
    }

    $insertAt = [Array]::IndexOf($filtered.ToArray(), '[JavaOptions]') + 1
    if ($insertAt -le 0) {
        throw "Launcher config is missing a [JavaOptions] section."
    }

    $filtered.Insert($insertAt, 'java-options=--add-modules=javafx.controls')
    $filtered.Insert($insertAt, "java-options=--module-path=$JavaFxPathValue")
    Set-Content -Path $ConfigPath -Value $filtered -Encoding ascii
}

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $projectRoot

try {
    $appName = "Connect Four FX"
    $normalizedVersion = Normalize-Version -RawVersion $AppVersion
    $archiveVersion = if ([string]::IsNullOrWhiteSpace($AppVersion)) { $normalizedVersion } else { $AppVersion }

    if ([string]::IsNullOrWhiteSpace($JavaFxClassifier)) {
        if ($env:OS -eq "Windows_NT") {
            $JavaFxClassifier = "win"
        } elseif ([System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::OSX)) {
            $JavaFxClassifier = "mac"
        } else {
            throw "Could not infer a JavaFX classifier for this operating system."
        }
    }

    $platform = Get-PlatformInfo -Classifier $JavaFxClassifier
    $javac = Resolve-JavaCommand -JavaHomePath $JavaHome -CommandName "javac"
    $jar = Resolve-JavaCommand -JavaHomePath $JavaHome -CommandName "jar"
    $jpackage = Resolve-JavaCommand -JavaHomePath $JavaHome -CommandName "jpackage"

    $javaFxJars = $null
    foreach ($searchDir in @(
        $JavaFxLib,
        (Join-Path $projectRoot "javafx-sdk/lib")
    )) {
        $javaFxJars = Get-JavaFxJars -SearchDir $searchDir -Classifier $JavaFxClassifier
        if ($null -ne $javaFxJars) {
            break
        }
    }

    if ($null -eq $javaFxJars) {
        throw "Could not find JavaFX runtime jars for classifier '$JavaFxClassifier'. Set JAVAFX_LIB or place the jars in '$projectRoot\javafx-sdk\lib'."
    }

    $buildRoot = Join-Path $projectRoot "build/package"
    $classesDir = Join-Path $buildRoot "classes"
    $inputDir = Join-Path $buildRoot "input"
    $javaFxInputDir = Join-Path $inputDir "javafx"
    $distDir = Join-Path $projectRoot "dist"
    $appDir = Join-Path $distDir $platform.BundleName
    $zipPath = Join-Path $distDir ("{0}-{1}-{2}.zip" -f $appName, $archiveVersion, $platform.ArchiveSuffix)
    $modulePath = [string]::Join([System.IO.Path]::PathSeparator, $javaFxJars)

    Remove-Item -Path $buildRoot -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -Path $appDir -Recurse -Force -ErrorAction SilentlyContinue
    Remove-Item -Path $zipPath -Force -ErrorAction SilentlyContinue

    New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
    New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
    New-Item -ItemType Directory -Path $javaFxInputDir -Force | Out-Null
    New-Item -ItemType Directory -Path $distDir -Force | Out-Null

    & $javac --module-path $modulePath --add-modules javafx.controls -d $classesDir "ConnectFourFX.java"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Copy-Item -Path (Join-Path $projectRoot "connect-four.css") -Destination (Join-Path $classesDir "connect-four.css") -Force

    & $jar --create --file (Join-Path $inputDir "ConnectFourFX.jar") --main-class "ConnectFourFX" -C $classesDir .
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    foreach ($javaFxJar in $javaFxJars) {
        Copy-Item -Path $javaFxJar -Destination $javaFxInputDir -Force
    }

    & $jpackage `
        --type app-image `
        --dest $distDir `
        --input $inputDir `
        --name $appName `
        --app-version $normalizedVersion `
        --vendor "Westo" `
        --description "Desktop Connect Four with a JavaFX UI and AI opponent." `
        --main-jar "ConnectFourFX.jar" `
        --main-class "ConnectFourFX" `
        --java-options "--module-path=$($platform.JavaFxPathValue)" `
        --java-options "--add-modules=javafx.controls"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    if ($platform.Family -eq "windows") {
        $appJavaFxDir = Join-Path $appDir "app/javafx"
        $configPath = Join-Path $appDir "app/$appName.cfg"
    } else {
        $appJavaFxDir = Join-Path $appDir "Contents/app/javafx"
        $configPath = Join-Path $appDir "Contents/app/$appName.cfg"
    }

    New-Item -ItemType Directory -Path $appJavaFxDir -Force | Out-Null
    Copy-Item -Path (Join-Path $javaFxInputDir "*") -Destination $appJavaFxDir -Force
    Rewrite-LauncherConfig -ConfigPath $configPath -JavaFxPathValue $platform.JavaFxPathValue

    if ($platform.Family -eq "windows") {
        Compress-Archive -Path $appDir -DestinationPath $zipPath -Force
    } else {
        & ditto -c -k --sequesterRsrc --keepParent $appDir $zipPath
        if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    }

    Write-Host ""
    Write-Host "Packaged desktop app created at:"
    Write-Host $appDir
    Write-Host ""
    Write-Host "Portable zip created at:"
    Write-Host $zipPath
}
finally {
    Pop-Location
}
