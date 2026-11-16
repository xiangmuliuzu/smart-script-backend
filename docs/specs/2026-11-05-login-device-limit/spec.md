# 用户登录最大设备数（3 台）Spec

## Why

当前 App 凭证域的登录态完全共存：每次登录都由 `TokenService.createToken` 签发独立 token 并写入
`login_tokens:<uuid>`，没有任何按用户聚合的会话索引，也没有设备数上限。同一账号可以在任意多台
设备/浏览器上同时保持登录，旧会话只有在改密、重置、换绑、停用、改角色或主动退出时才会被清除。

产品要求：同一账号最多同时保留 **3 台设备**的登录态。第 4 台登录成功，最久未活跃的那台被踢下线。

## 范围

- 仅 App 凭证域（`credentialDomain = "app"`）：
  - App 短信登录、密码登录、注册即登录
  - PC 用户端 `POST /api/v1/auth/login`
  - PC 统一登录 `PcUnifiedLoginController`
  - 企业端复用同一 App 凭证域
- 管理后台（`credentialDomain = "admin"`）不受影响，仍由若依原有会话机制管理。
- 上限可配置：`app.auth.max-devices`，默认 `3`，环境变量 `APP_AUTH_MAX_DEVICES`；设为 `0` 表示不限制。

## 设备口径

当前客户端没有可靠的设备唯一标识：

| 客户端 | deviceId 实际值 |
| --- | --- |
| Flutter App | 固定 `flutter-device`（`auth_providers.dart`） |
| PC 用户端 / 企业端 | 固定 `pc-web`（`auth.js`） |
| PC 密码登录 `LoginBody` | 不传 |

因此本轮按**有效登录会话**计数，而不是物理设备 ID。后果：

- 同一台设备重新登录（未先退出）会新增一个会话并占用名额；旧会话因不再活跃，在达到上限时会
  作为最久未活跃会话被优先踢掉。
- 若后续要求严格按物理设备去重，需要客户端生成并持久化唯一 `deviceId`，另行立项。

## 行为

1. 登录成功照常签发新 token，登录响应与协议不变。
2. 签发后立即把该用户的 App 会话裁剪到最多 `max-devices` 个。
3. 超限时按会话的 `expireTime` 升序踢除，即最久未活跃的会话先下线；当前新会话永不参与本轮裁剪。
4. 被踢会话的下一次请求由现有 `TokenService.getLoginUser` 判定为无效，返回现有 401
   「login expired, please sign in again」；不新增错误码。
5. `max-devices = 0` 时完全跳过裁剪。

## 架构

### 1. `LoginSessionRegistry`（ruoyi-framework）

新增会话索引组件，按「凭据域 + 用户」聚合会话：

- Redis ZSET key：`login_user_sessions:<domain>:<userId>`
  - member：会话 UUID（`LoginUser.token`，不是 JWT）
  - score：会话过期时间 `LoginUser.expireTime`（毫秒）
- `add(LoginUser)`：`ZADD` + 刷新索引 TTL（会话 TTL + 60 分钟缓冲）。
- `remove(LoginUser)`：`ZREM`；索引为空时删除 key。
- `trimTo(domain, userId, max, keepToken)`：
  1. `ZRANGE ... WITHSCORES` 读取全部成员（正常情况下 ≤ max + 1）。
  2. 剔除 `login_tokens:<uuid>` 已不存在的死成员（管理端强制下线、过期等）。
  3. 若成员数 ≤ max，直接返回。
  4. 按 score 升序，跳过 `keepToken`，逐个删除 `login_tokens:<uuid>` 并 `ZREM`，直到剩余 `max` 个。
  5. 索引为空时删除 key。

索引前缀与现有 `login_tokens:*` 分离，避免 `revokeUserSessions` / 在线用户列表的全量扫描把
ZSET 当成 `LoginUser` 反序列化。

### 2. `TokenService`（ruoyi-framework）

- `refreshToken`：写会话缓存后调用 `registry.add(loginUser)`（创建与续期共用）。
- `verifyToken`：仅当 `setCacheObjectIfPresent` 成功续期时调用 `registry.add`，避免把已删除的会话
  重新写回索引。
- `delLoginUser(sessionId)`：删除会话前先按 UUID 取出 `LoginUser`，再删除缓存与索引成员。
- `revokeUserSessions(userId, domain, keepToken)`：删除目标会话时同步移除索引成员。
- 新增按会话 UUID 读取 `LoginUser` 的内部方法，供上述路径复用（`getLoginUser(String)` 入参是 JWT，
  两者不可混用）。

### 3. `AppRuoYiSessionService.issue`（smartscript-user）

- 创建 token 后调用：
  `registry.trimTo("app", user.getUserId(), properties.getMaxDevices(), loginUser.getToken())`
- 只影响 App 域；管理端 Token 仍走原路径，不裁剪。

### 4. 配置

- `AppAuthProperties` 新增 `int maxDevices = 3`。
- `application.yml` 增加：

```yaml
app:
  auth:
    max-devices: ${APP_AUTH_MAX_DEVICES:3}
```

## 数据流

```
登录成功
  → TokenService.createToken
      → 写 login_tokens:<uuid>
      → registry.add(loginUser)
  → registry.trimTo("app", userId, 3, 当前token)
      → 剔除死成员
      → 超过 3 个则删除最久未活跃会话
  → 返回 token
```

后续请求：

```
Authorization: Bearer <jwt>
  → TokenService.getLoginUser(jwt) 校验 Redis 会话
  → 临近过期时 verifyToken 续期并更新 ZSET score
```

## 边界与失败处理

- **并发登录**：每次登录都是「先加入、后裁剪到上限」，最终不会超过 3；两个登录同时裁剪时可能多踢
  一台，但不会突破上限。不引入分布式锁。
- **上线过渡**：上线前已存在的会话不在索引中，其续期时会自动补入；上线后首次登录可能短暂超过 3，
  随后收敛。
- **管理端强制下线**：`SysUserOnlineController.forceLogout` 直接删 Redis key，不同步索引；下次
  `trimTo` 按 `login_tokens:<uuid>` 是否存在自动剔除。
- **Redis 故障**：登录/会话本就依赖 Redis，沿用现有失败行为，不额外降级。
- **现有吊销逻辑**：退出、首次设密码（保留当前）、改密、重置、换绑手机号/邮箱、停用/删除/改角色
  行为保持不变，只是额外同步索引。

## 测试

- `LoginSessionRegistryTest`：
  - add / remove / TTL
  - 3 个会话时 trim 踢掉 score 最小者
  - `keepToken` 永不被踢
  - 死成员被剔除且不占名额
- `RuoYiTokenDomainTest`：
  - createToken、verifyToken 续期、delLoginUser、revokeUserSessions 同步索引
  - 现有域校验、过期、吊销用例不回归
- `AppRuoYiSessionServiceTest`：
  - 正常登录调用 trim，参数为 `app` / userId / maxDevices / 新 token
  - `maxDevices = 0` 不调用 trim
- 回归：`AppRuoYiLoginProtocolTest`、`AppRuoYiRevocationTest`、登录相关单测。

## 明确不做

- 不改 App / PC 前端的 deviceId 生成与持久化逻辑。
- 不新增设备列表、设备管理、远程下线接口。
- 不修改管理端会话上限或在线用户功能。
- 不引入 MySQL 设备表，不新增数据库迁移。
- 不做"第 4 台拒绝登录"模式（产品已确认采用踢最久未活跃）。
