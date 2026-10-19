# 阿里云实名认证接入

商品：[cmapi025518](https://market.aliyun.com/detail/cmapi025518)。
鉴权依据：[调用 API 商品](https://help.aliyun.com/zh/api-gateway/traditional-api-gateway/use-cases/call-apis)。
接口示例与返回码：[该商品调用示例](https://developer.aliyun.com/article/1024694)。

- 后端通过 HTTPS POST `https://idenauthen.market.alicloudapi.com/idenAuthentication`，表单字段为 `name`、`idNo`，请求头为 `Authorization: APPCODE <AppCode>`。使用此认证方式无需 AppKey/AppSecret。
- `ALIYUN_REAL_NAME_ENABLED=true` 启用；`ALIYUN_REAL_NAME_APPCODE` 只写入后端私有 `.env.local` 或部署环境变量。关闭时拒绝新提交，不再回退人工审核。
- 保留现有提交端点、材料上传和归属校验。参数、材料和当前状态校验全部通过后才调用；姓名与证件号仍只保存掩码，照片不发送给二要素接口。
- `respCode=0000` 通过；`0007`（号码不存在）、`0008`（不匹配）驳回；复用现有审核更新和站内通知，自动审核员为空。
- `0001`–`0004` 返回输入错误。维护、未知返回码、空或非法响应、网络或网关异常返回服务不可用，不默认通过，不自动重试。
- 只在用户主动提交或重提时调用；启动、查询状态、加载页面和构建不调用。提交事务锁定用户行，避免并发成功提交重复核验。
- 此次验收仅使用无真实凭据的 MockRestServiceServer 拦截请求，不发起真实核验，不消耗试用额度。试用包余量由阿里云控制台管理，本地不虚构剩余额度。
- App 根据返回状态显示认证结果；驳回时保留当前输入与材料，便于纠正后主动重提。

## 运维端只读记录

- 菜单“实名审核”改为“实名认证记录”，保留列表、筛选、详情及按原权限查看材料。
- 移除人工通过、驳回操作及 `PUT /api/v1/admin/real-name-applications/{applicationId}/decision`。
- 删除 `user:realname:audit` 按钮与角色授权，保留 `user:realname:list`、`user:realname:query`。历史认证记录不作修改。
