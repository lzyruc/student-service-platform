// Only send the current message and an owned conversation ID; history lives on the server.
const MAX_MESSAGE = 2000;
const REQUEST_TIMEOUT = 240000;

const errorMessage = error => {
  const status = error && (error.statusCode || error.code);
  if (status === 504 || /timeout/i.test((error && error.errMsg) || '')) return '回答等待超时，请稍后重试。';
  if (status === 502 || status === 503) return '学业助手暂时不可用，请稍后重试。';
  if (status === 403) return '当前账号无权使用学业助手，请使用学生账号登录。';
  if (status === 404) return '会话不存在或已移除，请新建会话后重试。';
  if (status === 409) return '会话已收到新的消息，请刷新会话后重试。';
  return (error && (error.message || (error.data && error.data.message))) || '无法连接学业助手，请检查网络后重试。';
};

module.exports = { MAX_MESSAGE, REQUEST_TIMEOUT, errorMessage };
