param([string]$OutputPath = '')

$ErrorActionPreference = 'Stop'
$clientRoot = Split-Path -Parent $PSScriptRoot
if (!$OutputPath) { $OutputPath = Join-Path $clientRoot 'docs\agent-model-context.md' }
$knowledge = Get-Content -LiteralPath (Join-Path $clientRoot 'src\main\kotlin\cn\reverie\app\agent\AgentKnowledge.kt') -Raw
$contracts = Get-Content -LiteralPath (Join-Path $clientRoot 'src\main\kotlin\cn\reverie\app\agent\AgentToolDocumentation.kt') -Raw

function Remove-CommonIndent([string]$Value) {
    $lines = $Value.Trim("`r", "`n").Replace("`r`n", "`n").Split("`n")
    $indent = ($lines | Where-Object { $_.Trim().Length -gt 0 } | ForEach-Object { $_.Length - $_.TrimStart().Length } | Measure-Object -Minimum).Minimum
    return (($lines | ForEach-Object { if ($_.Length -ge $indent) { $_.Substring($indent) } else { '' } }) -join "`n")
}

$instructions = [regex]::Match($knowledge, '(?s)val instructions = """(.*?)"""\.trimIndent\(\)')
$pages = [regex]::Matches($knowledge, '(?s)"([a-z]+)" to """(.*?)"""\.trimIndent\(\)')
$tools = [regex]::Matches($contracts, '(?m)^\s*"([a-z_]+)" to ("(?:\\.|[^"\\])*")')
if (!$instructions.Success -or $pages.Count -eq 0 -or $tools.Count -eq 0) { throw 'Agent source layout changed; no documentation was overwritten.' }

$intro = @'
# 实际提供给 AI 的说明与工具契约

由 `tools/export-agent-context.ps1` 从当前 `AgentKnowledge.kt` 和 `AgentToolDocumentation.kt` 导出。正常上下文自动内联完整脚本手册；小上下文保留完整章节，其余通过 `script_api({topic:...})` 读取。

每轮还提供真实工作区快照：玩家、屏幕、模块摘要、脚本元数据、运行状态、已授予与缺失能力、用户笔记。省略内容带 hasMore/truncated 和详情入口。用户开启「附带选中脚本」时，AI 可用 read_attached_script 读取不可变的未保存草稿，另用 read_script 核实磁盘与 hash。这里不列用户游戏数据或凭据。

工具名、下面的完整说明以及参数 JSON Schema 一起发送；模块设置 schema 分别定义 boolean/number/mode/string 的必需字段、类型和范围。超长结果保留完整存档并通过 read_tool_result 分页读取，预览不代表完整结果。

## 系统行为指令
'@
$sections = [System.Collections.Generic.List[string]]::new()
$sections.Add($intro.Replace("`r`n", "`n").Trim())
$sections.Add([regex]::Replace((Remove-CommonIndent $instructions.Groups[1].Value), '(?m)^# ', '### '))
$sections.Add('## JavaScript API 手册')
foreach ($page in $pages) { $sections.Add((Remove-CommonIndent $page.Groups[2].Value)) }
$sections.Add('## Agent 工具契约')
foreach ($tool in $tools) {
    $text = ConvertFrom-Json -InputObject $tool.Groups[2].Value
    $sections.Add('### ' + $tool.Groups[1].Value + "`n`n" + $text)
}
[System.IO.File]::WriteAllText([System.IO.Path]::GetFullPath($OutputPath), ($sections -join "`n`n") + "`n", [System.Text.UTF8Encoding]::new($false))
Write-Output ("Exported {0} API chapters and {1} tool contracts to {2}" -f $pages.Count, $tools.Count, $OutputPath)
$rendering = $pages | Where-Object { $_.Groups[1].Value -eq 'rendering' } | Select-Object -First 1
if ($rendering) {
    $renderingText = Remove-CommonIndent $rendering.Groups[2].Value
    $renderingPath = Join-Path $clientRoot 'docs\nanovg-scripting.md'
    [System.IO.File]::WriteAllText($renderingPath, "# Nocturne NanoVG 脚本手册`n`n由实际 Agent 手册自动导出；AI 与客户端 API 文档使用相同内容。`n`n" + $renderingText + "`n", [System.Text.UTF8Encoding]::new($false))
    $example = [regex]::Match($renderingText, '(?s)```javascript\n(.*?)\n```')
    if (!$example.Success) { throw 'The complete render example was not found.' }
    $examplePath = Join-Path $clientRoot 'docs\examples\vector-hud.js'
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $examplePath) | Out-Null
    [System.IO.File]::WriteAllText($examplePath, $example.Groups[1].Value + "`n", [System.Text.UTF8Encoding]::new($false))
    Write-Output ('Exported NanoVG manual and runnable example: ' + $renderingPath)
}
