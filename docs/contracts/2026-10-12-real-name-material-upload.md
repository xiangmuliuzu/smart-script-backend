# 实名认证材料上传契约补充

已按用户确认的 App 实名材料上传方案实现；与现有实名审核状态机共用。

- `POST /api/v1/users/me/real-name/material`：App 用户 Access Token 鉴权，multipart 字段 `file`，单张可解码图片，不超过 5 MB。
- 成功响应：`{code:200,message:"success",data:{materialRef:"realname/<当前用户ID>/<随机文件名>.<实际格式>"}}`。
- 支持实际内容为 JPG/JPEG、PNG、GIF、BMP；不根据 MIME 或文件扩展名认定图片有效性。
- 引用对应 `app.admin.material-storage-path` 私有根目录下的文件，不能直接当公开 URL 使用；审核端沿用现有短时授权读取。
- 空文件/无效图片返回 400；超限返回 413；审核中/已通过上传返回 409；写入失败返回 500。响应沿用 App 错误信封。
- 原 `POST /api/v1/users/me/real-name` 和 `/resubmit` 继续接收姓名、身份证号和 `materialRefs`。新提交必须引用本用户上传且实际存在的材料；不能提交其他用户、外部 URL 或公开头像目录的地址。
- 当前材料列保存一份引用，App 只提交一张图片。本次不新增数据库结构，不要求身份证正反面或手持照等额外业务规则。
- 历史审核记录读取保持原有材料网关机制；客户端仍使用旧的手填引用方式时，需要更新至新版 App。
