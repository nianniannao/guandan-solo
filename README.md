# 掼蛋 · 五行单机/联机版（Guandan Solo）

一款纯 Java、零第三方依赖的安卓掼蛋应用：国标江苏掼蛋规则，三档 AI，五行八卦视觉，支持安卓 ↔ 鸿蒙局域网联机。

- 包名：`com.guandan.solo`
- 版本：8.8（versionCode 20）
- 最低系统：Android 5.0（minSdk 21），targetSdk 34
- 体积：约 470 KB（含 54 段离线语音素材）
- 许可：GNU GPL v3.0（见 LICENSE）

## 功能

**规则**
两副牌 108 张，逢人配（红桃级牌为万能牌，可代任意牌但不可代王），单张 / 对子 / 三张 / 三带二 / 顺子（5 张）/ 三连对（6 张）/ 钢板（6 张）/ 同花顺 / 炸弹（4~8 张）/ 天王炸。升级双上 3 级、一三 2 级、一四 1 级，上限打 A。牌力：2~A < 级牌 < 小王 < 大王。

**三档 AI**
统一打分函数（拆牌收益 + 压制价值 − 炸弹代价 − 拆炸/拆配惩罚 + 队友协同），难度差异见下：

| 难度 | 策略 | 说明 |
|---|---|---|
| 初级 · 木 | 能小压就压、优先散牌、炸弹不轻动 | 新手手感 |
| 高级 · 土 | 完整打分选优、队友领出主动让牌、炸弹择机 | 会配合 |
| 终极 · 水 | 打分 + 残局蒙特卡洛（≤16 张起对前 5 个候选各模拟 3~14 次） | 残局最强 |

**思考强度（按难度独立设置）**
每个难度都可选 快 / 标准 / 深度，对应残局模拟 3 / 8 / 14 次（高级深度档启用 6 次残局推演），并影响出牌停顿（0.3 / 0.8 / 1.4 秒）。设置存于 `SharedPreferences`（`think_0` / `think_1` / `think_2`）。

**语音**
内置 54 段离线中文语音（edge-tts 合成，随 APK 打包，不依赖系统 TTS）。播报口径：首家出牌只报牌型、单张报点数；跟牌用「大你 / 管住 / 炸你」；过牌用「要不起」；多套说法随机切换。顶部「声音 开/关」总开关，状态持久化。播放按顺序串行，上一句播完才走下一家。

还可用系统 TTS 兜底（如设备无中文引擎，建议安装开源的 RHVoice）：

1. 在 F-Droid 中搜索并安装 `RHVoice`；
2. 在 RHVoice 内下载「中文（普通话）」语音包；
3. 系统设置 → 无障碍 / 文字转语音输出 → 首选引擎选择 RHVoice（并下载语音数据）；
4. 回到本应用，点右上角「声音 开」即可正常播报。

也可以在系统设置中改用其他支持中文的引擎（如 Google 文字转语音、讯飞等）；本应用启动时会自动遍历已安装引擎并选择支持中文的那个。

**存档**
每步落盘（`progress_v1`），启动时提示「继续上一局」，首页另有续局入口；结算 / 新局 / 联机时自动清档。

**局域网联机**
- TCP 48620 传输牌局状态（行分隔 JSON），UDP 48621 广播发现（`GD?` / `GD!房间名|端口|房号`）
- 房主分配座位，90 秒座位保留，断线自动重连
- 22 秒出牌超时由房主托管
- 出牌节奏 2/3/5/6 秒可调（右上角「节奏」按钮，默认 3 秒）
- 协议见 `harmony/PROTOCOL.md`

**鸿蒙版**
`harmony/` 为完整 ArkTS 工程（需 DevEco Studio 5 编译 .hap），规则内核、打分 AI、发现与重连逻辑与安卓同构，可与安卓版互通。

## 构建

方式一：Android Studio 打开本仓库直接 Build（compileSdk 34 / minSdk 21）。
方式二：命令行无 Gradle 打包（需 JDK 8+ 与 Android SDK）：

```bash
ANDROID=/path/to/android-sdk ./build.sh
# 产物：guandan.apk
```

签名示例：

```bash
apksigner sign --ks release.keystore --out guandan.apk app-release-unsigned.apk
```

## 目录

```
app/src/main/java/com/guandan/solo/   源码（14 个类）
  Card / Play / Rules                 牌与牌型判定
  Engine                              对局状态机与升级结算
  AI / AIOld / AINew                  三档 AI（默认走 AIOld + 新候选牌型生成与记牌控场）
  GameView                            自绘牌桌与全部交互
  LocalVoice / Voice                  离线素材串行播放，系统 TTS 兜底
  Net / Json                          联机与协议
  Save                                进度存档
app/src/main/res/                     图标与主题
harmony/                              鸿蒙 ArkTS 工程
tools/selftest/                       自博弈与强度对照用例（main 方法，非 JUnit）
fdroid/com.guandan.solo.yml           F-Droid 元数据
fastlane/                             商店元数据（标题、简介、截图）
```

## 自测

`tools/selftest` 里的类可直接用 `java` 运行（无需测试框架）：

```bash
javac -cp <android.jar> -d out $(find app/src/main/java -name '*.java') tools/selftest/*.java
java -cp out:tobj AiTest      # 120 局自博弈：无死循环 / 单步耗时
java -cp out:tobj Strength    # 难度对照赛
java -cp out:tobj SaveTest    # 存档编解码回归
```

实测（云主机 JVM）：120 局 / 13,647 个动作全部正常结束，单步最慢 57ms；难度对照 150 局一组，终极·深度 vs 初级·快 93%，高级·深度 vs 初级·标准 90%，终极·深度 vs 高级·标准 67%。v8.0 AI 改写后对 v7.2 基线做 300 局对照两轮，胜率 57% / 58%。

## 语音素材说明

`app/src/main/assets/voice/` 下为 54 段 edge-tts 合成的中文语音（约 490 KB），键名带 `n_` 前缀以避免与历史版本同名文件冲突；`index.txt` 记录每个键对应的毫秒时长，供串行播放与节奏控制使用。素材可用 `gen_voice.py` 重新生成（需安装 edge-tts）。

## 许可

GPL-3.0-or-later。代码可自由修改与再分发，衍生作品需同样开源。
