# 面向用户错误文案统一 Spec

## Why

App、创作者 PC 端、企业 PC 端目前会把后端 `msg`/`message` 直接展示给用户。A3 认证错误在
`AppAuthExceptionHandler.safeMessage` 中被刻意保留为英文（`invalid request`、`account disabled`、
`phone or password incorrect` 等），实名姓名/证件号校验也返回 `real name length invalid` /
`id number format invalid`。此外，网络异常、超时、HTML 404、服务端 500、流式生成失败等会展示
`HTTP 404`、`Request failed with status code 404`、`Failed to fetch` 等技术信息。

目标：面向用户的错误提示统一为简洁、正常、礼貌的中文；保留错误码与 HTTP 状态，不影响客户端
判断分支、审计日志和运维排查。

## 范围

覆盖：

- Flutter App（`smart-script-app`）
- 创作者 PC 端（`smart-script-web-user`）
- 企业 PC 端（`smart-script-web-enterprise`）
- 上述三端使用的 App 凭证域后端接口文案

不覆盖：

- 运维管理后台（`smart-script-web-admin`）及其 `AjaxResult`/`GlobalExceptionHandler` 文案
- 后端错误码、HTTP 状态码、响应信封、审计日志字段
- 成功提示、业务状态标签、表单静态校验文案
- 后端日志中的异常详情

## 语气规范

- 简洁、正常、礼貌，不使用「呢/哦/吧」等过度口语语气词。
- 不指责用户，不出现「你错了」「非法」等措辞。
- 不出现 HTTP 状态码、英文原文、异常类名、堆栈、SQL、文件路径、内部字段名。
- 尽量说明「发生了什么 + 用户可以怎么做」；无法细分时使用统一兜底。
- 示例：
  - 实名信息不合规：`您的信息不符合规范，请检查后重试`
  - 网络异常：`网络连接异常，请检查网络后重试`
  - 服务异常：`服务暂时不可用，请稍后再试`

## 文案目录

| 场景 / 错误码 | 文案 |
| --- | --- |
| 通用参数错误 / 40000 | 您的信息不符合规范，请检查后重试 |
| 实名姓名/证件号不匹配 | 姓名或身份证号不正确，请核对后重试 |
| 手机号格式错误 | 手机号格式不正确，请检查后重试 |
| 短信验证码错误 40001 | 验证码错误，请重新输入 |
| 短信验证码过期 40002 | 验证码已过期，请重新获取 |
| 短信验证码已使用 40901 | 验证码已使用，请重新获取 |
| 手机号/邮箱已注册 40902 | 该手机号或邮箱已注册，请直接登录 |
| 操作频繁/短信频控 42901、42902 | 操作过于频繁，请稍后再试 |
| 手机号或密码错误 | 手机号或密码错误，请重试 |
| 密码不合规 | 密码需为 8-64 位，且同时包含字母和数字 |
| 密码过长 | 密码过长，请修改后重试 |
| 原密码错误 | 原密码不正确，请重新输入 |
| 新旧密码相同 | 新密码不能与原密码相同 |
| 账号停用 40301 | 账号暂时无法使用，请联系客服或管理员 |
| 未登录/登录过期 40100、HTTP 401 | 登录状态已过期，请重新登录 |
| 无权操作 40300、HTTP 403 | 暂无权限执行此操作 |
| 资源不存在 40400、HTTP 404 | 内容不存在或暂时不可用 |
| 文件过大 41300、HTTP 413 | 文件过大，请更换后重试 |
| 重复提交 40906 | 请勿重复提交 |
| 实名状态冲突 40905 | 实名认证正在审核或已通过，请勿重复提交 |
| 昵称已被使用 40907 | 该昵称已被使用，请更换后重试 |
| 创作者申请冲突 40908 | 创作者申请正在审核或已通过，请勿重复提交 |
| 其他状态冲突 409 | 当前状态无法执行此操作，请刷新后重试 |
| 协议版本不一致 40903 | 协议已更新，请重新阅读并同意 |
| 上传失败 | 上传失败，请稍后再试 |
| 服务异常 50000、HTTP 500/502/503 | 服务暂时不可用，请稍后再试 |
| 网络连接失败 | 网络连接异常，请检查网络后重试 |
| 请求超时 | 请求超时，请稍后再试 |
| 响应格式异常/无法解析 | 服务暂时不可用，请稍后再试 |

## 架构

### 1. 后端业务文案层（主来源）

`AppAuthExceptionHandler.safeMessage(code, fallback)` 改为中文目录：

- 有固定语义的 A3/A5 错误码直接返回上表文案，忽略异常自带文案。
- `AppAuthErrorCodes.PARAM`：如果 `fallback` 是安全中文（含中文字符、不含技术特征），保留它，
  用于实名等需要具体提示的场景；否则返回通用文案。
- 其他未知码：走统一消毒；英文/技术内容回退到通用文案。
- `MethodArgumentNotValidException`、非法参数、`RuntimeException` 的文案改为中文。
- 删除 `leakFree` 的英文回退 `"error"`，改为中文兜底。

服务层文案同步修改：

- `AppRealNameService`：`姓名或身份证号不正确，请核对后重试`（姓名/证件号分别可细化）。
- `AppAuthenticationService`：密码策略、原密码、新旧密码、手机号/密码错误、账号停用。
- `AppRuoYiSessionService`：登录过期、凭证域/权限、账号停用。
- `AppPhoneChangeService`、`AppUserProfileService`、`AppFileUploadService`：停用账号、上传失败等。
- `AppAuthController`、`PcUnifiedLoginController`、`AppAuthEntryPoint`、`AppAuthAuthenticationFilter`：
  `unauthorized`、`invalid request`、`internal error` 等。
- App 域的 `AppApiResponse.error` / `AjaxResult.error` 中残留英文（`invalid request`、
  `unauthorized`、`internal error`）改为中文。

不改：

- `AppAdminException`、管理端控制器、`GlobalExceptionHandler` 的运维文案。
- 错误码、HTTP 状态、响应结构、日志事件字段。
- A3 契约中的错误码定义；仅 `msg` 文案变化，契约文档补充说明。

### 2. App 兜底层

新增 `lib/core/network/friendly_error.dart`：

- `String friendlyMessage(Object error, {int? status, int? code})`
- `String sanitizeBackendMessage(String? message, {required String fallback})`
- HTTP 状态映射：400/401/403/404/405/413/429/500/502/503。
- 网络类型映射：连接超时、发送/接收超时、连接失败、证书异常、取消。
- 消毒规则：无中文且含 ASCII 字母 → 视为英文文案；含 `HTTP`、`Exception`、`null`、
  `undefined`、`Traceback`、URL/路径特征 → 使用兜底；含中文且无技术特征 → 保留。

`ApiClient`：

- `_send` / `postStream` 在无可用后端消息、响应非 Map、HTTP 非 2xx 时使用 `friendlyMessage`。
- 保留 401/40301 的会话失效信号逻辑不变。
- `_mapDioError` 文案改为上表。

`AuthFeedback`：

- 按错误码补充/更新文案；账号停用、验证码、登录失败等与后端目录一致。
- 页面层不再直接拼接 `$e` 或 `HTTP ${status}`；统一调用 `friendlyMessage`。

### 3. PC 创作者端 / 企业端兜底层

两端各新增 `src/utils/friendly-error.js`，内容一致：

- `sanitizeMessage(message, fallback)`
- `friendlyErrorMessage({ status, code, message, fallback })`
- HTTP/网络/超时/JSON 解析失败映射与 App 目录一致。

`src/auth.js` 的 `request()`：

- 捕获 `fetch` 抛出的网络错误、`AbortError`（超时）、非 JSON 响应。
- 后端 `msg`/`message` 先消毒；英文/技术内容替换为状态兜底。
- 抛出 `Error` 时保留 `status`/`code`，仅替换 `message`。

`src/api/creator.js`：

- 流式生成失败去掉 `生成失败（HTTP ${status}）`，改用友好文案。
- 解析 `event:error` 的 `message` 时先消毒。

页面层：

- 去掉 `文本加载失败（404）`、`下载失败（500）` 等技术后缀。
- `handleError` 兜底改为 `操作未完成，请稍后再试`。
- 直接展示 `failure.message` 的地方无需改动，但此时 `message` 已被统一消毒。

## 优先级

1. 后端安全中文文案
2. 客户端按错误码映射的文案
3. 客户端按 HTTP 状态/网络类型映射的兜底
4. 通用兜底「操作未完成，请稍后再试」

任何情况下不得展示英文、HTTP 状态码、异常类名或堆栈。

## 测试

- 后端：
  - `AppAuthExceptionHandler`：逐码断言中文文案；英文 fallback → 通用文案；安全中文 fallback → 保留。
  - `AppRealNameServiceTest`：姓名/证件号错误文案。
  - 登录/认证相关测试更新英文断言。
  - 跑 `mvn -pl smartscript-user -am test` 及相关模块。
- App：
  - 新增 `friendly_error_test.dart`：HTTP 状态、网络异常、英文消毒、中文保留。
  - 更新 `auth_account_disabled_test.dart`、`auth_forms_matrix_test.dart`、
    `user_center_states_test.dart` 等文案断言。
  - 跑 `flutter test`。
- PC 两端：
  - 新增 `tests/friendly-error.test.js`：状态/网络/英文消毒/中文保留。
  - 更新 `tests/auth.test.js` 中受影响的断言。
  - 跑 `npm test`、`npm run lint`、`npm run typecheck`、`npm run build`。
- 手工验收：
  - 实名姓名/证件号错误
  - 错误密码、验证码错误/过期
  - 404、500、断网、超时
  - AI 流式生成失败、材料/文件下载失败
  - 确认页面不出现英文、HTTP 状态码、异常类名或堆栈。

## 验收标准

- App、创作者 PC、企业 PC 的请求错误/校验错误/网络错误均为中文正常语气。
- 后端错误码、HTTP 状态、审计日志和既有客户端分支逻辑不变。
- 管理后台提示不受影响。
- 后端、App、PC 两端测试与构建通过。
