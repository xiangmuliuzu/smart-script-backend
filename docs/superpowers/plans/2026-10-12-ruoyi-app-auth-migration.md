# 若依 App 登录迁移实施计划

1. 复用若依 LoginUser、AuthenticationManager、SysPasswordService、验证码及 IP 黑名单校验；为 App 账号设置独立 credentialDomain，并禁止管理端权限进入 App 登录态。
2. 在 TokenService 中绑定 JWT 声明和 Redis 登录态的凭证域；仅在各自安全链中执行过滤器，保留 AppIdentityContext 业务适配层。
3. 统一密码/短信/注册为单令牌；提供 LoginBody/AjaxResult/getInfo 协议；升级退出、密码与账号状态变更的会话吊销。
4. App 改用单令牌安全存储、验证码与 getInfo；移除刷新令牌和重试代码，保护新登录态不被旧请求结果清理。
5. PC 用户门户采用新的顶层 token，清除旧刷新令牌；保留管理端登录路径和账号权限隔离。
6. 单测验证双域、续期、吊销、验证码表单和旧请求竞态；真实 HTTP 使用临时账号验证全链路并清理数据；执行 App/Web/后端构建。

本次不提交其它工作区改动，不修改数据库结构。既有历史 App 令牌要求重新登录。
