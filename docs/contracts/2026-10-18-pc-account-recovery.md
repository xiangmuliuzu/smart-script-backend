# PC 三端「注册个人账号 / 忘记密码」

用户端（创作者端）与企业端复用 App 凭证域的匿名接口，不新增后端；运维端账号是 `sys_user.user_type='00'`、走管理端令牌域，另开一组匿名找回接口。

## 门户复用（用户端 / 企业端）

| 接口 | 说明 |
| --- | --- |
| `GET /api/v1/auth/agreements` | 协议版本。响应是 `AjaxResult`，列表在 `data.agreements`，前端应兼容 `result.agreements` 与 `result.data.agreements`。 |
| `POST /api/v1/auth/sms/send` | body `{phone, scene, deviceId}`；scene 取 `REGISTER` 或 `RESET_PASSWORD`。冷却 60s、验证码 5 分钟有效，dev 环境固定码 `123456`。 |
| `POST /api/v1/auth/register` | body `{phone, code, password, deviceId, deviceName, agreementAcceptances:[{type,version}]}`；协议版本必须与 `GET /agreements` 当前版本一致，否则 409。 |
| `POST /api/v1/auth/password/reset` | body `{phone, code, newPassword}`；成功后吊销该账号已有 App 会话。 |

- 登录沿用 `POST /api/v1/auth/login`；注册接口成功即返回 `{code:200,msg,token,user}`，可直接当作登录结果。
- 密码策略（注册与重置一致，`AppAuthenticationService.validatePasswordPolicy`）：8–64 字符、UTF-8 ≤ 72 字节、至少一个字母和一个数字。
- Web 端 `deviceId` 固定 `pc-web`，`deviceName` 固定 `PC Web`；服务端只把它用于设备记录，不做 PC 专属校验。
- 用户端注册出来的账号是 `user_type='01'`、没有创作者能力，创作者中心登录会被 `authorCapability` 校验拒绝。注册成功后**不落令牌**，提示用户去 App 用同一手机号提交创作者认证；企业端不校验创作者能力，注册成功可直接写 `portal.tokenKey` 并进入 `/dashboard`。

## 运维端自助找回（新增）

`PcAdminRecoveryController`（`ruoyi-admin`，`@RequestMapping("/api/v1/pc-auth/admin")`）：

| 接口 | 说明 |
| --- | --- |
| `POST /api/v1/pc-auth/admin/sms/send` | body `{phone}`。手机号格式错 → 40000「请输入正确的手机号」；未绑定管理员、冷却中、触发日限/时限都统一返回 200「若该手机号已绑定管理员账号，验证码已发送」，避免匿名枚举。 |
| `POST /api/v1/pc-auth/admin/password/reset` | body `{phone, code, newPassword}`。密码策略不通过单独返回中文原因；验证码错误/过期/已用与账号不存在统一返回 40000「手机号或验证码不正确，请重新获取验证码后再试」；新旧密码相同返回 40000「新密码不能与当前密码相同」；成功返回「密码已重置，请使用新密码登录」。 |

- 只处理 `user_type='00'`、`del_flag='0'`、`status='0'` 的账号，不会成为改写 App 用户密码的旁路。
- 验证码复用 `SmsCodeService`，scene 固定 `RESET_PASSWORD`；重置用 `SecurityUtils.encryptPassword` + `ISysUserService.resetPwd`，管理端登录链路不变。
- 成功写一条 `ADMIN_PASSWORD_RESET` 安全审计事件（手机号掩码，不含密码与验证码）。
- 两个接口都是匿名可达，因此必须同时登记在 `AppAuthSecurityConfig` 的 permitAll 与 `AppAuthAuthenticationFilter.PUBLIC_PATHS`（`/pc-auth/admin` 前缀），否则会先被过滤器 401。
- 与改密一样，重置后不会主动吊销该账号已签发的管理端令牌（若依 `TokenService` 无按用户吊销能力）；如需立即失效，走系统管理端的账号停用。

## 前端落点

- 用户端 / 企业端 `src/components/AccountPanel.vue`：登录页内切换的注册 / 忘记密码面板（不弹窗），60s 倒计时、协议勾选、本地校验与服务端错误回显。
- 用户端 `src/views/Login.vue`：`忘记密码？` 切到 reset 态，页脚上方「还没有账号？注册个人账号」切到 register 态，标题与副标题随状态变化。
- 企业端 `src/views/Login.vue`：`忘记密码？` 切到 reset 态，`注册个人账号` 按钮切到 register 态，注册成功直接进工作台。
- 运维端 `src/components/PasswordRecoveryPanel.vue` + `src/views/user/Login.vue` 的 `handleForgot`：同一张登录卡片内切换「登录 / 找回密码」，`src/api/login.js` 的 `sendAdminRecoveryCode` / `resetAdminPasswordByPhone` 走 `adminV1Request`，带 `silent: true` 避免与拦截器全局提示重复。

## 验证

- 后端：`mvn -q -pl ruoyi-admin -am compile -DskipTests` 通过；真实本地 `:8080` 覆盖了运维端发送/重置的成功、未绑定、格式错、弱密码、错码/过期码、已用码、账号不存在、新旧同密码等分支，以及门户注册（含协议版本不匹配）、重置后旧密码失效、新密码可登录、重复注册 409。
- 前端：三端 `npm run gate` 通过（用户端 30、企业端 15、运维端 46 个单测）。
- 浏览器：`shared/scripts/e2e-account-recovery.mjs` 用 Playwright 跑真实页面 + 真实后端（10/10 通过），覆盖两端注册、三端找回，并在重置后用新密码调登录接口复核。
