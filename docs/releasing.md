# 发布与签名

## GitHub Actions

[Build and release](../.github/workflows/build.yml) 在 PR、`master` 推送和 `v*` 标签推送时运行
SDK 检查、Android 单元测试、Debug 构建和 lint，提供 7 天保留期的 Debug APK 与检查报告。
不运行真实 NGA 接口测试或设备插桩测试。项目当前 lint 配置不会因 lint 告警导致构建失败，需查看报告。

只有标签推送且检查成功后才构建签名 Release APK，并创建 GitHub Release，附带三个独立 APK 和
`SHA256SUMS`。标签必须是 `vMAJOR.MINOR.PATCH`，指向 `master` 历史中的提交，且版本与 APK
的 `versionName` 一致。分支和 PR 构建不读取签名 Secrets。

Debug / Release 均通过 ABI splits 生成 `arm64-v8a`、`armeabi-v7a`、`x86_64` 三种 APK，
不生成通用 APK 或 32 位 x86 APK。发布附件命名为 `nagi-<tag>-<abi>.apk`；发布前会核对三种
ABI 是否齐全、原生库是否属于对应 ABI，以及每个 APK 的版本和签名。
Release 正文仅使用[本次 changelog](../.github/release-notes.md)，不追加安装说明、验证范围或自动生成的提交日志。

## 首次配置密钥

如果已有分发给用户的 APK，请复用它的签名密钥，不要生成新密钥。否则，在**仓库外**生成专用密钥：

```sh
mkdir -p "$HOME/.nagi-signing"
chmod 700 "$HOME/.nagi-signing"
keytool -genkeypair -v -storetype JKS \
  -keystore "$HOME/.nagi-signing/nagi-release.jks" \
  -alias nagi -keyalg RSA -keysize 3072 -validity 10000
chmod 600 "$HOME/.nagi-signing/nagi-release.jks"
```

使用 JDK 21 的 `keytool`，交互输入密钥库密码、证书信息和密钥密码；密钥密码可以与密钥库密码相同。
不要把密码放进命令参数、源码或聊天记录。将密钥库加密备份到至少两个独立的安全位置，
并在密码管理器保存密码、别名与证书指纹。GitHub Secrets 不是备份，也无法下载恢复。
对于直接分发的 APK，丢失密钥意味着无法为已有安装提供同签名更新。

在本机使用已登录的 `gh` 配置以下 **Actions repository secrets**：

```sh
base64 < "$HOME/.nagi-signing/nagi-release.jks" | tr -d '\r\n' | \
  gh secret set NAGI_SIGNING_STORE_BASE64 --repo Yarnus/nagi
printf '%s' nagi | gh secret set NAGI_SIGNING_KEY_ALIAS --repo Yarnus/nagi
# 不带 --body 时，gh 会交互读取密码；分别输入密钥库密码和密钥密码。
gh secret set NAGI_SIGNING_STORE_PASSWORD --repo Yarnus/nagi
gh secret set NAGI_SIGNING_KEY_PASSWORD --repo Yarnus/nagi

gh secret list --repo Yarnus/nagi
```

Base64 只是编码，不是加密；不要把编码后的密钥库保存进 Git、Issue 或构建产物。
GitHub 加密保存 Secrets；工作流仅在发布构建步骤中注入密码，将密钥库解码到 runner 临时目录，
并在结束时清理。Release job 不使用 Gradle 缓存，不上传密钥库或密码。
`NAGI_SIGNING_STORE_FILE` 是 CI 内部路径，无需配置为 Secret。

任何能修改并运行发布工作流的人都可能窃取签名凭据。限制仓库写入权限，保护 `master` 和
`v*` 标签，审核工作流修改，不对不可信 PR 使用 `pull_request_target` 或提供 Secrets。
需要审批门禁时可将 release job 绑定到受保护的 GitHub Environment，并把 Secrets 迁入该环境。

## 发布一个版本

1. 修改 `app/build.gradle.kts` 的 `versionName`，并递增 `versionCode`（首个版本可保留 `0.1.0` / `1`）。
2. 将 `.github/release-notes.md` 替换为本版本的 changelog，只写本次用户可见的更新。
3. 提交并推送到 `master`，等待 CI 通过。
4. 配置好签名 Secrets 后，在该提交上创建并推送标签：

```sh
git switch master
git pull --ff-only origin master
git tag -a v0.1.0 -m "Release v0.1.0"
git push origin v0.1.0
```

查看 [Actions](https://github.com/Yarnus/nagi/actions) 和
[Releases](https://github.com/Yarnus/nagi/releases)。只创建本地标签不会触发发布；
不要把标签打在未合并分支上，也不要移动已发布标签。
发布失败时检查 Actions 日志；修复 Secrets 后可重跑失败任务。流程不覆盖已存在的 Release。

工作流检查构建、测试、APK 架构与签名，但不能证明三种架构的设备运行行为或真实 NGA 接口可用。
首次发布前应在设备上安装测试，并记录签名证书指纹；后续用新 APK 覆盖安装验证升级与数据保留。
