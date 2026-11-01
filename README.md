# Nocturne

一个基于 **Fabric** 的 Minecraft 客户端（Kotlin + Java 混合），目标版本 `26.2`，loader `0.19.5`。
界面用 NanoVG 全自绘（点击 GUI、HUD、主菜单、动态岛、聊天栏），内置 100+ 功能模块、脚本系统、
云音乐播放器、AI Agent 面板，以及一套完整的 **native 加壳 + 字节码混淆** 发布管线。

> 本仓库是源码版。发布版 jar 由 `protectedBuild` 生成，加壳与混淆链路在发布时全部生效。

---

## 快速开始

### 环境要求

| 项 | 要求 |
| --- | --- |
| JDK | **25**（`java.toolchain` 固定为 25，`JAVA_HOME` 指向它即可） |
| Gradle | 无需安装，用仓库自带 `./gradlew`（wrapper 9.5.0） |
| 系统 | 开发/构建：Windows / Linux / macOS 均可 |
| 发布加壳 | **仅 Windows**（MSVC x64 + CMake + `NOCTURNE_APP_SECRET`） |

### 开发运行

```bash
./gradlew runClient
```

开发环境（`FabricLoader.isDevelopmentEnvironment()`）会自动跳过两件事：

1. **native 加壳**：`PackBoot` 直接返回，不加载 DLL、不还原打包类；
2. **账号校验**：`NocturneAuth` 视为已登录，不连后端。

原因很直接：开发类路径里既没有编译好的 native 镜像，也没有 `assets/nocturne/c/` 下的加密类体，
账号后端也不该给每个 clone 的人开号。发布 jar 走的是完整链路，一行不少。

### 构建

```bash
./gradlew build              # 普通 jar，加壳链路不参与
./gradlew protectedBuild     # 完整发布：remap -> 混淆 -> 打包类体 -> 内嵌 native
```

`protectedBuild` 需要：

```bat
set NOCTURNE_APP_SECRET=<你的 app secret>
gradlew protectedBuild
```

产物在 `build/libs/nocturne-<version>-protected.jar`。

---

## 保护管线（并不强）

发布版不是把 class 直接塞进 jar，而是经过一条四段式流水线。所有密钥都只在 native 镜像里推导，
jar 里拿不到任何可解密的材料。

### 1. 字节码混淆 —— `buildSrc/src/main/java/cn/reverie/obf`

| Pass | 作用 |
| --- | --- |
| `RenamePass` / `RenamePassVerifier` | 按 `obf.json` 的包白名单做重命名，`glyph` 命名风格，保留类/成员白名单；改完再校验一遍引用完整性 |
| `FlowPass` / `FlowPassSupport` | 控制流平坦化：把方法体拆成基本块，用状态机 switch 串起来 |
| `StringPass` / `PoolGen` | 字符串加密：明文常量抽成 ciphertext pool，运行期由 `StringVault` 解密，`stringPools` 分池 |
| `NumberPass` | 数值常量编码（异或/加减），去掉字节码里的立即数指纹 |
| `RefPass` | 反射/调用点改写，抹掉直接引用关系 |
| `BsmGen` | 生成 invokedynamic 的 bootstrap，让调用点走合成的方法句柄 |
| `TrapPass` | 插入恒假分支/无效异常处理，干扰反编译器的结构还原 |
| `IntegrityPass` | 在 `nocturneClient.onInitializeClient()V` 上挂完整性自检，`sample` 抽样校验 |
| `PackerPass` / `MemCompile` | 把敏感类整体加密成 `assets/nocturne/c/<idx>.dat`，`index.bin` 存类名清单 |
| `StripPass` / `VerifyPass` | 剥离调试信息，最后统一验证产物 |

配置全部集中在 `obf.json`：重命名范围、白名单、混淆种子、字符串池数量、是否打包、完整性挂钩位置。

### 2. Native 层 —— `native/`

```
native/core/     nocturne_core.dll   实际持有密钥与解密逻辑（app secret / pack key / session key / HMAC）
native/loader/   nc_loader.dll       手动映射器，把 core 从内存映像起起来，不留磁盘痕迹
native/nc_obf.h                      编译期字符串混淆宏（NC_OBF("...") 在编译期做异或 + 拆解）
```

`NBootstrap.bind(core, guardClass)` 是唯一的 JNI 出口，名字被写死在 loader 里所以**不能重命名**
（`obf.json` 的 `keepClasses` 里有它）。

### 3. 启动链

```
nocturneClient class-init   -> ProtectionSplashWindow.show()   弹出启动窗
onInitializeClient()        -> PackBoot.launch()
                               ├─ NativeGuard.ensureInit()     加载 DLL，等 core 注册 natives
                               ├─ 读 assets/nocturne/c/index.bin
                               └─ 逐类 AES 解密 -> DecryptingClassLoader.define()
                             -> ProtectionSplashWindow.complete()
```

打包类在明文类路径里**不可达**，只有 `DecryptingClassLoader` 能定义它们，所以即便 dump 内存也只见密文。
进度条是真的：`ProtectionSplashWindow.report()` 由上述每一步实际完成度驱动，不是定时器。

### 4. 反调试

`NativeGuard` 之外，core 侧带 PEB/调试器检查、硬件断点扫描、模块校验与定时自检，
被 hook 或断下时走 `nc_obf.h` 里的失败路径。

---

## 功能模块

`cn.reverie.module` 下分六类：

- `combat` — 战斗（KillAura、Velocity、Backtrack、Criticals、AutoClicker、AimAssist 等）
- `movement` — 移动（Speed、Fly、NoSlow、Step、Scaffold 等）
- `player` — 玩家（AutoTool、AutoEat、InvManager、ChestStealer、各类告警等）
- `render` — 渲染（ESP、NameTags、HUD 组件、ChatBar、动态岛等）
- `world` — 世界（Ambience、HumanTelly 等）
- `misc` — 杂项（Disabler、AutoReconnect、脚本、反检测等）

配套：

- `app/command` — `/` 指令系统（含别名、补全、参数解析）
- `app/config` — 配置档案（多 profile、导出/分享）
- `app/agent` — AI Agent 面板，支持多账户/多模型切换
- `app/music` — 云音乐（mp3/flac 解码、歌词、二维码登录）
- `render/` — NanoVG 渲染层（字体、着色器、动效、模糊、视频背景）
- `mixin/` — 对 Minecraft 的注入点，统一走 `manager/*HookManager` 转发

---

## 目录

```
buildSrc/                混淆器（独立 Gradle 子工程）
docs/                    架构文档（依赖边界、脚本 API、Human Telly 等）
native/                  C++ 加壳核心 + 手动映射 loader
src/main/java/           mixin 与少量 Java 工具
src/main/kotlin/         主体（client / manager / module / render / gui / app）
src/main/resources/      资源（字体、着色器、视频、图标）
```
---

## 协议

**GPL-3.0**。仓库内 `src/main/java/com/viaversion/viafabricplus/` 是 Vendored 的
ViaFabricPlus 5.0.1 源码（GPL-3.0），因此本项目整体以 GPL-3.0 发布，见 `LICENSE`
与 `LICENSE-ViaFabricPlus.txt`。

## 免责

本项目仅供学习与研究 Minecraft 客户端实现、字节码混淆与 native 保护技术使用。
在多人服务器使用前请自行确认该服务器规则；因使用本项目产生的任何后果由使用者自行承担，
作者不提供任何担保。
