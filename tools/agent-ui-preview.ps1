param([string]$JavaPath = 'C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot')

$ErrorActionPreference = 'Stop'
$clientRoot = Split-Path -Parent $PSScriptRoot
$fixtureRoot = Join-Path $clientRoot 'build\agent-ui-preview-fixture'
$fixturePackage = Join-Path $fixtureRoot 'cn\reverie\render\hud'
$initPath = Join-Path $clientRoot 'build\agent-ui-preview.init.gradle'
$utf8 = [System.Text.UTF8Encoding]::new($false)
$previousJdk = $env:JAVA_HOME
$previousPath = $env:PATH

Push-Location $clientRoot
try {
    if (!(Test-Path -LiteralPath (Join-Path $JavaPath 'bin\javac.exe'))) { throw 'Provide a JDK 25 path with -JavaPath.' }
    New-Item -ItemType Directory -Force -Path $fixturePackage | Out-Null
    $fixtureSource = @'
package cn.reverie.render.hud;
// Preview process only: real HUD registration requires a live Minecraft world.
// AgentScreen, NanoVG, Draw2D, fonts and input handlers are the production classes.
public final class HudRenderer { public HudRenderer() {} }
'@
    $fixtureFile = Join-Path $fixturePackage 'HudRenderer.java'
    [System.IO.File]::WriteAllText($fixtureFile, $fixtureSource, $utf8)
    & (Join-Path $JavaPath 'bin\javac.exe') -encoding UTF-8 -d $fixtureRoot $fixtureFile
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the isolated HUD fixture.' }
    $initSource = @'
allprojects {
    afterEvaluate {
        if (plugins.hasPlugin('java')) {
            tasks.register('agentUiPreview', JavaExec) {
                dependsOn tasks.named('testClasses')
                classpath = files('build/agent-ui-preview-fixture') + sourceSets.test.runtimeClasspath
                mainClass = 'cn.reverie.app.agent.ui.AgentUiPreview'
                javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
                jvmArgs '--enable-native-access=ALL-UNNAMED'
                systemProperty 'java.awt.headless', 'true'
            }
        }
    }
}
'@
    [System.IO.File]::WriteAllText($initPath, $initSource, $utf8)
    $env:JAVA_HOME = $JavaPath
    $env:PATH = (Join-Path $JavaPath 'bin') + ';' + $env:PATH
    & .\gradlew.bat -I $initPath agentUiPreview --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Agent UI rendering or input checks failed.' }
    Write-Output ('Screenshots: ' + (Join-Path $clientRoot 'build\agent-ui-preview'))
} finally {
    Pop-Location
    $env:JAVA_HOME = $previousJdk
    $env:PATH = $previousPath
}
