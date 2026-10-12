# App 登录迁移至若依认证框架

用户确认完整迁移：复用若依 AuthenticationManager 与 TokenService，同时保留 App/管理端账号和权限隔离。

- 密码登录 `POST /api/v1/auth/login` 接收若依 LoginBody：`username/password/code/uuid`。username 可为 App 用户名或手机号；认证时转换为数据库中的规范用户名，共用若依密码重试限制。验证码开关、生成和校验复用若依。
- `GET /api/v1/auth/captchaImage` 是若依原生验证码控制器的别名，确保 App 请求仍位于 `/api/v1` 网关下。
- 短信登录和注册保留原入口及验证流程，但成功响应改为若依 AjaxResult：`{code:200,msg,token,user}`。新会话只签发一个 token，不创建 refresh session。
- `GET /api/v1/auth/getInfo` 返回 `{code:200,msg,user,roles,permissions}`；旧 `/auth/me` 保留为业务兼容入口，认证同样依赖若依 Redis 会话。
- App JWT 与管理端 JWT 都由 TokenService 签发，签名声明 `credential_domain` 必须匹配 Redis LoginUser 的域。App 登录态没有管理端权限，管理端过滤器拒绝 App 令牌，App 过滤器拒绝管理员令牌并实时检查账号状态。
- 私有请求调用若依 verifyToken，接近过期时自动延长 Redis 登录态。Redis 会话不存在或过期时要求重新登录；客户端不发刷新令牌请求。
- 登录保留历史若依账号的短密码兼容（若依最低 5 位）；App 原有注册与改密强度规则保持，已创建的长密码仍可使用。App 登录后读取 getInfo，再建立含真实角色和创作者能力的已验证状态。
- 保留 AppIdentityContext 作为业务身份适配层，以兼容现有用户中心、内容和创作者接口。
- 退出删除当前 Redis 会话；首次设密码保留当前会话并清除其它 App 会话；改密码、重置密码、换绑手机号、停用账号和修改角色均清除目标账号的 App 会话。旧 JWT 签发器、刷新轮换服务及数据库映射、刷新接口和独立密钥配置已删除。历史数据库表保留，但当前代码不再访问。
- 旧 App JWT 不迁移至新凭证，升级后需要重新登录。现有若依管理员会话仍兼容；PC 用户门户同时改用新单令牌。
- 不改数据库结构，不执行数据库迁移；保留工作区原有资料名称、系统管理和本地配置改动。

验证包含完整后端单测、App 认证与业务回归、Web gate、真实本地 HTTP 双域隔离/吊销、APK 构建。
