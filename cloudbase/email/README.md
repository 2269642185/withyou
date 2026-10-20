# 1.7.0 邮箱认证与朋友准入

2026-10-20。环境 withyou-sync-d0gg468vheead040b，原生 Kotlin/Compose 客户端 + CloudBase 新版 HTTP Auth + QQ SMTP。SMTP 授权码仅在云端配置，不进入源码、APK、日志或工程 ZIP。

## 使用路径

- 旧账号：继续以原用户名和密码登录 → 设置中的“账号与伙伴”（1.8.0 起） → “账号安全与邮箱” → 验证当前密码及新邮箱。不要为旧账号重新注册另一个身份。
- 新朋友：已有用户选择“邀请朋友注册”，复制链接；朋友安装新版后打开链接，也可在邮箱注册页粘贴完整链接。验证码验证后建立账号，凭邀请开通应用使用资格。
- 注册邀请 72 小时有效、一次使用；每个已开通用户 24 小时内最多生成 5 个。开通与双人配对独立，不自动建立共享关系。
- 忘记密码：邮箱验证码确认后更新密码，撤销旧刷新会话及应用业务会话，然后重新登录原 UID。

## 已验证与边界

用户确认 QQ 与 Gmail 两个邮箱均收到最初的测试邮件，随后在本机临时验证页面输入验证码完成绑定 QQ、找回密码、改绑 Gmail 和邮箱注册。真实 HTTP 检查 23 项通过；实际到账耗时和垃圾箱分类未单独记录，不把接口耗时称作送达耗时。

真实 Android 模拟器用两份临时云端账号验证邮箱登录、配对确认、双方分别打卡、隐私隔离、重建页面保留身份，以及旧会话被服务端拒绝后的自动退出和重新登录。不是两台实体手机同步验证。临时账号、业务记录、测试照片已清理，指定测试邮箱地址已释放。

邮件正文已配置“与你 · 纪念日”、验证码、有效期和非本人操作提示。发件人显示名仍受 QQ 邮箱设置及收件客户端影响，未保证所有客户端统一显示名称。

用户已明确接受第一版使用平台频率限制、异常验证和管理员全局关闭开关，不提供全局每日邮件总额硬限制。App 的 60 秒倒计时不是服务端安全边界；原生 Auth 验证接口可直接调用，仍由平台执行频率限制。遇到 CAPTCHA 时客户端安全失败并提示稍后重试/联系管理员，本版没有内嵌完整图片验证码流程。

业务准入最多 200 个成员，含原有成员。该限制不代表原生认证平台绝不会产生未开通身份；未获邀请的已验证身份不能访问本应用业务数据。注册失败或邀请失效后，可登录已建立的账号，换一个有效邀请继续开通，不自动合并其他账号。

CloudBase 官方 revoke-all 会拒绝旧刷新令牌，但旧访问 JWT 在有效期内仍可能被原生用户信息接口接受。007 增加了业务数据截止时间：SQL/RLS 下的待办、共享纪念日和私有云照片拒绝旧会话；已在真实环境确认旧业务请求 403、旧照片拒绝、刷新失败，新登录保持原 UID。新版客户端收到 SESSION_REVOKED 后清除会话及共享照片缓存，保留账号自己的本机记录和待同步队列。离线副本及截图不能远程擦除；不能宣称整个原生认证平台令牌立即失效。

## 初始化及升级

先部署 001 至 005，再运行：

```powershell
python -X utf8 scripts/deploy_email_admission.py
python -X utf8 scripts/cloudbase_sql.py --apply cloudbase/sql/007_email_session_revocation.sql
```

首次部署时，脚本分页读取现有 Auth UID 并在同一迁移事务中回填准入资格，避免遗漏尚未创建业务资料的旧账号。不会迁移或重新创建用户，也不输出邮箱或密码。现有 3 个身份已保留。准入表已存在时重跑脚本不会再把原生 Auth 中的未开通身份批量放行；该重跑行为已实际核对。

旧客户端激活码注册云函数也已更新，在原有效激活码注册成功后开通同一 UID；原有激活码不重置。重新部署时保持原函数 HTTP 类型、超时、权限与路由，参照上级 README。

006、007 可重跑；006 不覆盖已有 auth_uid 实现，避免撤销会话保护被降级。若重跑更早的核心迁移，仍需按序补齐后续迁移，不能把旧的 wy_uid 覆盖留在线上。

## 管理员开关

使用已登录的 CloudBase CLI，不向 App 提供管理员凭据：

```powershell
# 暂停新朋友开通，原有成员仍可正常使用
python -X utf8 scripts/email_registration_control.py --admission closed
# 恢复朋友准入
python -X utf8 scripts/email_registration_control.py --admission open
# 关闭邮箱认证发信；保留原用户名登录配置
python -X utf8 scripts/email_registration_control.py --email off
# 恢复已配置的邮箱身份源
python -X utf8 scripts/email_registration_control.py --email on
# 不显示 SMTP 地址或授权码的只读复查
python -X utf8 scripts/check_email_auth.py --output build/email-auth-config.json
```

复查脚本不推断历史投递成功或发布状态，实际验证记录另存。关闭准入不等于关闭原生 Auth 身份创建；要停止邮件发送需关闭邮箱身份源。没有每日硬上限，不自动改用付费代发或升级套餐。

## SMTP 配置

CloudBase 控制台 → 身份认证 → 登录方式 → 邮箱验证码 → 自定义 SMTP，QQ 邮箱、smtp.qq.com、465、SSL；发件地址和登录账号均为同一个完整 QQ 邮箱。密码栏填写在 QQ 邮箱网页版开启 SMTP 后生成的授权码，不是 QQ 密码。原配置已保存并真实投递成功。

## 验证与资料

- scripts/test_email_admission.mjs：真实 PGlite PostgreSQL 的邀请、准入、权限与关闭开关测试。
- scripts/test_email_sessions.mjs：真实 SQL 的旧会话拒绝、身份保留与迁移重跑保护。
- EmailAuthContractTest / EmailUiTest：Android 认证协议、安全失败与大字体/深色布局专项。
- EmailCloudEvidenceTest：明确手动启用的真实云端 Android 专项；普通回归通过 build-local.ps1 -Test 排除此类测试，避免使用真实账号运行。
- validation/v1.7.0.md：验证结果、环境和剩余限制。

官方依据：
- https://docs.cloudbase.net/api-reference/manager/node/login-config
- https://docs.cloudbase.net/http-api/auth/auth-send-verification
- https://docs.cloudbase.net/http-api/auth/auth-verify-verification
- https://docs.cloudbase.net/http-api/auth/auth-sign-up
- https://docs.cloudbase.net/authentication-v2/auth/account-linking
- https://docs.cloudbase.net/api-reference/webv3/authentication
