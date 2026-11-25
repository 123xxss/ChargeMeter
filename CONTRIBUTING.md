# 参与贡献

感谢你愿意改进 ChargeMeter！

## 报告问题

请使用「机型兼容性反馈」Issue 模板，务必附上：

1. 手机品牌 + 型号、系统版本
2. 「校准」里设置的电芯数
3. App 显示的功率 vs 手机系统显示的瓦数
4. 界面底部「原始 …」那一行

这四项能定位 90% 的读数问题。

## 提交代码

1. Fork 本仓库
2. 新建分支：`git checkout -b feat/你的功能`
3. 提交前确保能编译通过：
   ```bash
   ANDROID_SDK_ROOT=~/android/sdk bash build.sh
   ```
4. 提交 PR，描述里写清楚动机和验证方式（哪台机器、什么场景）

## 代码约定

- 兼容 `minSdk 29`（Android 10），不要引入 AndroidX / Gradle 依赖，保持纯 framework + 脚本构建
- 新增字符串一律中文，单位保留 W / A / V / Hz 等标准符号
- 涉及电池数据的改动，请在真机上验证并说明机型

## 签名说明

仓库里不含 keystore。`build.sh` 首次运行会生成调试签名 `signing.keystore`（口令 `android`），发布自用版请替换成你自己的。
