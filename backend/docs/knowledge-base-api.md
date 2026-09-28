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
- 文件是 PDF；
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

发布接口会先把 `docStatus` 改为 `PUBLISHED`、把 `ingestStatus` 改为 `PROCESSING`，然后由后台线程把关联 PDF 发送到 Python RAG 服务：

```text
POST /api/admin/knowledge/documents/{id}/publish
  -> Java 后端读取 t_file 和磁盘 PDF
  -> POST Python /api/admin/ai/documents/{id}/ingest
  -> Python 解析、分块并替换该 policyId 的 Chroma 向量
  -> Java 回写 chunkCount、contentHash 和 lastIngestedAt
```

入库成功后 `ingestStatus=READY`；失败后 `ingestStatus=FAILED`，`ingestError` 保存错误原因。发布接口本身立即返回，管理端每 3 秒刷新一次处理中记录，避免浏览器等待 PDF 解析。

删除接口会先调用 Python RAG 服务清理该 `policyId` 和同名旧文档的向量，成功后再删除 `t_policy_doc` 中的政策记录。`t_file` 文件记录和磁盘 PDF 仍然保留，避免误删被其他业务引用的原文件。正在入库的文档不能删除。
