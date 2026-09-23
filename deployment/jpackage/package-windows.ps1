# Builds the Windows app image and .msi (ADR 0014, Tech Spec §1.12.5). Must run on Windows with a
# JDK 21 that ships jmods (Temurin does), cargo, and the WiX Toolset on PATH.
#
#   deployment/jpackage/package-windows.ps1 [-RunTests]
#
# Output: target/jpackage/ at the repository root.
#
# ADR 0014: the MSI registers no Windows service yet, and live acquisition does not work on Windows
# yet (POSIX-only secret file check and directory fsync). Tests are skipped by default here for
# the same reason; pass -RunTests once that slice lands.
param(
    [switch]$RunTests
)
$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true

$here = $PSScriptRoot
$root = (Resolve-Path (Join-Path $here '..\..')).Path
$out = Join-Path $root 'target\jpackage'
$name = 'observatorio-aps'
# Fixed forever: without it every build gets a random upgrade code and a new version installs
# side by side with the old one instead of upgrading it (§1.12.5 requires tested upgrades).
$upgradeUuid = 'b13c915b-dc96-4290-8c20-bdce7d42cbdd'

$javaHome = if ($env:JAVA_HOME) { $env:JAVA_HOME } else {
    Split-Path (Split-Path (Get-Command java).Source)
}
if (-not (Test-Path (Join-Path $javaHome 'jmods'))) {
    throw "JDK at $javaHome has no jmods\ - jlink needs a full JDK"
}

# 1. Backend jar, with the web client under static/ (-Pweb).
$pom = Join-Path $root 'apps\agent\pom.xml'
if ($RunTests) {
    mvn -B -f $pom -Pweb verify '-Dsurefire.reuseForks=false'
} else {
    mvn -B -f $pom -Pweb package '-DskipTests'
}
$version = (mvn -B -f $pom -q help:evaluate '-Dexpression=project.version' '-DforceStdout').Trim()
$jar = "esusdata-agent-$version.jar"

# 2. Execution plane (ADR 0010, ADR 0011).
cargo build --release --locked --manifest-path (Join-Path $root 'apps\execplane\Cargo.toml')

# 3. Staging: everything in input\ lands in $APPDIR (app\).
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
$staging = Join-Path $out 'input'
New-Item -ItemType Directory -Force $staging | Out-Null
Copy-Item (Join-Path $root "apps\agent\target\$jar") $staging
Copy-Item (Join-Path $root 'apps\execplane\target\release\observatorio-execplane.exe') $staging
Copy-Item (Join-Path $here 'windows\observatorio-aps.yml') $staging

# 4. Runtime. §1.12.5: module reduction comes after the smoke test, so link every JDK module
#    except the incubators.
$modules = (Get-ChildItem (Join-Path $javaHome 'jmods') -Filter *.jmod |
    ForEach-Object { $_.BaseName } |
    Where-Object { $_ -notlike 'jdk.incubator.*' }) -join ','
& (Join-Path $javaHome 'bin\jlink') --add-modules $modules `
    --strip-debug --no-header-files --no-man-pages --strip-native-commands `
    --output (Join-Path $out 'runtime')

# 5. App image, then the .msi built from it.
$description = 'Observatorio APS - servico local de indicadores sobre o PEC e-SUS'
& (Join-Path $javaHome 'bin\jpackage') --type app-image `
    --name $name `
    --app-version $version `
    --vendor 'Observatorio APS' `
    --description $description `
    --input $staging `
    --main-jar $jar `
    --runtime-image (Join-Path $out 'runtime') `
    --java-options '-Dobservatorio.install-dir=$APPDIR' `
    --java-options '-Dspring.config.additional-location=optional:file:$APPDIR/observatorio-aps.yml,optional:file:C:/ProgramData/ObservatorioAPS/config/' `
    --win-console `
    --dest $out

& (Join-Path $javaHome 'bin\jpackage') --type msi `
    --name $name `
    --app-version $version `
    --vendor 'Observatorio APS' `
    --description $description `
    --app-image (Join-Path $out $name) `
    --install-dir 'ObservatorioAPS' `
    --win-upgrade-uuid $upgradeUuid `
    --win-dir-chooser `
    --dest $out

Get-ChildItem (Join-Path $out '*.msi')
