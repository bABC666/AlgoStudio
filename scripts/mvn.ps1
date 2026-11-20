# Use unbound arguments so Maven's -o/-e options are not PowerShell common parameters.
$MavenArguments = @($args)

$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$previousLocation = Get-Location
$previousJavaHome = $env:JAVA_HOME
$previousMavenHome = $env:MAVEN_USER_HOME
try {
    Set-Location -LiteralPath $projectDirectory
    $localJdk = Get-ChildItem -LiteralPath '.tools' -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending | Select-Object -First 1
    if ($null -ne $localJdk) { $env:JAVA_HOME = $localJdk.FullName }
    if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME/bin/javac.exe")) {
        throw '请设置 JAVA_HOME 为 JDK 21；也可将 JDK 21 解压到项目 .tools 目录。'
    }
    $jdkRelease = Get-Content -LiteralPath "$env:JAVA_HOME/release" -Raw
    if ($jdkRelease -notmatch 'JAVA_VERSION="21(?:\.|\")') {
        throw '本项目要求 JDK 21。请设置 JAVA_HOME，或将 JDK 21 解压到项目 .tools 目录。'
    }
    $env:MAVEN_USER_HOME = Join-Path $projectDirectory '.tools/maven-home'
    if (-not $MavenArguments) { $MavenArguments = @('test') }
    & (Join-Path $projectDirectory 'mvnw.cmd') @MavenArguments
    $mavenExitCode = $LASTEXITCODE
} finally {
    $env:JAVA_HOME = $previousJavaHome
    $env:MAVEN_USER_HOME = $previousMavenHome
    Set-Location -LiteralPath $previousLocation
}
exit $mavenExitCode
