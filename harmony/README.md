# 掼蛋 · 鸿蒙版（ArkTS）

与安卓版同一套规则、同一个联机协议（TCP 48620，见 `PROTOCOL.md`），可与安卓版互通联机。

## 目录

```
guandan-harmony/
├─ AppScope/app.json5
├─ build-profile.json5          # 工程级构建配置
├─ hvigorfile.ts
├─ entry/
│  ├─ build-profile.json5
│  ├─ src/main/module.json5     # 已声明 INTERNET / GET_NETWORK_INFO
│  ├─ src/main/ets/
│  │  ├─ entryability/EntryAbility.ets
│  │  ├─ model/Guandan.ets      # 牌型、规则、AI、引擎（与安卓同逻辑）
│  │  ├─ net/Lan.ets            # TCPSocket 房主 / 加入方
│  │  └─ pages/Index.ets        # 五行八卦风格界面
│  └─ src/main/resources/       # 图标、主题色、页面清单
```

## 怎么出 HAP

1. 安装 **DevEco Studio 5.0** 及以上（自带 HarmonyOS SDK、hvigor）
2. 打开本目录（Open Project）
3. 首次会自动 Sync 依赖；在 `Project Structure → Signing Configs` 勾选自动签名
4. 连鸿蒙真机（开发者模式 + USB 调试），点 Run 即可安装
5. 打包：`Build → Build Hap(s)/APP(s) → Build Hap(s)`，产物在 `entry/build/default/outputs/default/`

> 本机 Linux 环境没有 HarmonyOS SDK，所以只交付可编译的工程源码，`hvigor` 打包需在 DevEco Studio 里完成。

## 与安卓联机

- 鸿蒙「创建房间」→ 安卓「加入房间」输入鸿蒙设备 IP（反之亦可）
- 两端须在同一局域网（同一路由器 / 热点下）
- 端口 48620 需未被占用，路由器不隔离设备

## 当前实现范围

- 规则内核：牌型判定、逢人配展开、炸弹分级、接风、升级、结算 —— 与安卓逐条对齐
- AI：候选牌枚举 + 三档策略（初级/高级/终极）
- 联机：房主权威 + 状态快照全量同步 + UDP 广播发现（48621）+ 掉线重连（90 秒内原座复位）+ 22 秒出牌超时托管
- 界面：五行配色（木青/土金/水玄）+ 八卦方位座位（乾·天行 / 坤·厚德 / 震·雷动 / 兑·泽悦）
