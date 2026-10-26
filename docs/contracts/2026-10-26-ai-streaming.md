# AI 创作流式输出

App 与 PC 创作者端共用以下受认证保护的 POST 接口，入参沿用对应同步接口：

- `/api/v1/content/ai/outline/stream`
- `/api/v1/content/ai/write/stream`
- `/api/v1/content/ai/polish/stream`

响应为 UTF-8 `text/event-stream`，事件使用 LF 或 CRLF 分隔：

```text
event:message
data:{"delta":"逐步生成的内容"}

event:message
data:{"done":true,"recordId":123}

```

大纲的完成事件携带 `quotaUsed` 和 `requestNo`，写作与润色携带 `recordId`。只有收到 `done:true` 才确认生成完成；连接提前结束时保留已显示的文本并提示中断。错误事件为 `event:error`，data 包含 `code` 和 `message`。

大纲流直接生成分段可读文本，避免客户端等待完整 JSON。同步大纲接口保留原有结构化结果；流式大纲的最终结果与历史回看记录保留完整文本，次数校验、成功落库与扣次规则沿用原有服务。

后端使用 Spring MVC `StreamingResponseBody` 在异步请求中执行生成，保留请求身份与安全上下文，每个增量立即 flush，超时为 120 秒。响应包含 `Cache-Control: no-cache, no-transform` 和 `X-Accel-Buffering: no`，代理需要允许 SSE 及时透传。

本次修改涉及后端、App 和创作者端，生效时需同步更新后端服务、App 安装包和创作者端静态资源。
