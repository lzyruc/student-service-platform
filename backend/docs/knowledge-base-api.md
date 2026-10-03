# 知识库政策文档后端接口

所有接口都需要管理员 JWT。请求头可使用：

```http
x-access-token: <管理员登录返回的 token>
```

## 文件与政策记录的关系

文件上传和政策登记分为两步：

1. 调用 `POST /api/file/upload`，上传参数 `businessType=policy`，从响应的 `data.id` 取得 `fileId`。
2. 调用 `POST /api/admin/knowledge/documents`，在 JSON 请求体中传入该 `fileId`。

后端会确认：

- `t_file` 中存在这条文件记录；
- 文件的 `business_type` 是 `policy`；
- 文件是 PDF、PNG、JPG 或 JPEG；图片会通过 OCR 提取文字；
- 同一个文件没有被其他政策文档使用；
- 当前 JWT 对应一个有效的管理员账号。

## 创建政策文档

```http
POST /api/admin/knowledge/documents
Content-Type: application/json
```

```json
{
  "title": "本科生学籍管理规定",
  "category": "学籍管理",
  "audience": "UNDERGRADUATE",
  "version": "v1.0",
  "effectiveDate": "2026-09-01",
  "expiryDate": null,
  "tags": ["本科", "学籍"],
  "content": null,
  "keywords": "本科,学籍,休学,复学",
  "officialUrl": "https://example.edu/policy/10",
  "remark": "教务处发布",
  "fileId": 10001
}
```

`audience` 可选值为 `ALL`、`UNDERGRADUATE`、`POSTGRADUATE`。未传时使用 `ALL`。新建记录的 `docStatus` 为 `DRAFT`，`ingestStatus` 为 `PENDING`。

## 查询

```http
GET /api/admin/knowledge/documents?page=1&pageSize=20
GET /api/admin/knowledge/documents?keyword=学籍&category=学籍管理
GET /api/admin/knowledge/documents?audience=UNDERGRADUATE&docStatus=PUBLISHED&ingestStatus=READY
GET /api/admin/knowledge/documents/{id}
```

分页响应的 `data` 包含 `records`、`total`、`page` 和 `pageSize`。单条记录同时返回政策信息、关联文件名、文件类型、文件大小、创建人及 RAG 入库状态。

## 修改、发布和删除

```http
PUT /api/admin/knowledge/documents/{id}
POST /api/admin/knowledge/documents/{id}/publish
DELETE /api/admin/knowledge/documents/{id}
```

`PUT` 使用与创建相同的 JSON 结构。修改后文档会回到 `DRAFT`，RAG 入库状态会回到 `PENDING`，等待后续重新向量化。

发布接口会先把 `docStatus` 改为 `PUBLISHED`、把 `ingestStatus` 改为 `PROCESSING`，然后由后台线程把关联文件发送到 Python RAG 服务：

```text
POST /api/admin/knowledge/documents/{id}/publish
  -> Java 后端读取 t_file 和磁盘文件
  -> POST Python /api/admin/ai/documents/{id}/ingest
  -> Python 解析、分块并替换该 policyId 的 Chroma 向量
  -> Java 回写 chunkCount、contentHash 和 lastIngestedAt
```

入库成功后 `ingestStatus=READY`；失败后 `ingestStatus=FAILED`，`ingestError` 保存错误原因。发布接口本身立即返回，管理端每 3 秒刷新一次处理中记录，避免浏览器等待 PDF 解析。

删除接口会先调用 Python RAG 服务清理该 `policyId` 的向量，成功后再删除 `t_policy_doc` 中的政策记录。同名的其他政策或版本不会被删除。`t_file` 文件记录和磁盘原文件仍然保留。正在入库的文档不能删除。

## 统一来源与旧政策迁移

- 原始文件由 Java 的 `uploads` 目录保管；MySQL 的 `t_file` 和 `t_policy_doc` 管理文件关联及政策状态。
- Python 只接收发布任务，把解析出的文本和向量写入 `chroma_db`，不会永久保存第二份 PDF。
- Java 在每次问答时查询 `PUBLISHED/READY` 政策 ID 并覆盖客户端传入的 `policyIds`；Python 按此白名单检索。
- 修改后文档回到 `DRAFT/PENDING`，即使旧向量尚未替换，也不会参与学生问答。空白名单不调用模型。
- 旧文件夹扫描和全量重建接口已移除；Python 首次运行新版代码时清理未登记的旧向量。

首次从 `backend` 目录启动新版 Java 时，`LegacyPolicyImport` 会将相邻的旧 `python-services/政策文件库` 中的 PDF 和图片复制进 `uploads`、登记为管理端政策，并提交发布任务。迁移需要已有启用的管理员；首次管理员初始化会先于迁移执行。可通过 `LEGACY_POLICY_DIR` 指定旧目录的绝对路径。

迁移按文件名和实际文件内容判断是否已经登记，重试不会重复创建相同政策。全部文件登记成功后，旧目录重命名为 `政策文件库.imported`，仅作为原文件备份；失败的注册保留原目录供重试，入库失败的已登记政策可在 Web 中重新发布。

更新时先重启 Python 问答服务，再重启新版 Java。迁移过程中管理端会显示“处理中”，待全部变为“已就绪”后再验证问答。已有新政策记录会保留，无需重新初始化 MySQL。

## 问答等待时间

模型生成比普通业务接口慢。默认模型请求超时为 60 秒（Python 的 `LLM_TIMEOUT`），Java 的问答专用读取超时为 90 秒（`EXTERNAL_AI_READ_TIMEOUT`），小程序问答请求超时为 120 秒。普通小程序接口仍为 15 秒，其他 Java 外部接口仍为 30 秒。前后端的外层等待时间大于模型请求时间，使模型服务有机会返回结果或错误。

问答生成不自动重试，避免超时后重复消耗模型请求。小程序显示等待状态，等待期间不能重复发送，并分别显示超时、登录失效和后端错误。
