# 双人待办云服务（1.5.0）

环境：`withyou-sync-d0gg468vheead040b`，上海，PostgreSQL。
业务访问：`https://withyou-sync-d0gg468vheead040b.api.tcloudbasegateway.com/v1/rdb/rest/rpc/`
原生认证：`/auth/v1/signin`、`/auth/v1/token`、`/auth/v1/user/me`、`/auth/v1/user/signout`。
注册：`https://withyou-sync-d0gg468vheead040b-1322438694.ap-shanghai.app.tcloudbase.com/withyou-register`。

## 首次初始化

使用 CloudBase CLI 浏览器授权，无需向聊天、APK 或源码提供 SecretKey：

```powershell
npm exec --yes --package=@cloudbase/cli -- tcb login --flow web
python scripts/cloudbase_sql.py --apply cloudbase/sql/001_schema.sql cloudbase/sql/002_api.sql cloudbase/sql/003_registration.sql
```

脚本要求先通过 npm exec 缓存 CLI，当前 Windows 版本从 npm 缓存定位其入口。SQL 脚本可在 CloudBase SQL 编辑器以管理员运行；控制面单语句限制由 Python 脚本的事务 DO 包装处理，不简单按分号拆散函数体。
数据库脚本只创建 wy_* 业务对象，不更改 auth schema、不授予匿名用户业务权限。
部署前检查目标环境，数据库版本升级另写迁移，不用重建表代替迁移。

## 注册云函数

CloudBase 公共 signup 接口不支持仅用户名密码注册，因此第一版是受邀注册：管理员生成一次性激活码，云函数验证后创建外部账号，用户再用原生 signin 登录。
运行时使用平台临时执行角色凭据，任何凭据均不返回客户端。激活码仅保存摘要，成功后不能再次使用。

```powershell
# 在 cloudbase/register 目录执行
npm exec --yes --package=@cloudbase/cli -- tcb fn deploy withyou-register -e withyou-sync-d0gg468vheead040b --dir . --httpFn --runtime Nodejs20.19 --install-dependency true --json
```

HTTP 函数必须使用 `WEB_SCF` 路由类型，不是普通 Event 函数的 `SCF`。在 HTTP 网关将 `/withyou-register` 指向此函数，关闭额外平台登录校验及网页安全域名校验（原生 Android 无浏览器 Origin），注册仍须激活码。设置总 QPS 3、每 IP QPS 1；数据库还限制每来源每分钟 8 次注册尝试。
函数超时设为 30 秒，使用 Nodejs20.19，保持现有低用量资源，不开通付费增强服务。
部署后对照网关路由实际地址，不推断旧 `.service.tcloudbase.com` 地址一定支持新 HTTP 函数。

`scf_bootstrap` 必须无 BOM、LF 换行并具有可执行权限；在 Linux 打包时执行 `chmod +x scf_bootstrap`。依赖锁定 `@cloudbase/manager-node` 5.9.1，由平台在云端安装；其传递依赖的弃用提示不代表 Android 客户端引入了那些依赖。

## 账号和权限

- 每人独立账号、最多一个当前伙伴；配对由生成邀请码、对方申请、生成方确认组成。
- 配对码 128 位随机、10 分钟有效，库中只存 SHA-256；不可重复使用。
- 注册激活码与配对码不同，前者由管理员提供，用完失效。
- 普通账号没有直接 INSERT/UPDATE/DELETE 表权限，只能调用审核过的 RPC。
- 业务用户身份从网关验证的 JWT `sub` 获取，不接受客户端 owner_id 作为身份依据。
- 内部权限辅助函数位于 wy_private，不通过 public PostgREST 暴露。
- 每账号最多 500 个未删除事项；共同事项按创建者管理，双方打卡独立保存。

## 同步协议

wy_snapshot：返回当前配对、待确认申请、可见事项 ID 与增量内容。可见 ID 集合用于清理被撤回的共享项。
wy_apply：操作 UUID 去重、预期 revision 乐观锁、服务器归属检查。删除留墓碑，不接受旧修改复活。冲突回传中文提示，手机保存草稿摘要而不无限重试。
wy_pair_action：配对与解除统一加事务锁；快照和业务变更使用同一短事务锁，防止配对切换与权限检查竞态。适合本次双人小规模使用，不宣称大规模吞吐性能。

客户端发送队列前先刷新关系，旧配对操作和跨日习惯打卡停止补传；不会自动补签。日历统一 Asia/Shanghai。一次性待办使用专用稳定完成键，完成状态不随跨日重置。
本机数据按账号独立 AtomicFile 保存，令牌用 Android Keystore AES-GCM 加密。离线不能得知云端已解除配对，恢复连接后清理对方数据；已看过、导出或截图的内容无法撤回。

前台成功同步最多每 30 秒检查一次；失败指数退避至 10 分钟；后台没有轮询或定时云函数。本版本未承诺即时推送。
照片、纪念日及其设置不参与双人同步。

## 备份与额度

App 的“账号与伙伴 → 导出打卡数据”导出当前账号可见业务数据，不含令牌。此 JSON 是备份/迁移材料，第一版未实现一键导入；云端仍是已同步数据来源。
管理员可手动导出业务数据库：

```powershell
python scripts/cloudbase_backup.py --output local-secrets/withyou-cloud-backup.json
```

备份包含私密业务记录，不上传 GitHub。平台账号认证库由 CloudBase 管理，本脚本不导出密码、刷新令牌或激活码，也不宣称是完整环境恢复镜像。
免费体验环境查询到期日为 2027-04-19，自动续费未开启。平台控制台为准，不自动升级套餐。请在到期前导出数据并决定续期。

## 验证

`scripts/test_cloudbase_sql.mjs` 在 PGlite PostgreSQL 中执行真实 SQL/RLS，需要先本地安装测试依赖至 build/pg-test（不进入 App）：

```powershell
npm install --prefix build/pg-test --no-audit --no-fund @electric-sql/pglite
node scripts/test_cloudbase_sql.mjs
```

`scripts/verify_cloudbase_live.py` 是明确标记的真实云端回归，只读取 build/cloud-private/accounts.json 中一次性 QA 账号；会创建测试事项并解除这组测试账号配对。不要把真实用户账号写入该文件。
TodoCloudEvidenceTest 是手动开启的 Android 真实云端与离线专项测试，普通回归排除它；测试只在专用模拟器运行，会切换该模拟器网络并在 finally 恢复。
后续源码上传、GitHub Release 和下载页发布仍等待用户单独通知。

## 官方接口依据

- https://docs.cloudbase.net/database/postgresql/initialization
- https://docs.cloudbase.net/http-api/auth/auth-sign-in
- https://docs.cloudbase.net/http-api/auth/auth-grant-token
- https://docs.cloudbase.net/http-api/auth/auth-sign-up
- https://docs.cloudbase.net/api-reference/manager/node/user
- https://docs.cloudbase.net/cloud-function/develop/how-to-writing-functions-code


## 1.6.0 共享纪念日与私有照片

按顺序部署 001、002、003 后，再部署 004_shared_memories.sql 和 005_completion_dates.sql。已有 1.5 环境只需要后两份迁移；005 更新打卡写入函数，保存离线一次性待办实际完成日期，兼容 1.5 客户端。重跑 002 会恢复旧写入函数，必须随后重跑 005。

```powershell
python -X utf8 scripts/cloudbase_sql.py --apply cloudbase/sql/004_shared_memories.sql cloudbase/sql/005_completion_dates.sql
```

004 创建 wy_memories 及 RPC wy_memory_snapshot / wy_memory_apply，并创建私有 storage.buckets: withyou-memories。对象名为 owner/id/SHA256.jpg；仅本人上传/删除、当前有效配对伙伴读取已发布照片，不允许覆盖不可变照片。头像、分类、置顶、私人照片路径不会共享。每账号最多 100 份正在共享的纪念日、150 个云照片对象，单文件最大 768 KiB、JPEG。正常照片替换/移除后客户端清理旧对象；上传成功但请求中断可能留下孤立对象，额度达到上限时需管理员核对关联后清理，不能盲删。

使用实际 PostgreSQL Storage HTTP API：POST /v1/storages/object/withyou-memories/{path}，GET /v1/storages/object/authenticated/withyou-memories/{path}，DELETE /v1/storages/object/withyou-memories/{path}。所有请求使用终端用户 Bearer 令牌，不含管理员 Key。官方依据：https://docs.cloudbase.net/api-reference/manager/node/storage （PostgreSQL Environment 部分），本机安装 manager-node 5.9.1 的接口实现与真实环境已交叉验证；不使用 COS 架构的 uploadFile，也不假定通用 Supabase Storage URL。

照片上传和元数据发布分两步，先上传不可变文件，再在数据库事务中同时提交照片路径与完整外观。客户端校验 SHA256 后落盘；下载失败展示对应新版本的安全底色，不混用旧照片。队列、上传照片副本和共享缓存按账号保存于 App 私有目录。撤回/解除配对在服务器上即时撤销后续鉴权读取，在另一端前台联网同步后清理副本。第三方截图/导出无法撤回，离线副本不会远程自毁。同步是前台轮询（成功后间隔至少 30 秒），失败有退避和手动刷新，不承诺后台实时推送。

scripts/cloudbase_backup.py 现在包含共享纪念日元数据；不包含 Storage 对象二进制，不是完整共享照片恢复包。用户自己创建的原始纪念日及照片仍可由原设置页导出 ZIP；伙伴共享副本不并入本机私人纪念日备份。

验证：node scripts/test_shared_memories.mjs；scripts/verify_shared_cloud.py 明确创建临时 QA 账号并保存忽略目录，只在管理员本机运行。SharedCloudEvidenceTest 需手动开启 sharedCloudEvidence=true，测试凭据经 adb 放在专用模拟器 /data/local/tmp/withyou-v16-qa.json（测试后删除），不放进 APK 或源码。它会测试网络中断/重启/上传/对方查看/撤回/解绑，仅用于专用测试账号。
