# 管理员开通创作者能力后仍无法登录

日期：2026-10-14（Asia/Hong_Kong）。

## 原因与修复

受影响账号的 user_author_capability.enabled 已为 1，账号未停用、未删除。管理员能力更新接口使用的权威表与身份 Mapper 一致。

AppAuthenticationService.issueSession 在匿名登录请求内调用 toCurrentUser。原实现使用 IdentityProvider.currentIdentity()，此时安全上下文尚未建立 App 身份，返回游客的空角色、空权限和 authorCapability=false；前端按严格布尔能力准入，因此拒绝已开通账号。现按本次凭据已验证的 AppUserRecord.userId 查询既有 AppIdentityMapper，并从 AppUserMapper 查询实名状态。登录、短信登录、注册响应与身份恢复共用此投影，不修改请求身份、不采用其他账号的会话、不将角色推导成创作者能力。

前端继续要求 authorCapability === true，不改为默认放行。

## 验证

新增 AppLoginCreatorCapabilityTest，修复前 4 项测试中 3 项失败，分别复现匿名登录错误能力、授予后未刷新能力和其他主体干扰身份投影；修复后全部通过，缺失能力继续返回 false。

登录协议、若依会话、登录审计和新增回归合计 22 项测试通过；smartscript-user 及其依赖的全量测试通过。用户端现有 21 项 Node 测试通过。

本机后端启用了 Spring Boot DevTools，编译后已自动加载修复。使用独立临时普通账号与仅拥有 user:creator:update 的临时管理账号进行真实 API 和 Chromium 联调，没有改动受影响账号权限：

- 初始无能力时，真实浏览器登录被拒绝且不保存用户端令牌。
- 管理员开通后，同一浏览器同一账号再次提交密码登录成功，进入 /works。
- 登录响应和 getInfo 都返回 authorCapability=true，刷新页面恢复工作台。
- 管理员撤销后，刷新返回登录页并清理用户端令牌。

临时账号、角色、能力、同意记录、短信记录及测试会话已清理；结果仅保存检查摘要，不保存密码、验证码或令牌。联调脚本及摘要位于工作区 .local/creator-login-20261014。

## 构建边界

完整后端的 -DskipTests package 遇到既有 smartscript-content 测试编译问题：AppUserWorkServiceTest 的四处 listWorks 调用仍传两个参数，而现有接口要求四个。这与本次身份投影修复无关，本次未修改作品模块。认证模块测试已独立通过；使用 -Dmaven.test.skip=true 完成整体生产打包。没有声称整仓所有模块测试通过。
