$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/java.exe")) {
    $jdk = Get-ChildItem "$env:USERPROFILE/.gradle/jdks" -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path "$($_.FullName)/bin/java.exe" } | Select-Object -First 1
    if (-not $jdk) { throw 'Please install JDK 21 and configure JAVA_HOME.' }
    $env:JAVA_HOME = $jdk.FullName
}
$env:Path = "$env:JAVA_HOME/bin;$env:Path"
$env:SERVER_ADDRESS = '127.0.0.1'
if ($env:JAVA_TOOL_OPTIONS -notmatch 'jdk.net.unixdomain.tmpdir') {
    $env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS -Djdk.net.unixdomain.tmpdir=$env:SystemRoot\Temp".Trim()
}
if (-not (Test-Path 'db/getjobs.db')) {
    throw 'Missing db/getjobs.db. Download getjobs.db.template from Releases and rename it; see README.'
}
& ./gradlew.bat bootRun @args
exit $LASTEXITCODE
