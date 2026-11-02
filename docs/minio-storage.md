# MinIO 对象存储接入说明

## 目标

解决「共享数据库 + 各自本地磁盘」导致的上传文件丢失：印章图、封面上传到 A 机器后，B 机器或生产服务器访问 `/profile/**` 时拿不到文件，表现为破图。

改造后：

- 数据库仍然只存 `/profile/<key>` 相对地址，App、网页端无需改动；
- 上传先落本地 `ruoyi.profile`（保留现有上传校验、PDF 渲染逻辑），随后同步到 MinIO；
- 读取时本地优先，本机没有就从 MinIO 拉回并缓存在本地；
- 未启用 MinIO 时所有逻辑退化为纯本地磁盘，行为与历史版本一致。

## 配置

在部署环境注入以下变量（`application.yml` 已给出默认映射）：

```text
MINIO_ENABLED=true
MINIO_ENDPOINT=http://127.0.0.1:9000
MINIO_ACCESS_KEY=<access-key>
MINIO_SECRET_KEY=<secret-key>
MINIO_BUCKET=smartscript-profile
MINIO_PRIVATE_BUCKET=smartscript-private
MINIO_AUTO_CREATE_BUCKET=true
```

说明：

- 两个桶都保持**私有**。公开图片仍由后端 `/profile/**` 代理下发，浏览器/App 不直接访问 MinIO，也不需要配置 CORS 或公开读策略。
- `MINIO_ENABLED=true` 时启动会校验 endpoint / 密钥 / 桶，并默认自动建桶；MinIO 不可达会直接启动失败，避免运行时才发现上传失败。
- 桶名只是默认值，可按环境覆盖。
- `MINIO_ENDPOINT` 必须是**后端进程**能访问的地址：后端在容器里时不要填 `127.0.0.1`，应填容器网络里的服务名（如 `http://minio:9000`）或宿主机地址。
- 本地 `ruoyi.profile`（默认 `uploadPath/`）仍会保留文件，作为上传暂存和读取缓存；生产建议给它一个持久目录，减少回源次数。

## 历史文件迁移

1. 安装 [MinIO Client `mc`](https://min.io/docs/minio/linux/reference/minio-mc.html)。
2. 在仓库根目录执行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/migrate-to-minio.ps1
```

脚本会：

- 创建 `smartscript-profile` / `smartscript-private` 桶（若不存在）；
- 把 `RUOYI_PROFILE`（默认 `uploadPath`）下所有文件按原相对路径 `mc mirror` 到公开桶；
- 把 `APP_ADMIN_MATERIAL_STORAGE_PATH`（默认 `private-materials`）下所有文件镜像到私有桶。

也可以在部署机上手工执行等价命令：

```bash
mc alias set smartscript "$MINIO_ENDPOINT" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY"
mc mb --ignore-existing smartscript/smartscript-profile
mc mirror --overwrite ./uploadPath smartscript/smartscript-profile
mc mb --ignore-existing smartscript/smartscript-private
mc mirror --overwrite ./private-materials smartscript/smartscript-private
```

对象 key 与本地相对路径一一对应：`uploadPath/upload/2026/11/02/a.jpg` → 对象 `upload/2026/11/02/a.jpg` → 地址 `/profile/upload/2026/11/02/a.jpg`。

> **每个曾经上传过文件的后端实例/开发机都要执行一次迁移脚本**（都指向同一个 MinIO）。共享数据库里可能还留着“某台机器本地上传、其他机器没有”的文件，只迁移生产机不够。

> 已经丢失的历史文件（数据库有记录、任何机器上都没有原图）无法靠迁移恢复，只能由所属账号重新上传。

## 验证

1. 以 `MINIO_ENABLED=true` 启动后端。
2. 在 App 或网页端上传一张印章图/封面。
3. 确认对象已写入：

```bash
mc ls smartscript/smartscript-profile/upload/2026/11/02/
```

4. 直接请求图片地址应返回 `image/*` 而不是 JSON：

```bash
curl -I https://<domain>/profile/upload/2026/11/02/<file>
```

5. 换一台没有本地缓存的机器打开同一条记录，应能正常显示。

## 回滚

把 `MINIO_ENABLED` 改回 `false` 并重启即可回到纯本地模式。若要保留已上传文件，先从桶同步回本地：

```bash
mc mirror smartscript/smartscript-profile ./uploadPath
mc mirror smartscript/smartscript-private ./private-materials
```

## 注意事项

- 所有后端实例必须指向同一个 MinIO；不要再让某台机器的本地目录成为唯一文件来源。
- 本地缓存和 MinIO 之间按对象 key + lastModified 校验；MinIO 暂时不可达时读取会降级使用本地缓存。
- 若 MinIO 开启了版本控制/生命周期，请保留 `contract/` 下的合同产物，避免签署 PDF 被提前归档删除。
- 私有材料（实名认证）写入 `MINIO_PRIVATE_BUCKET`，只能经后端鉴权接口读取，不会出现在 `/profile/**`。
