// Align with AgentChatRequest's limits. Keep complete successful exchanges only.
const MAX_MESSAGE = 2000;
const REQUEST_TIMEOUT = 240000;

const buildHistory = (exchanges, question) => {
  const history = [];
  let length = question.length;
  for (let i = exchanges.length - 2; i >= 0; i -= 2) {
    const user = exchanges[i], assistant = exchanges[i + 1];
    if (!user || !assistant || user.role !== 'user' || assistant.role !== 'assistant') continue;
    if (typeof user.content !== 'string' || typeof assistant.content !== 'string') continue;
    if (!user.content.trim() || !assistant.content.trim() || user.content.length > 4000 || assistant.content.length > 4000) continue;
    const pairLength = user.content.length + assistant.content.length;
    if (history.length + 2 > 20 || length + pairLength > 20000) break;
    history.unshift({ role: 'user', content: user.content }, { role: 'assistant', content: assistant.content });
    length += pairLength;
  }
  return history;
};

const appendExchange = (history, question, answer) => buildHistory([
  ...history, { role: 'user', content: question }, { role: 'assistant', content: answer }
], '');

const errorMessage = error => {
  const status = error && (error.statusCode || error.code);
  if (status === 504 || /timeout/i.test((error && error.errMsg) || '')) return '回答等待超时，请稍后重试。';
  if (status === 502 || status === 503) return '学业助手暂时不可用，请稍后重试。';
  if (status === 403) return '当前账号无权使用学业助手，请使用学生账号登录。';
  if (status === 404) return '学业助手暂时不可用，请联系管理员。';
  return (error && (error.message || (error.data && error.data.message))) || '无法连接学业助手，请检查网络后重试。';
};

module.exports = { MAX_MESSAGE, REQUEST_TIMEOUT, buildHistory, appendExchange, errorMessage };
