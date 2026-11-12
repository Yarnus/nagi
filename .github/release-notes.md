## 安装包选择

每台设备只需下载安装一个 APK（Android 8.0 及以上）：

- **arm64-v8a**：大多数现代 Android 手机和平板，通常选这个。
- **armeabi-v7a**：旧款 32 位 ARM 手机和平板。
- **x86_64**：64 位 Intel / AMD Android 设备及对应架构的模拟器。

不包含 32 位 x86 版本。不确定架构时可通过 `adb shell getprop ro.product.cpu.abilist` 查看。
`SHA256SUMS` 可用于校验下载文件的完整性。

首次从 Debug 版或其他签名版本切换时可能需要先卸载，卸载会清除本地数据。
后续正式版使用同一签名密钥；请选择与设备匹配的架构进行升级。

## 验证范围

发布流程包含 SDK 检查、Android 模拟单元测试、Debug / Release 构建、lint 报告、
APK 原生库架构与签名校验。它不代表真机安装、三种架构的运行行为或真实 NGA 接口已验证。
