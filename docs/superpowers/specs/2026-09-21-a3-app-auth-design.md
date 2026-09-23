# A3 App 认证迁移设计

## 1. 文档定位

本文件记录 A3 启动前已经批准的跨仓设计。跨仓业务规则的权威版本仍位于
`D:\build\shared`；后续必须建立并共同引用以下文档：

- `A3-App认证迁移实施方案.md`
- `A3-认证接口契约.md`
- `A3-G3-测试矩阵.md`
- `A3-G3-评审验收记录.md`

本设计不授权直接修改 `release`，也不代表 A3/G3 已通过。

## 2. 准入与交接顺序

1. 以 A2/G2 签署提交
   `00debf6aa469c0663ab280760890c8f71cc2ba7e` 完成后端 PR。
2. A2 只允许合入后端 `main`，不得直接合入 `release`。
3. 合并后在最终 `main` 重新执行构建、数据库正向迁移、校验、回滚、管理 API
   和敏感信息扫描，并记录最终 `main` SHA。
4. 合并后门禁通过才删除本地和远端 A2 功能分支。
5. 后端和 Flutter App 分别从各自最新 `main` 创建 A3 功能分支；A3 不继续在
   A2 功能分支上开发。

## 3. 范围

### 3.1 包含

- 短信验证码发送及 LOGIN、REGISTER、SET_PASSWORD、RESET_PASSWORD、
  CHANGE_PHONE_OLD、CHANGE_PHONE_NEW 场景边界。
- 验证码登录、手机号密码登录、传统注册。
- Access Token 与 Refresh Token 的签发、持久化、轮换、撤销和重放检测。
- 登出、当前用户、首次设置密码、修改密码、重置密码。
- 当前协议版本查询和用户协议/隐私协议留痕。
- OAuth provider 扩展点；未开放时返回稳定业务错误，不伪造成功。
- PC 管理员凭证域与 App 用户凭证域的双向隔离。
- Flutter 启动恢复、401 单次刷新、并发刷新锁、原请求最多重试一次和安全存储。
- 删除或严格隔离免登录测试入口及 DEMO_SESSION 预览能力。

### 3.2 不包含

- A4 PC 用户管理、实名审核、作者能力、消息与反馈管理页面。
- A5 个人中心、实名流程、换绑完整页面、消息中心和反馈页面。
- B、C、D、E 业务模块实现。
- 真实微信或 QQ 开放平台接入；A3 只定义稳定扩展边界。
- 共享开发库或生产库的自动迁移，以及任何 `release` 合并。

## 4. 仓库与模块边界

### 4.1 后端

后端继续使用一个若依多模块仓库。A3 业务实现进入 `smartscript-user` 业务模块，
安全过滤器、认证入口和统一异常集成只在必要处接入 `ruoyi-framework` /
`ruoyi-admin`，不得复制第二套若依管理员认证体系。

建议内部组件边界：

- `SmsCodeService`：验证码生成、散列、频控、一次性核销和 Provider 边界。
- `AppAuthenticationService`：注册、验证码登录和密码登录编排。
- `AppTokenService`：Access Token 签发与严格验证。
- `RefreshSessionService`：Refresh Token 散列、family、轮换、重放检测和撤销。
- `AppSessionRevocationService`：登出、密码变化及账号状态变化后的统一会话吊销。
- `AgreementService`：当前版本与接受留痕。
- `AppIdentityContext`：向后续模块提供安全上下文，不暴露 Token 或会话表。

Controller 只负责协议适配、参数校验和响应封装；事务、并发控制和状态机位于服务层。

### 4.2 Flutter App

保留现有路由、主题、Dio 和 Riverpod 结构，替换不符合 A3 契约的认证实现：

- `AuthRepository` 统一封装 A3 认证接口。
- `SecureTokenStorage` 使用平台安全存储保存 Access/Refresh Token；
  `SharedPreferences` 只保存非敏感用户缓存和展示偏好。
- `AuthController` 负责启动恢复、登录/注册结果、当前用户和强制退出状态。
- `AuthInterceptor` 负责 Bearer 注入、单飞刷新、队列等待和一次重试。
- 登录、注册、验证码与密码页面只调用 Controller/Repository，不直接读写 Token。

现有 `enterTestSession()` 和登录页“测试进入”必须删除；如确需 UI 预览，只允许在
编译期测试入口中存在，并且 production 构建无法启用。

## 5. 凭证域和数据流

### 5.1 登录与注册

1. App 提交手机号、凭据、设备标识和必需协议版本。
2. 后端验证场景、频控、验证码或密码，并使用数据库唯一约束解决并发注册。
3. 新用户生成不含完整手机号、不可预测的内部 `user_name`。
4. 后端写入协议留痕，创建 Refresh Session，再签发 App Access/Refresh Token。
5. Flutter 将 Token 写入安全存储，拉取 `/api/v1/auth/me` 后更新全局状态。

### 5.2 刷新与重放

1. Flutter 遇到可刷新的 401 时，通过进程内单飞锁发起一次刷新。
2. 同时失败的请求等待同一个刷新结果，不得各自刷新。
3. 后端在单个事务中锁定 Refresh Session，校验散列、family、过期与撤销状态，
   撤销旧会话并生成唯一后继会话。
4. 旧 Refresh Token 再次出现视为重放，吊销整个 family，并返回稳定错误码。
5. 刷新成功后原请求最多重试一次；再次 401 时清理凭据并进入登录。

### 5.3 凭证域隔离

- App Access Token 使用独立 issuer、audience、token type、密钥和 Redis 前缀。
- PC Token 只能进入若依管理域；App Token 只能进入 `/api/v1/**` 中明确允许的 App 域。
- 错误 issuer、audience、算法、token type、过期或撤销状态一律拒绝。
- 当前用户 ID 只来自认证上下文，禁止从请求体接受权威 `userId`。

## 6. API 契约原则

权威契约必须覆盖开发规格中的全部 `/api/v1/auth/**` 接口，并为每个接口声明：

- 方法、路径、调用方和鉴权域。
- 请求字段、格式、长度、必填条件和未知字段策略。
- 响应字段、空值语义和兼容策略。
- 错误码、HTTP 状态、客户端行为和日志级别。
- 幂等与并发语义。
- 敏感字段是否允许返回或记录。

App API 响应固定为 `{code, message, data}`。不得沿用当前 App 中 `/auth/login`、
`/auth/refresh` 等旧路径作为第二套长期契约。

## 7. 安全与配置

- Refresh Token、验证码和重置凭证只保存强散列；日志不得出现原值。
- 密码只使用项目确认的 BCrypt 方案，不进行可逆加密或自定义散列。
- 生产配置不得包含默认 Token 密钥、短信密钥、数据库口令、宽泛 CORS 或 Mock Provider。
- Mock SMS 只允许 local/test；其他 profile 启用时必须拒绝启动。
- 手机号仅按最小必要原则返回并脱敏；错误文案不得用于账号枚举。
- Refresh Session、验证码、频控和协议留痕必须跨进程重启保留。

## 8. 错误处理

接口契约至少固定以下语义：参数错误、未登录、Access Token 过期、Refresh Token
无效或重放、账号禁用、权限不足、验证码过频/错误/过期/已使用、协议未同意、
手机号占用、重复提交、资源不存在、OAuth 未开放和系统错误。

客户端只对“Access Token 可刷新”场景启动刷新；账号禁用、Refresh 重放、密码变化
导致的会话吊销直接清凭据并展示明确提示，禁止刷新循环。

## 9. 测试与验收

### 9.1 后端

- `mvn.cmd clean verify` 必须成功且无无理由跳过。
- 覆盖验证码一次性核销、并发注册、手机号唯一冲突、刷新轮换、并发刷新、重放吊销、
  登出、密码变化、账号禁用和服务重启。
- 覆盖 `alg=none`、错误算法、issuer、audience、token type 及 PC/App 交叉访问。
- 验证日志中不存在密码、验证码、Token、完整手机号和内部异常详情。

### 9.2 Flutter

- `flutter pub get`、`flutter analyze`、`flutter test` 全部通过。
- 覆盖启动恢复、离线、Bearer 注入、单飞刷新、一次重试上限、会话失效和安全清理。
- 覆盖登录、注册、验证码、密码表单的参数、Loading、防重复提交和服务端错误。
- production 构建与测试证明免登录入口不可用。

### 9.3 联调

- 使用同一联调编号记录后端和 App 精确 commit、契约版本和数据库迁移版本。
- 在 MySQL 8.0.36、Redis 和真实后端进程上执行，不以 Mock 成功替代。
- G3 验收记录逐项列出命令、退出码、通过/失败/跳过数量和证据文件。

## 10. 交付文档验收标准

四份共享文档必须满足以下条件后才能启动编码：

- 没有未决事项、占位错误码或未定义字段。
- API 契约、实施方案、测试矩阵和 G3 检查表相互引用且版本一致。
- 明确后端先行、App 消费的提交与合入顺序，以及联合回滚顺序。
- 明确 A3 不包含 A4/A5 功能，避免跨阶段扩张。
- G3 记录初始状态为“不通过/待开发”，不得预填通过结论。

## 11. 已批准决策

- 采用“共享权威文档包 + 两仓实施分支”。
- 本轮先完成 A2 交接闭环和 A3 完整文档包，不直接实现 A3 功能。
- A3 后端兼容契约先行，Flutter 后接入。
- A3 必须独立通过 G3，且不得直接合入 `release`。
