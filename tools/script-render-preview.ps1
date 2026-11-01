param([string]$JavaPath = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot')

$ErrorActionPreference = 'Stop'
$clientRoot = Split-Path -Parent $PSScriptRoot
$initPath = Join-Path $clientRoot 'build\script-render-preview.init.gradle'
$previousJdk = $env:JAVA_HOME
$previousPath = $env:PATH
$initSource = @'
allprojects {
    afterEvaluate {
        if (plugins.hasPlugin('java')) {
            tasks.register('scriptRenderPreview', JavaExec) {
                dependsOn tasks.named('testClasses')
                classpath = sourceSets.test.runtimeClasspath
                mainClass = 'cn.reverie.app.agent.script.ScriptRenderPreview'
                javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
                jvmArgs '--enable-native-access=ALL-UNNAMED'
                systemProperty 'java.awt.headless', 'true'
            }
        }
    }
}
'@
Push-Location $clientRoot
try {
    if (!(Test-Path -LiteralPath (Join-Path $JavaPath 'bin\java.exe'))) { throw 'Provide a JDK 25 path with -JavaPath.' }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $initPath) | Out-Null
    [System.IO.File]::WriteAllText($initPath, $initSource, [System.Text.UTF8Encoding]::new($false))
    $env:JAVA_HOME = $JavaPath
    $env:PATH = (Join-Path $JavaPath 'bin') + ';' + $env:PATH
    & .\gradlew.bat -I $initPath scriptRenderPreview --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Native script drawing checks failed.' }
    Write-Output ('Screenshots: ' + (Join-Path $clientRoot 'build\script-render-preview'))
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJdk
    $env:PATH = $previousPath
}
