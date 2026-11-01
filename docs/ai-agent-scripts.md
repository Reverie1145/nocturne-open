# AI Agent 与 JavaScript 脚本系统

入口：主菜单 **AI Agent**，ClickGUI 的 **Misc → AIAgent**，或游戏内 `.agent` / `.script`。

## 开始使用

1. 在「模型设置」选择 **API Key** 或 **ChatGPT 订阅 · OAuth**。API Key 模式支持 OpenAI Responses、兼容服务的 Chat Completions、Anthropic Messages；OAuth 模式见下节。
2. API Key 模式可在界面填写 Key（使用现有 AccountVault 加密存储），也可放入 `NOCTURNE_AI_API_KEY` 或自定义环境变量。环境变量优先；本地无鉴权模型可留空。
3. 在「Agent」描述任务，例如「创建一个每秒检查生命值、低于 8 时通知我的脚本」。模型会读取 API、检查代码、保存脚本，按需要继续调用工具。
4. 默认修改前确认。弹窗展示工具名和完整参数；允许只对当前调用有效。可在设置中选择只读或自动执行。
5. 在「脚本」选中文件，查看代码、声明能力并授予权限，保存后运行。AI 能声明所需能力，但没有授权工具。

API Key 模式中，服务地址填写 API 根路径（例如 `https://api.openai.com/v1`、`https://api.anthropic.com/v1`、`http://127.0.0.1:1234/v1`），也可以填写协议对应的完整请求 URL。远程服务必须用 HTTPS。不会跟随 HTTP 重定向。模型必须支持所选协议的工具调用；模型名由用户填写，不固定到某个供应商的模型。

Chat Completions 面向兼容服务，使用 `max_tokens`；需要 Responses 的 OpenAI 模型请选 Responses。Token 用量依据服务返回的 `usage`，轮数上限始终独立生效。每次用户提交任务会重新计算本次预算，会话累计用量另行保存。

## 使用 ChatGPT 订阅登录

1. 在「模型设置」选择 **ChatGPT 订阅 · OAuth**，点击 **Continue with ChatGPT**。
2. 系统浏览器打开 OpenAI 授权页。完成登录，并明确允许使用 ChatGPT 计划用量。身份登录和订阅权限分别检查。
3. 客户端验证回调与账户身份后，自动加载此账户的可用模型。选择模型后保存设置，即可在 Agent 和脚本的 `nocturne.ai.ask` 中使用。
4. **ChatGPT 账户** 可切换已保存的注册，**添加账户** 可登录另一个账号或工作区。**Continue with ChatGPT** 使用当前账户的已签发 Client ID 重新登录。
5. **Manage usage** 打开 ChatGPT 用量设置。达到订阅限制时暂停本次任务；OAuth 请求不会自动切换到 API Key 计费。

没有专用 Client ID 时，首次登录使用官方 `dynamic_agent_client` 注册入口，以 Nocturne 名称请求授权，保存回调签发的 Client ID；之后的代码交换、重新登录、刷新和撤销均使用该签发 ID。可在选填字段配置专用公共客户端 ID，无需提供或保存 client secret。每个账户保留独立身份、Client ID、令牌及有效期；退出时撤销可续期会话并清除本地令牌，保留账户映射供下次登录。远程撤销未确认时会明确提示。切换账户、模型或服务后，续接会话保留文字和工具记录，清除原服务专用的模型上下文；运行期间切换账户会停止后续请求。

**准入限制：** 当前官方订阅通道面向开源合作方及获准的私有客户端，并受账户、工作区和计划权限约束。此仓库的 `All-Rights-Reserved` 许可未修改；动态注册能否获准由 OpenAI 决定，集成代码不会绕过准入，也不保证任意订阅可用。以授权页和接口的实际结果为准。

OAuth 使用官方 `https://api.openai.com/v1/responses`，固定 `store:false`、`stream:true`，本地工具放在 `nocturne` namespace 中，不发送订阅通道不支持的 `max_output_tokens` 等字段。读取完成 / 未完成 / 错误终止事件后才处理模型结果；响应流中断时不执行其中的工具。单轮输出长度由服务决定，客户端仍检查任务总预算和轮数。

登录使用 PKCE S256、随机 state / nonce 和 `127.0.0.1` 临时端口回调；验证 OpenAI JWKS 签名、issuer、audience、有效期和 nonce。已有账户还检查 subject，验证失败不会替换原账户。凭据通过 AccountVault 加密、原子保存；续期使用文件锁与单次刷新队列，避免轮换令牌并发消费。取消 AI 请求会立即停止等待，已经发出的续期请求仍保存其替换凭据。临时网络错误保留登录，已确认失效的刷新令牌清除并提示重新登录。

官方依据：[注册与登录](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)、[账户与续期](https://developers.openai.com/siwc/token-sharing-open-source/profiles-and-sessions)、[模型与订阅推理](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)、[准入说明](https://developers.openai.com/siwc/quickstart)。

## 命令

| 命令 | 行为 |
| --- | --- |
| `.agent` / `.ai` | 打开工作区 |
| `.agent ask <任务>` | 打开工作区并开始任务 |
| `.agent stop` | 取消模型请求、待确认操作和未执行工具 |
| `.agent new` | 保存当前会话并新建会话 |
| `.agent status` | 查看状态、轮数和用量 |
| `.script list` | 列出脚本和运行状态 |
| `.script run <id>` | 运行脚本 |
| `.script stop <id>` | 停止脚本 |
| `.script stop-all` | 停止全部脚本 |
| `.script reload <id>` | 停止旧实例并从磁盘运行新实例 |
| `.script check <id>` | 检查语法，不执行代码 |
| `.script logs <id>` | 显示最近运行日志 |

编辑器支持行号、选区、复制粘贴、撤销重做、Unicode 输入、滚动；`Ctrl+S` 保存，脚本页 `Ctrl+Enter` 保存并运行，Agent 页 `Ctrl+Enter` 发送任务。切换脚本保留草稿；关闭前提示未保存文件。保存时检查磁盘版本，避免覆盖 Agent 或外部编辑器的改动。删除会停止脚本并移入本地 `trash`，记忆保留、授权撤销。

AI 保存脚本后，工作区在后台同步列表和编辑器。没有本地修改时自动显示新版本并保留光标；未保存草稿与磁盘版本同时变化时，保留双方并显示「冲突」。可在冲突窗口分别查看草稿和磁盘 / AI 版本，再选择「采用磁盘版本」或「保留草稿」。选择前备份双方；保留草稿后，下次保存将覆盖所查看的磁盘版本。磁盘文件被外部删除时，编辑器仍保留源码，明确选择保留后才允许重新保存。每个脚本保留最近 10 份冲突备份。保存期间如果磁盘再次变化，仍会拒绝覆盖并要求重新查看。

工作区采用 ClickGUI 的玻璃底板、白色细描边、圆角与动画速度设置；Dropdown 模式沿用 HUD 强调色，Window 模式沿用浅色强调色。标题、工具栏、代码、日志、任务和底部操作具有独立布局与裁切区，小窗口按比例适配。窗口开关、标签切换、悬停、按压和滚动提供过渡，动画中的鼠标坐标按实际绘制变换还原。任务清单和历史独立滚动，单行表单不拦截垂直滚轮，隐藏字段不接收输入，`Shift+Tab` 可反向切换字段。

Agent 的 `read_script` 按字符偏移分页，返回 `endOffset`、`hasMore` 和完整文件 hash。`write_script` 保存完整源码；`replace_script` 可精确替换长脚本中的唯一原文，两者都会检查磁盘 hash 和语法，保存不会自动运行，也不会改动用户授权。

工具结果超过 24000 字符时，返回有效 JSON 的存档说明（`resultState=archived`、`resultId`、`format`、完整 `hash` 和预览）。AI 用 `read_tool_result` 按 `endOffset` 读取全部 `text`，再按 JSON / TEXT 理解；原操作不会重复执行。存档在恢复会话后仍可读取，删除对话会一起清理。每个会话最多 32 个长结果、16 MB，单个存档最多 4 MB；旧结果被清理或存储失败时，明确提示重新读取实际状态，避免重复修改。`get_script_status` 另返回能力声明、实际授权、缺失授权和具体操作路径，AI 可据此结束已完成的创建工作并准确说明尚需授权的部分。

## 脚本文件

根目录位于游戏数据目录的 `nocturne/agent/`，与客户端账号、配置文件分开。

```text
agent/
  scripts/<id>.js          JavaScript 源码
  scripts/<id>.json        名称、描述、声明能力、自动启动
  grants/<id>.json         用户授予的能力（AI 没有写入入口）
  memory/<id>.json         脚本 JSON 存储
  sessions/<uuid>.json     Agent 会话、完整轨迹、任务清单、摘要
  tool-results/<会话id>/<结果id>.json  已执行工具的完整长结果
  draft-backups/<脚本id>/*.json  冲突选择前的草稿和磁盘备份，每个脚本最近 10 份
  module-backups/*.json    显式恢复损坏模块配置前的原文件备份
  agent-memory.md          Agent 的跨会话笔记
  settings.json            独立的 API / OAuth 配置和当前模式，不含 API Key
  model-catalogs/<hash>.json  按服务/账户隔离的模型目录缓存
  script-modules.json      ClickGUI 脚本模块、设置值、按键和启用状态
  api-key.enc              本地加密的 API Key
  openai-oauth.enc         加密的 ChatGPT 账户映射、令牌和稳定 host ID
  openai-oauth.lock        跨进程轮换令牌的互斥锁
  trash/                  被删除的脚本及元数据
```

首次初始化安装四个示例：`welcome`、`health-watch`、`ai-companion`、`vector-hud`。已有安装会单独添加新的 NanoVG 示例；安装标记防止之后删除示例又被自动恢复。示例默认不自动启动。

脚本模块配置总量最多 4 MB，创建前检查可持久化大小。模块写入共用顺序队列，短时间连续调整合并到最新状态；写入失败在下一次同步重试，退出时也按同一队列保存最终状态。损坏的模块 JSON 或无法恢复的记录会写入 `@modules` 日志，启动、同步与退出均保留原文件；之后显式创建或删除模块时，先把原文件保存到 `module-backups` 再写入恢复后的定义。

元数据格式：

```json
{
  "id": "health-watch",
  "name": "生命值提醒",
  "description": "低血量时显示本地通知",
  "capabilities": [],
  "autoStart": false
}
```

将 `.js` 文件放入 `scripts` 后点击刷新即可导入；没有 `.json` 时采用默认元数据。id 支持 1–64 个字母、数字、下划线或短横线，排除 Windows 保留名称。自动启动仅在元数据和授权均合法时成功；失败可查看日志。运行中保存代码不会替换旧实例，点击重载才启用新版本。

## JavaScript API

运行时为 Rhino 1.9.1，支持其 ES6 能力。没有 Node.js、`require`、Java 互操作或原始 Minecraft 对象。可在工作区「API 文档」复制 API 说明。

| API | 用途 / 所需能力 |
| --- | --- |
| `nocturne.on(event, callback)` / `off(handle)` | 注册 / 注销观察事件 |
| `nocturne.setTimeout(fn, ms)` / `setInterval(fn, ms)` / `clearTimer(handle)` | 计时器，最小 50 ms，通过客户端 tick 驱动 |
| `console.log/info/warn/error(...)`、`nocturne.log(text)` | 当前客户端运行日志 |
| `nocturne.notify(text)`、`nocturne.chat.print(text)` | 本地通知 / 聊天文字 |
| `nocturne.player.state()` / `inventory()` | JSON 玩家状态 / 背包 |
| `nocturne.world.entities({radius: 16})` | 附近实体，最多 64 格 / 64 个 |
| `nocturne.modules.list()` / `get(name)` | 模块和设置 |
| `nocturne.modules.setEnabled(name, bool)` / `setSetting(module, setting, value)` | 修改模块，需要 `modules` |
| `nocturne.chat.send(text)` | 服务器消息；`/` 为服务器命令，需要 `chat` |
| `nocturne.input.hold(key, ticks)` / `look(yaw, pitch)` / `release()` | 短时输入 / 朝向，需要 `controls` |
| `nocturne.storage.get(key, defaultValue)` / `set(key, value)` / `remove(key)` | 每脚本 64 KB 的持久 JSON 存储 |
| `nocturne.ai.ask(prompt, callback)` | 异步模型请求，回调 `(answer, error)`，需要 `ai` |
| `nocturne.render` / `nocturne.nvg` | NanoVG 路径、渐变、裁剪、变换、中文文字和省略号布局，无需额外能力 |

事件：`start`、`stop`、`tick {tick}`、`world {inGame}`、`key {key}`、`chat {text,direction}`、`packet {type,side}`、`render2d {width,height,pixelRatio,mouseX,mouseY,partialTicks,time,delta,screen,screenOpen,theme}`。它们是异步观察事件，不支持取消原版操作或直接修改包。聊天观察包含收到的服务器系统 / 玩家消息和发出的消息 / 命令，本地脚本输出不会递归触发聊天事件。

输入键：`forward`、`back`、`left`、`right`、`jump`、`sneak`、`sprint`。持续 1–200 tick；打开游戏界面时不施加输入；脚本停止、任务结束或世界切换时释放。输入通过现有 `MoveInputEvent` 合成，不修改物理键盘状态。数值设置会限制到模块合法范围；复杂颜色和布局设置在 ClickGUI 修改。

```javascript
var lastAlert = 0;
nocturne.on("tick", function (event) {
    if (event.tick % 20 !== 0) return;
    var player = nocturne.player.state();
    if (player.inGame && player.health < 8 && Date.now() - lastAlert > 10000) {
        nocturne.notify("生命值较低：" + player.health);
        lastAlert = Date.now();
    }
});
```

每脚本使用一个串行工作线程，最多 8 个运行实例、64 个监听器、64 个计时器。初始化/语法解析独立预算 2 秒，用户代码执行、render2d 分发和 API 边界检查使用 250 ms 上限；游戏线程请求最多等待 3 秒。实际绘图回调应尽量短以保持动画更新频率。事件队列有界，繁忙时合并 tick / 丢弃过量事件，绘制事件单独合并到最新输入，不阻塞游戏线程。源文件最多 256 KB。AI 请求每脚本最小间隔 5 秒，最多 2 个未完成请求。

这是进程内扩展运行时，隔离 Java / 文件系统能力并限制执行时间和 API 数据大小，不提供操作系统进程级的内存隔离。不要将 AI 回复自动 `eval`。运行日志是当前客户端的有界内存记录，Agent 会话轨迹则持久保存。

## 用 AI 创建完整 NanoVG 绘制

可以直接告诉 Agent：「创建一个与 ClickGUI 风格一致的玩家状态 HUD，用 NanoVG 绘制，包含平滑生命条、最近生命值图表和中文名称，再添加 Render 分类的开关。」模型会先读 `script_api({topic:"rendering"})`，使用详细的真实接口说明与完整示例，按现有流程创建、检查并保存脚本/模块。

「脚本」列表里的 **NanoVG 状态面板**（`vector-hud`）可直接运行，无需授予额外能力。进入世界后显示响应式面板；小窗口折叠图表，长名称使用实际字体宽度的省略号，主题颜色/圆角随 ClickGUI。完整源码见 [vector-hud.js](examples/vector-hud.js)，精确接口和完整绘图流程见 [NanoVG 脚本手册](nanovg-scripting.md)。

绘图命令只在 `render2d` 中使用，玩家状态与模块设置先在 `tick` 缓存。坐标使用事件提供的 Nocturne 画布单位，不再乘 GUI 缩放。工作线程生成不可变指令，渲染线程在已有 NanoVG 帧内执行；最新结果整体替换，停止/失败/换世界/改分辨率时清空，保存源码不会留下旧画面。

每帧最多 1024 指令、256 次填充/描边/文字、16 层状态、8192 字符及有界路径复杂度。支持形状、三种渐变、变换、裁剪、中文字体、自动换行和省略号；目前绘制通道是世界内 HUD，独立屏幕、鼠标点击接管、图片/物品纹理、背景模糊与 3D 绘制未提供。`get_script_status.render` 区分订阅、生成帧和实际 NanoVG 执行，游戏内画面仍需在真实环境检查。

## Agent 工作逻辑与参考对应

参考用户提供的 `C:/Users/24033/Downloads/claude-code-rev-main/claude-code-rev-main`。该目录 README 标明为重建源码，因此参考其可见的运行逻辑，并未直接依赖其 shim 或私有服务。

| 参考文件 | 本项目的对应行为 |
| --- | --- |
| `src/query.ts` 的 `queryLoop`、`needsFollowUp` | `AgentEngine` 使用真实工具调用决定是否继续；调用结果回填后重新采样 |
| `src/services/tools/toolExecution.ts` | 工具注册、严格参数检查、只读 / 确认 / 自动策略、错误作为结果回填 |
| `src/services/tools/StreamingToolExecutor.ts` | 完整调用批次中的每个 id 必有结果，取消后补齐未执行结果；游戏写操作串行执行 |
| `src/services/compact/grouping.ts` | 按完整 API 调用轮次切分，保留调用与结果配对，不独立截取工具结果 |
| `src/services/compact/autoCompact.ts` | 上下文达到配置阈值后生成摘要，保留近期完整轮次；摘要失败回退为有界执行记录 |
| `TaskCreateTool` / SessionMemory | 持久任务清单与显式跨会话笔记 |

状态包括思考、执行工具、等待确认、完成、取消、错误、达到上限。取消信号贯穿 HTTP、权限等待和游戏线程队列；游戏线程任务开始前检查取消、世界代次与当前授权，超时请求不会在之后继续执行。已经完成的修改不回滚，游戏输入会释放。恢复会话时修复未配对工具结果，避免把缺失结果误认为成功。

HTTP 请求在独立线程执行；429 / 5xx 最多重试两次，只重试模型采样，不重放已执行工具。Responses 保留加密 reasoning 项，Anthropic 保留原生内容块。设置 / 会话采用原子替换，持久字段使用 `SerializedName` 防止客户端混淆改变 JSON 格式。API Key 不进入工具目录、客户端状态或会话记录，接口错误会对 Key 脱敏。

实现没有引入操作系统 shell、任意文件访问、MCP 连接器或子 Agent；工具集中在客户端观察、模块、输入、脚本、日志、任务和笔记。API Key 和 OAuth 模式都逐段读取 SSE：Responses、Chat Completions、Anthropic Messages 各自合并文本与工具参数。界面立即显示 Markdown 回复和工具参数预览，只有完整完成事件才允许工具执行。兼容服务返回普通 JSON 时仍可正常解析。取消或断流保留可见的部分输出，但不把未完成工具加入后续模型上下文。

协议依据：[OpenAI Function calling](https://developers.openai.com/api/docs/guides/function-calling)、[Anthropic 工具定义](https://platform.claude.com/docs/en/agents-and-tools/tool-use/define-tools)、[Rhino 1.9.1 发布说明](https://github.com/mozilla/rhino/releases/tag/Rhino1_9_1_Release)。

## 连接持久化、模型和消息界面

API Key 和 ChatGPT OAuth 各保存一套独立配置：地址、协议、模型、预算和指令不会互相覆盖。切换连接前保存当前表单，模型选择立即保存；旧版单套 `settings.json` 会自动迁移。API Key 保存在 `api-key.enc`，OAuth 登录及所选账户保存在 `openai-oauth.enc`；重启后沿用登录并按到期时间刷新令牌。

启动、保存连接、登录或切换账户时自动加载模型列表。目录按服务地址/协议/凭据或 ChatGPT 账户隔离并缓存，不在缓存中保存凭据。模型弹窗可以搜索显示名称和模型 ID。API 服务没有 `/models` 时会显示原因，并保留手填模型入口；不会影响已经选定模型的调用。

对话使用原生 NanoVG Markdown 渲染，支持标题、粗体、斜体、删除线、行内代码、带语言的代码块、列表/任务项、引用、表格和可点击的 HTTP(S) 链接；HTML 作为文本展示。代码带语法配色和复制按钮，每条消息可复制或查看完整原文。工具有独立卡片，显示生成参数、执行、等待授权、完成、失败等状态，可以展开参数和结果。翻阅较早消息时，流式追加不会强制滚回底部。

「历史」中可以删除指定对话。删除当前对话会创建空白会话，已生成脚本和模块继续保留。运行中的任务需先停止才能删除历史。

OAuth 空响应修复：原实现只取完成事件里的 `response.output`。现在同时合并 `response.output_text.delta/done`、内容块、输出项和函数参数，再与终止响应去重；即使终止事件仅给出状态和 usage，也能保留已收到的正文和工具。订阅请求仍按[官方模型与推理规范](https://developers.openai.com/siwc/token-sharing-open-source/models-and-inference)使用 public Responses、`store:false` 和 `stream:true`。

## 创建脚本和 ClickGUI 模块

Agent 可以调用 `create_script` 直接生成并保存 JavaScript，使用 `validate_script` 检查语法，然后用 `create_module` 把脚本注册到 ClickGUI。新增接口还包括 `update_script_metadata`、`read_script_storage`、`list_script_modules`、`remove_module` 和 `set_keybind`。这些修改仍遵守用户选择的确认/只读/自动策略。

脚本模块和内置模块使用相同分类、开关、按键和设置组件，支持 boolean、number、mode、string 四类可调设置。开启运行脚本，关闭停止，启动失败或运行错误会关闭模块；从脚本页面启动/停止也会同步开关。下拉和窗口两种 ClickGUI 布局均刷新动态模块列表。模块定义、设置、绑定和启用状态保存到 `script-modules.json`，重启后恢复并检查脚本授权。

一个脚本绑定一个模块，最多 32 个脚本模块，每个最多 24 项设置。删除模块会停止脚本并保留源码；删除脚本后对应模块自动移除。模块运行仍计入最多 8 个并发脚本限制。

```javascript
// 通过 Agent create_module 或已获 modules 权限的脚本注册：
// { id:"health-module", script:"health-watch", name:"Custom Health",
//   description:"低血量提醒", category:"PLAYER",
//   settings:[{name:"Threshold",type:"number",value:8,min:1,max:20,step:1}] }
var lastAlert = 0;
nocturne.on("tick", function (event) {
    if (event.tick % 20 !== 0) return;
    var module = nocturne.modules.self();
    var threshold = module.settings.filter(function (s) {
        return s.name === "Threshold";
    })[0].value;
    var player = nocturne.player.state();
    if (player.inGame && player.health < threshold && Date.now() - lastAlert > 10000) {
        nocturne.notify("生命值较低：" + player.health);
        lastAlert = Date.now();
    }
});
```

脚本中新增 `nocturne.modules.self()`、`listCustom()`、`create(options)`、`remove(id)` 和 `setKey(name,key)`。写操作需要声明并获得 `modules` 权限，脚本只能创建或移除自己拥有的模块。`create` 在脚本重启时复用已有绑定并保留用户调整的设置；首次创建更适合由 Agent 的 `create_module` 完成。

## 提供给 AI 的详细说明与上下文

每轮请求自动附带系统工作流程和完整的 JavaScript 手册，包含实际签名、返回结构、字段含义、数值/长度限制、权限、生命周期、排错和可执行示例。模型不必先猜测有 `script_api` 才知道客户端 API；小上下文按完整章节缩减内联内容，仍可通过 `script_api` 的 `topic` 读取 all/index/runtime/events/rendering/state/modules/interaction/storage/ai/workflows/examples。界面的 API 文档使用同一份手册。

每个工具有操作条件、返回契约和完整参数 Schema。`create_module.settings` 明确定义 boolean/number/mode/string 四种结构，写入前递归检查必需字段、类型、枚举、长度、范围和未知字段。工具 schema 与实际执行权限一起发送，避免模型只看到一个笼统的 object。

真实工作区快照自动包含玩家/屏幕状态、模块摘要、脚本 manifest、status、grants、missingGrants 和用户笔记，不读取连接凭据。`client_context` 可以刷新；快照截断时明确标记详情入口。`list_scripts`、`list_modules`、`list_script_modules` 返回带 items/offset/endOffset/total/hasMore 的分页对象；`script_logs` 默认最近 30 条，hasEarlier 提示旧日志，显式 offset 可读旧页。`read_script_storage` 分页返回 JSON 文本，各页 json 拼接后为完整存储对象。

附带选中脚本现在包含编辑器草稿的真实快照。`read_attached_script` 分页读取包含未保存修改的源码，`read_script` 则读取磁盘版本，两者 hash 用途明确区分。16,000 字符的任务不会因为追加附件说明而被拒绝。`get_script_status` 可以核实启动状态和错误，手册要求区分语法通过、文件保存、启动成功和游戏效果。

发送任务前自动保存当前模型表单，缺少模型或订阅授权时转到设置页并保留任务文本。自动选择模型会避开目录中明显用于嵌入、语音、图像等用途的产品，用户仍可手动选择。

详细手册和工具 schema 会占用输入 Token；默认任务预算调整为 180,000，界面仍显示并允许修改这个硬上限。旧版同时使用默认轮数、输出、上下文、超时和 60,000 预算的配置首次升级到新默认；已有自定上限保留。版本标记防止以后将用户明确选回的 60,000 再次升级。预算仍统计服务返回的实际用量，未绕过上限。

完整内容见 [实际提供给 AI 的说明与工具契约](agent-model-context.md)。该文档包含当前系统指令、10 个 API/工作流章节和每个工具的详细返回说明；`tools/export-agent-context.ps1` 同时导出完整绘图手册及可运行示例源码。

## 验证

```powershell
.\gradlew.bat test --tests 'cn.reverie.app.agent.*' --console=plain
.\gradlew.bat test jar --console=plain
.\tools\agent-ui-preview.ps1
.\tools\script-render-preview.ps1
```

新增检查覆盖真实 JavaScript 的生命周期、计时器、异步 AI 回调、语法检查不执行、超时不可捕获、启动时取消、Java / Node 入口隔离、能力授权、存储配额、工具错误与批次配对、确认 / 拒绝 / 取消、预算、摘要、恢复、编辑器 Unicode / 撤销，以及三个协议的真实本地 HTTP 往返和 Key 脱敏。测试使用本地模拟模型服务，不需要或使用用户的真实 Key。真实供应商模型仍需在运行客户端后配置验证。新增回归覆盖三个协议在结束前收到流式文本、UTF-8 跨包、稀疏终止响应、拒绝内容、多个工具参数、断流不执行工具及部分输出保存。

OAuth 检查使用本地授权服务器和真实 RSA 签名，覆盖 state / PKCE / ID Token 验证、授权拒绝、账户隔离、加密恢复、退出撤销、跨客户端轮换刷新、取消后保存刷新结果、临时与终止错误、SSE 工具调用闭环、响应流中断和禁用 API Key 回退。未使用或读取用户真实 OpenAI 凭据；新增测试也覆盖恢复登录后刷新账户模型目录，以及仅通过 delta 发出正文、终止事件 output 为空时的完整订阅调用。真实账户的服务端响应仍需游戏内复测。

模块检查覆盖真实 JavaScript 启停、可调设置与按键恢复、权限不足、所有权、重复注册、停止幂等、陈旧回调和移除后禁止再次启动；连接检查覆盖旧配置迁移、两套配置独立恢复、模型缓存、历史删除和运行中删除限制。

绘图检查执行真实 Rhino 脚本与完整手册示例，覆盖 RGBA/渐变普通数据、队列合并、最新画布、空帧替换、停止/取消监听、运行中的世界/分辨率清理、迟到帧拒绝、状态栈/变换/路径配额、禁止渲染中请求客户端或写盘、异常与死循环不可捕获。语法错误在脚本工作线程返回 FAILED，错误前缀不会执行；初始化与编译不占用用户代码预算。

`script-render-preview.ps1` 使用独立隐藏 OpenGL 窗口、生产指令桥接和 MiSans/UI/Consolas 字体，验证全部 44 个绘制命令（另有 RGBA 与三个渐变工厂），输出 `build/script-render-preview/*.png`。10 个场景覆盖完整绘制、低/零血量、长中文名称与省略号、小窗、高 DPI、ClickGUI 隐藏、分辨率恢复和停止；同时验证客户端裁剪、变换、颜色与 OpenGL 状态。使用临时文件和合成玩家数据，无游戏/模型网络；这不能替代真实世界内的完整客户端验证。

界面检查覆盖小窗、竖向和超宽画布的边界与控件互不重叠、弹窗内容与操作区、动画坐标逆变换、不同帧率下的滚动、Unicode 裁切、日志选区，以及单行字段的滚轮传递。离屏预览使用实际 AgentScreen、NanoVG、Draw2D 和字体，在隐藏 OpenGL 窗口中输出 `build/agent-ui-preview/*.png`，同时检查 OpenGL 错误、当前有效点击区、动画中的点击、文本输入、弹窗隔离、拒绝审批及 Tab / Shift+Tab。数据与凭据为临时测试数据，HUD 注册使用隔离夹具；不启动完整 Minecraft，也不发送模型请求。实际游戏场景中的背景模糊及生命周期仍需实机验证。预览脚本默认使用本机 JDK 25，可通过 `-JavaPath` 指定另一安装路径。
