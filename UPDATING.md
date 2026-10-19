# 版本发布与手机更新

更新来源：`2269642185/withyou`。首次支持更新的版本为 1.4.0（versionCode 6）。

## 手机使用

先在旧版设置中导出备份，再直接打开新版 APK 覆盖安装，不要卸载旧版。
之后在「设置 → 版本与更新」检查新版。每天至多自动检查一次；忽略的版本仍可手动查看。
下载完成后检查 SHA-256、大小、包名、版本、系统兼容性和原安装签名，再允许进入系统安装界面。
首次 App 内安装需要允许本 App 安装未知来源应用；拒绝授权或取消安装不会清除记录。
下载取消或失败可以重试，不支持断点续传。进程被系统终止后，完整且验证通过的安装包可重新验证使用；未完成下载需重试。

自动检查仅检查开发者已经发布的新版，不会静默安装。2026-10-19 用户已持续授权：后续按用户需求完成的改进，通过构建和必要验证后直接发布源码、APK、更新清单与下载页，不再逐版询问发布许可。验证失败或缺少原签名时不得发布，应说明问题；没有授权新增定时任务或无人值守 CI 发布。
App 通过 GitHub Releases 的 `releases/latest/download/update.json` 读取更新信息，安装包使用版本固定地址。
不向 App 写入 GitHub 登录令牌；匿名下载要求仓库公开。GitHub 在某些网络下可能无法访问。

## 构建与手动发布

1. 在 `app/build.gradle.kts` 增加 versionCode 和 versionName；同步 RELEASE-NOTES.md。
2. 使用原开发电脑的同一签名构建。`build-local.ps1 -Test` 完成验证。
3. 生成发布文件：

```powershell
python scripts/prepare_release.py --apk app/build/outputs/apk/debug/app-debug.apk --notes RELEASE-NOTES.md --version 1.6.1 --code 9 --out release-output/v1.6.1
```

4. 将 `release-output/site/` 内容复制至仓库的 `docs/`，提交已验证源码、文档和 docs。不要提交 APK 到 Git 历史。
5. 首次在 GitHub 的 Settings → Pages 选择 Deploy from a branch、main、/docs。公开仓库可提供匿名访问的页面。
6. 在 GitHub Releases 创建对应标签版本，上传 `release-output/assets/withyou.apk` 和 `update.json`，填写更新说明后发布。先上传完附件再公开版本，避免半发布。
7. 在上述持续授权范围内，使用已登录 Git 凭据的手动脚本：

```powershell
python scripts/github_release.py --bundle release-output/v1.6.1 --commit 实际已推送提交SHA
```

脚本只在手动运行时发布，没有定时任务、推送触发的版本发布或自动更新签名。若版本标签已存在，不覆盖已有版本；先检查后台是否留下未发布草稿。
发布后确认页面、APK 和 update.json 可匿名访问，哈希与本地一致，并测试新旧版本判断。

## 签名与隐私

当前交付沿用原有 debug 签名，以便保留手机旧版数据；这是个人使用/测试版本，未宣称应用商店就绪。
签名私钥仅位于原电脑的 Android 用户目录，不上传源码、Releases 或网页。请独立妥善备份该密钥；丢失后新签名无法直接覆盖旧签名版本。
其他电脑直接构建会生成不同 debug 签名，无法给已安装旧版的手机覆盖更新。
`updateFixtureCode` 只用于本地生成校验测试用的较高版本，不用于正式发布；普通构建不传此参数。
App 内更新下载不采集纪念日、照片或分类。GitHub 自身会处理常规访问日志。
