# F-Droid 收录说明

`com.guandan.solo.yml` 是提交到 [fdroiddata](https://gitlab.com/fdroid/fdroiddata) 的元数据模板。

## 提交步骤（需你的 GitLab 账号）

1. Fork `https://gitlab.com/fdroid/fdroiddata`
2. 把 `com.guandan.solo.yml` 放到 `metadata/` 目录
3. 提交合并请求（Merge Request），目标分支 `master`
4. F-Droid 机器人会跑 `fdroid lint` 与构建验证；首次收录通常需要几天

## 关键要求与自查

| 要求 | 本仓库状态 |
|---|---|
| 公开源码仓库 | 已满足（GitHub 公开） |
| 自由许可证 | GPL-3.0（LICENSE） |
| 可用 Gradle 构建 | 已提供（`app/build.gradle`，compileSdk 34 / minSdk 21） |
| 无专有依赖 | 无第三方库，纯 framework API |
| 无内嵌广告 / 统计 | 无 |
| 每条 Build 有 tag | 已打 `v7.2` |
| 语音素材版权 | ⚠️ 由公开在线合成接口生成，见根目录 README「语音素材说明」；若审核要求，替换为自有录音后重新发版 |

## 备注

F-Droid 会用其自有密钥重新签名，与本仓库 Release 中的 APK 签名不同，属正常现象。
安装时请先卸载 GitHub 版本，否则会因签名不同安装失败。

## 已提交的上游合并请求

- GitLab MR：https://gitlab.com/fdroid/fdroiddata/-/merge_requests/57812 （!57812，源分支 `nianniannao/fdroiddata:add-guandan-solo` → `fdroid/fdroiddata:master`）
