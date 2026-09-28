# API 接口文档

## 通用说明

- 基础路径：`http://localhost:8081`
- 请求数据格式：
  - `application/json`：普通 JSON 请求体
  - `multipart/form-data`：文件上传
- 通用 JSON 返回格式：

```json
{
  "code": 200,
  "message": "成功",
  "data": {}
}
```

- 通用字段说明：
  - `code`：业务状态码，`200` 表示成功，`500` 表示服务端异常。
  - `message`：返回消息。
  - `data`：返回数据；无数据时为 `null`。

## 认证与修改密码

### 管理后台登录

`POST /api/geeker/login`

请求体中的密码不做前端 MD5；生产环境必须通过 HTTPS 传输，由服务端统一校验并迁移为 BCrypt。

```json
{
  "username": "admin",
  "password": "your-password"
}
```

### 管理员修改密码

`POST /api/geeker/user/change_password`

该接口要求管理员 JWT。服务端校验原密码，新密码必须为 6-72 位且不能与原密码相同；修改成功后客户端应清除当前令牌并重新登录。

```json
{
  "oldPassword": "current-password",
  "newPassword": "new-strong-password"
}
```

## 1. 健康检查

### 接口地址

`GET /api/health`

### 请求方式

`GET`

### 请求参数

无

### 返回格式

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | number | 业务状态码 |
| message | string | 返回消息 |
| data | null | 固定为 `null` |

### 示例 JSON

```json
{
  "code": 200,
  "message": "系统运行正常",
  "data": null
}
```

## 2. 数据库连接测试

### 接口地址

`GET /api/test/db`

### 请求方式

`GET`

### 请求参数

无

### 返回格式

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | number | 业务状态码 |
| message | string | 返回消息 |
| data | string | 数据库连接测试结果 |

### 示例 JSON

```json
{
  "code": 200,
  "message": "数据库连接正常",
  "data": "MySQL connected successfully"
}
```

## 3. 用户数量测试

### 接口地址

`GET /api/test/user-count`

### 请求方式

`GET`

### 请求参数

无

### 返回格式

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | number | 业务状态码 |
| message | string | 返回消息 |
| data | number | `t_user` 表中的用户数量 |

### 示例 JSON

```json
{
  "code": 200,
  "message": "用户表查询正常",
  "data": 12
}
```

## 4. 文件上传

### 接口地址

`POST /api/file/upload`

### 请求方式

`POST`

### 请求参数

Content-Type：`multipart/form-data`

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| file | file | 是 | 上传的文件 |
| businessType | string | 否 | 业务类型 |

上传人 ID 由后端根据 JWT 中的当前登录用户自动填写，客户端不能指定。

### 返回格式

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | number | 业务状态码 |
| message | string | 返回消息 |
| data | object | 文件上传结果 |
| data.id | number | 文件记录 ID |
| data.originalName | string | 原始文件名 |
| data.storedName | string | 服务端存储文件名 |
| data.filePath | string | 服务端文件路径 |
| data.fileType | string | 文件 MIME 类型 |
| data.fileSize | number | 文件大小，单位字节 |
| data.businessType | string | 业务类型 |

### 示例 JSON

```json
{
  "code": 200,
  "message": "文件上传成功",
  "data": {
    "id": 1716192000000,
    "originalName": "transcript.pdf",
    "storedName": "550e8400-e29b-41d4-a716-446655440000.pdf",
    "filePath": "uploads/550e8400-e29b-41d4-a716-446655440000.pdf",
    "fileType": "application/pdf",
    "fileSize": 245760,
    "businessType": "transcript"
  }
}
```

## 5. 文件下载

### 接口地址

`GET /api/file/download/{fileId}`

### 请求方式

`GET`

### 请求参数

路径参数：

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| fileId | number | 是 | 文件记录 ID |

### 返回格式

成功时返回文件二进制流：

- Content-Type：`application/octet-stream`
- Content-Disposition：`attachment; filename*=UTF-8''文件名`

文件不存在时返回 HTTP `404`。

服务端异常时返回通用 JSON：

```json
{
  "code": 500,
  "message": "服务器内部错误：错误详情",
  "data": null
}
```

### 示例 JSON

该接口成功时不返回 JSON。异常返回示例：

```json
{
  "code": 500,
  "message": "服务器内部错误：Incorrect result size: expected 1, actual 0",
  "data": null
}
```

## 6. AI 问答

### 接口地址

`POST /api/student/ai/ask`

### 请求方式

`POST`

### 请求参数

Content-Type：`application/json`

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| question | string | 是 | 学生提问内容 |
| studentNo | string | 是 | 学号 |

请求示例：

```json
{
  "question": "我的培养方案还差哪些课程？",
  "studentNo": "20240001"
}
```

### 返回格式

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | number | 业务状态码 |
| message | string | 返回消息 |
| data | object | AI 服务返回结果，按外部 AI 服务原样透传 |

### 示例 JSON

```json
{
  "code": 200,
  "message": "AI问答调用成功",
  "data": {
    "answer": "根据当前培养方案，你还需要完成专业必修课和实践环节相关学分。",
    "studentNo": "20240001"
  }
}
```

## 7. 更新 AI 知识库

### 接口地址

`POST /api/admin/ai/ingest-all`

### 请求方式

`POST`

### 请求参数

无

### 返回格式

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | number | 业务状态码 |
| message | string | 返回消息 |
| data | object | AI 服务返回结果，按外部 AI 服务原样透传 |

### 示例 JSON

```json
{
  "code": 200,
  "message": "知识库更新调用成功",
  "data": {
    "status": "success",
    "message": "知识库已更新"
  }
}
```

## 8. 查询培养方案列表

### 接口地址

`GET /api/training-plan/list`

### 请求方式

`GET`

### 请求参数

无

### 返回格式

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | number | 业务状态码 |
| message | string | 返回消息 |
| data | array | MySQL 中保存的培养方案列表，按最近更新时间倒序排列 |

### 示例 JSON

```json
{
  "code": 200,
  "message": "成功",
  "data": [{
    "id": 1720000000001,
    "major": "计算机科学与技术",
    "grade": "2024",
    "version": "v1.0",
    "jsonContent": "{\"courses\":[...]}",
    "courseCount": 1,
    "totalCredits": 4
  }]
}
```

## 9. 保存培养方案

### 接口地址

`POST /api/training-plan/save`

### 请求方式

`POST`

### 请求参数

Content-Type：`application/json`

请求体包含培养方案索引字段和完整 JSON。`id` 为空时新增，存在时修改。后端会解析 `jsonContent`，重新计算课程数和总学分，并要求至少存在一门课程类别包含“核心”的课程。

示例：

```json
{
  "id": null,
  "major": "计算机科学与技术",
  "grade": "2024",
  "version": "v1.0",
  "remark": "2024 版培养方案",
  "jsonContent": "{\"major\":\"计算机科学与技术\",\"grade\":\"2024\",\"version\":\"v1.0\",\"courses\":[{\"category\":\"专业核心课\",\"courseName\":\"程序设计基础\",\"credits\":4,\"offeredAt\":\"1\"}]}"
}
```

### 返回格式

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | number | 业务状态码 |
| message | string | 返回消息 |
| data | number | 新增或修改后的培养方案 ID |

### 示例 JSON

```json
{
  "code": 200,
  "message": "培养方案保存成功",
  "data": 1720000000001
}
```

培养方案详情使用 `GET /api/training-plan/{id}`，删除使用 `DELETE /api/training-plan/{id}`。培养方案只保存在 MySQL；Python 预警服务不再维护独立的内存副本。

## 10. 学业预警分析

### 接口地址

`POST /api/student/warning/analyze`

### 请求方式

`POST`

### 请求参数

Content-Type：`multipart/form-data`

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| file | file | 是 | 成绩单文件 |
| studentNo | string | 管理员代查时必填 | 学生登录时从 JWT 获取学号；管理员代查时传目标学号 |

### 返回格式

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | number | 业务状态码 |
| message | string | 返回消息 |
| data | object | 学业预警服务返回的课程解析和预警报告 |

### 示例 JSON

```json
{
  "code": 200,
  "message": "学业预警分析成功",
  "data": {
    "status": "success",
    "data": {
      "course_count": 12,
      "courses": [],
      "report": {
        "warning_level": "一般预警",
        "total_earned_credits": 82.5,
        "core_courses": [],
        "failed_courses": [],
        "missing_core_courses": [],
        "course_suggestions": []
      }
    }
  }
}
```

分析成功后，Java 后端会保存成绩单文件，并把学生用户 ID、成绩单文件 ID、实际使用的培养方案 ID 和完整分析结果写入 `t_warning_record`。

## 11. 管理端发布通知

`POST /api/notification/save`

```json
{
  "id": null,
  "title": "关于选课的通知",
  "content": "请按时完成选课",
  "tags": "教学,选课",
  "is_urgent": true,
  "file_id": 1716192000000
}
```

`id` 为空时新增，存在时修改。`file_id` 必须指向 `businessType=notice` 的文件，发布人由后端根据管理员 JWT 填写。修改通知会清空已有确认状态，学生需要重新确认。

管理端列表使用 `GET /api/notification/list`，删除使用 `DELETE /api/notification/{id}`，回执明细使用 `GET /api/notification/{id}/receipts`。列表会返回 `confirmed_count` 和 `total_count`。

## 12. 学生查看与确认通知

学生通知列表：`GET /api/student/notice/list`。学生身份从 JWT 获取，不接受学生端代查其他学号。

确认通知：`POST /api/student/notice/confirm`

```json
{
  "notificationId": 1716192000001
}
```

确认操作具有幂等性；重复提交不会重复新增回执。通知不存在、学生不存在或账号停用时会拒绝确认。

