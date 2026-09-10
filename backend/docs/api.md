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
  "data": "Kingbase connected successfully"
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
| uploaderId | number | 否 | 上传人 ID |

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

## 8. 查询培养方案

### 接口地址

`GET /api/admin/warning/training-plan`

### 请求方式

`GET`

### 请求参数

无

### 返回格式

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | number | 业务状态码 |
| message | string | 返回消息 |
| data | object | 学业预警服务返回的培养方案数据，按外部服务原样透传 |

### 示例 JSON

```json
{
  "code": 200,
  "message": "培养方案查询成功",
  "data": {
    "major": "计算机科学与技术",
    "grade": "2024",
    "requiredCredits": 160,
    "courses": [
      {
        "courseCode": "CS101",
        "courseName": "程序设计基础",
        "credits": 4
      }
    ]
  }
}
```

## 9. 保存培养方案

### 接口地址

`POST /api/admin/warning/training-plan`

### 请求方式

`POST`

### 请求参数

Content-Type：`application/json`

请求体为培养方案 JSON，当前后端按 `Object` 接收并透传到学业预警服务。

示例：

```json
{
  "major": "计算机科学与技术",
  "grade": "2024",
  "requiredCredits": 160,
  "courses": [
    {
      "courseCode": "CS101",
      "courseName": "程序设计基础",
      "credits": 4
    }
  ]
}
```

### 返回格式

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | number | 业务状态码 |
| message | string | 返回消息 |
| data | object | 学业预警服务返回结果，按外部服务原样透传 |

### 示例 JSON

```json
{
  "code": 200,
  "message": "培养方案保存成功",
  "data": {
    "status": "success",
    "message": "培养方案已保存"
  }
}
```

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
| studentNo | string | 是 | 学号 |

### 返回格式

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| code | number | 业务状态码 |
| message | string | 返回消息 |
| data | object | 学业预警服务返回的分析结果，按外部服务原样透传 |

### 示例 JSON

```json
{
  "code": 200,
  "message": "学业预警分析成功",
  "data": {
    "studentNo": "20240001",
    "riskLevel": "low",
    "missingCredits": 8,
    "suggestions": [
      "优先补修专业必修课",
      "下学期完成实践环节学分"
    ]
  }
}
```

