# 掼蛋 · 五行单机/联机版（Guandan Solo）

一款纯 Java、零第三方依赖的安卓掼蛋应用：国标江苏掼蛋规则，三档 AI，五行八卦视觉，支持安卓 ↔ 鸿蒙局域网联机。

- 包名：`com.guandan.solo`
- 版本：7.2（versionCode 10）
- 最低系统：Android 5.0（minSdk 21），targetSdk 34
- 体积：约 640 KB（含 81 段内置中文语音素材）
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
内置 81 段中文语音（`assets/voice`，SoundPool 顺序拼接播放），播报出牌、「不要」、「轮到你出牌」提示音；顶部「声音 开/关」总开关，状态持久化。系统 TTS 作为兜底。

**存档**
每步落盘（`progress_v1`），启动时提示「继续上一局」，首页另有续局入口；结算 / 新局 / 联机时自动清档。

**局域网联机**
- TCP 48620 传输牌局状态（行分隔 JSON），UDP 48621 广播发现（`GD?` / `GD!房间名|端口|房号`）
- 房主分配座位，90 秒座位保留，断线自动重连
- 22 秒出牌超时由房主托管
- 协议见 `harmony/PROTOCOL.md`

**鸿蒙版**
`harmony/` 为完整 ArkTS 工程（需 DevEco Studio 5 编译 .hap），规则内核、打分 AI、发现与重连逻辑与安卓同构，可与安卓版互通。

## 构建

```bash
# 需 JDK 17 + Android SDK（compileSdk 34）
./gradlew :app:assembleRelease
# 产物：app/build/outputs/apk/release/app-release-unsigned.apk
```

签名示例：

```bash
apksigner sign --ks release.keystore --out guandan.apk app-release-unsigned.apk
```

## 目录

```
app/src/main/java/com/guandan/solo/   源码（11 个类）
  Card / Play / Rules                 牌与牌型判定
  Engine                              对局状态机与升级结算
  AI                                  三档 AI（打分 + 残局模拟）
  GameView                            自绘牌桌与全部交互
  Voice / LocalVoice                  语音（系统 TTS / 内置素材）
  Net / Json                          联机与协议
  Save                                进度存档
app/src/main/assets/voice/            内置语音素材 + index.txt（时长索引）
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

实测（云主机 JVM）：120 局 / 13,647 个动作全部正常结束，单步最慢 57ms；难度对照 150 局一组，终极·深度 vs 初级·快 93%，高级·深度 vs 初级·标准 90%，终极·深度 vs 高级·标准 67%。

## 语音素材说明

`assets/voice` 内的中文片段由公开在线语音合成接口离线化生成，随 APK 打包以便离线播报。若你计划再分发或商用，请替换为自己的录音 / 自有版权合成音，并用 `tools/gen_voice.py` 的命名规则重新生成（`index.txt` 记录每个片段的时长，用于顺序播放）。

## 许可

GPL-3.0-or-later。代码可自由修改与再分发，衍生作品需同样开源。
