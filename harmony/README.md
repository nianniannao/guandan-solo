# 掼蛋 · 鸿蒙版（ArkTS）

与安卓版同一套规则、同一个联机协议（TCP 48620，见 `PROTOCOL.md`），可与安卓版互通联机。
当前对齐安卓 **v8.8（versionCode 20）**。

## 目录

```
harmony/
├─ AppScope/app.json5              # bundleName com.guandan.solo，versionName 8.8 / versionCode 20
├─ build-profile.json5             # compatibleSdkVersion 5.0.0(12)，runtimeOS HarmonyOS
├─ hvigorfile.ts
├─ entry/
│  ├─ src/main/module.json5        # 已声明 INTERNET / GET_NETWORK_INFO
│  ├─ src/main/ets/
│  │  ├─ entryability/EntryAbility.ets
│  │  ├─ model/Guandan.ets         # 规则、牌型、AI、引擎
│  │  ├─ net/Lan.ets               # TCPSocket 房主 / 加入方
│  │  ├─ pages/Index.ets           # 五行八卦界面 + 设置行
│  │  └─ voice/Voice.ets           # 内置离线语音播报（AVPlayer，按素材时长排队）
│  ├─ src/main/resources/
│  │  ├─ rawfile/voice/            # 54 段中文播报 mp3 + index.txt（与安卓同一套素材）
│  │  └─ base/                     # 图标、主题色、页面清单
```

## v8.8 在鸿蒙版上做了什么

1. **AI 增强**：候选牌枚举后按“手数减少 / 拆牌代价 / 炸弹保留 / 记牌控制力 / 队友与对手残局”综合打分；
   新增记牌（`Engine.playedCounts`）与领出决策 `Ai.chooseLead`，不再无脑甩最小单张。
2. **接风不再卡住**：过牌判定从固定 3 家改为“除领出者外的全部活跃玩家”，有人出完后轮次仍能正常结束。
3. **语音播报**：单张报点数，其余只报牌型；跟牌用「大你 / 管住」，炸弹用「炸你一下」，过牌用「要不起」，
   给队友让牌用「让你一手」。不报左右对家称呼。语音播完才轮到下一位，不会话赶话。
4. **节奏设置**：`2s / 3s / 5s / 6s` 四档，默认 3 秒，位置在「声音」左侧同一行。
5. **字号加粗加大**：座位、手牌、桌面牌、按钮、提示文字整体放大并加粗。
6. **开局静默**：只在“出现了新的出牌”时播报，读档 / 重连同步状态不会凭空出声。

## 怎么出 HAP（必须签名才能装到纯血鸿蒙）

1. 安装 **DevEco Studio 5.0** 及以上（自带 HarmonyOS NEXT SDK 与 hvigor）
2. `File → Open` 打开本 `harmony` 目录，等待首次 Sync 完成
3. `File → Project Structure → Signing Configs`，勾 **Automatically generate signature**（需登录华为开发者账号），
   它会自动生成 `debug`/`release` 签名材料
4. 连真机（开发者模式 + USB 调试）→ 点 Run 直接安装
5. 出安装包：`Build → Build Hap(s)/APP(s) → Build Hap(s)`，
   产物在 `entry/build/default/outputs/default/` 下的 `*.hap`

> HarmonyOS NEXT 只接受已签名 HAP，未签名包无法安装；上架应用市场则需在 AppGallery Connect 创建应用与发布证书。
> 本仓库的 Linux 环境没有 HarmonyOS SDK，因此这里只提供可编译工程，`hvigor` 打包需在 DevEco Studio 内完成。

## 与安卓联机

- 鸿蒙「创建房间」→ 安卓「加入房间」输入鸿蒙设备 IP（反之亦可）
- 两端须在同一局域网（同一路由器 / 热点下）
- 端口 48620 需未被占用，路由器不隔离设备

## 已知限制

- 语音音量为系统媒体音量，暂未做独立音量条
- 鸿蒙版尚未接入自动存档（安卓版有）
