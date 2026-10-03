const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const chat = require('../utils/agent-chat.js');
const plain = value => JSON.parse(JSON.stringify(value));
const tick = () => new Promise(resolve => setImmediate(resolve));

function loadPage(overrides = {}) {
  const storage = { token: 'student-token', role: 'student', baseUrl: 'http://current-server:8081', ...overrides };
  const calls = [], redirects = [], toasts = [];
  let aborted = 0;
  const wx = {
    getStorageSync: key => storage[key],
    removeStorageSync: key => { delete storage[key]; },
    showToast: options => toasts.push(options.title),
    showNavigationBarLoading() {}, hideNavigationBarLoading() {},
    reLaunch: options => redirects.push(options.url), navigateTo() {},
    request: options => {
      calls.push(options);
      return { abort: () => { aborted++; options.fail({ errMsg: 'request:fail abort' }); } };
    }
  };
  const module = { exports: {} };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../utils/request.js'), 'utf8'), { module, wx });
  let page;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/agent/agent.js'), 'utf8'), {
    Page: definition => { page = definition; }, wx,
    require: name => name.includes('agent-chat') ? chat : module.exports
  });
  page.setData = changes => Object.assign(page.data, changes);
  page.onLoad();
  page.onShow();
  const reply = (index, answer = '依据当前数据给出的回答', status = 'COMPLETED') => calls[index].success({
    statusCode: 200, data: { code: 200, data: { answer, status, toolRounds: 1, toolCalls: [] } }
  });
  const ask = question => { page.data.inputValue = question; return page.sendMessage(); };
  return { page, storage, calls, redirects, toasts, reply, ask, aborted: () => aborted };
}

test('student chat uses real request wrapper, JWT and bounded timeout, never sends identity or greeting', async () => {
  const h = loadPage();
  const pending = h.ask('帮我分析最近学习情况');
  assert.equal(h.calls.length, 1);
  assert.equal(h.calls[0].url, 'http://current-server:8081/api/student/agent/chat');
  assert.equal(h.calls[0].method, 'POST');
  assert.equal(h.calls[0].header.Authorization, 'student-token');
  assert.equal(h.calls[0].timeout, 240000);
  assert.deepEqual(plain(h.calls[0].data), { message: '帮我分析最近学习情况', history: [] });
  await h.ask('重复点击');
  assert.equal(h.calls.length, 1);
  h.reply(0);
  await pending;
  assert.equal(h.page.data.asking, false);
  assert.equal(h.page.data.messages.at(-1).content, '依据当前数据给出的回答');
});

test('followup sends only successful user and assistant exchanges, a new HTTP request each time', async () => {
  const h = loadPage();
  const first = h.ask('最近表现');
  h.reply(0, '最新可用学期是秋季');
  await first;
  const second = h.ask('趋势呢');
  assert.deepEqual(plain(h.calls[1].data.history), [
    { role: 'user', content: '最近表现' }, { role: 'assistant', content: '最新可用学期是秋季' }
  ]);
  h.reply(1, '依据本次工具结果解释趋势');
  await second;
  assert.equal(h.calls.length, 2);
});

test('timeout restores question and is not included as a successful answer in followup history', async () => {
  const h = loadPage();
  const first = h.ask('概况');
  h.reply(0, '正常记录');
  await first;
  const failed = h.ask('挂科风险');
  h.calls[1].success({ statusCode: 504, data: { code: 504, message: 'request exceeded budget' } });
  await failed;
  assert.match(h.page.data.messages.at(-1).content, /等待超时/);
  assert.equal(h.page.data.inputValue, '挂科风险');
  assert.equal(h.page.data.asking, false);
  const retry = h.page.sendMessage();
  assert.deepEqual(plain(h.calls[2].data.history), [
    { role: 'user', content: '概况' }, { role: 'assistant', content: '正常记录' }
  ]);
  h.reply(2);
  await retry;
});

test('missing data remains visible but does not become a verified conversation fact', async () => {
  const h = loadPage();
  const first = h.ask('课程表现');
  h.reply(0, '请先上传成绩单', 'DATA_UNAVAILABLE');
  await first;
  assert.equal(h.page.data.messages.at(-1).notice, '暂未取得完整分析');
  const retry = h.ask('再看看');
  assert.deepEqual(plain(h.calls[1].data.history), []);
  h.reply(1);
  await retry;
});

test('tool limit displays partial answer rather than hiding acquired evidence', async () => {
  const h = loadPage();
  const pending = h.ask('完整分析');
  h.reply(0, '已有近期结果，趋势尚未获取', 'TOOL_LIMIT');
  await pending;
  assert.equal(h.page.data.messages.at(-1).notice, '本次仅取得部分结果');
  assert.equal(h.page._history.at(-1).content, '已有近期结果，趋势尚未获取');
});

test('anonymous or administrator cannot initiate chat and is sent to student login', async () => {
  for (const overrides of [{ token: '' }, { role: 'admin' }]) {
    const h = loadPage(overrides);
    await h.ask('查询学业');
    assert.equal(h.calls.length, 0);
    assert.equal(h.redirects.at(-1), '/pages/login/login');
  }
});

test('expired login clears conversation and identity while preserving server configuration', async () => {
  const h = loadPage();
  const first = h.ask('最近表现');
  h.reply(0);
  await first;
  const second = h.ask('再看趋势');
  h.calls[1].success({ statusCode: 401, data: { code: 401, message: '凭证过期' } });
  await second;
  assert.equal(h.storage.token, undefined);
  assert.equal(h.storage.baseUrl, 'http://current-server:8081');
  assert.equal(h.page._history.length, 0);
  assert.equal(h.page.data.messages.length, 1);
  assert.equal(h.redirects.at(-1), '/pages/login/login');
});

test('account change discards an old pending answer and starts next request without old history', async () => {
  const h = loadPage();
  const pending = h.ask('旧账号成绩');
  h.storage.token = 'other-student-token';
  h.reply(0, '旧账号的学业资料');
  await pending;
  assert.equal(h.page.data.messages.length, 1);
  assert.equal(h.page._history.length, 0);
  h.page.onShow();
  const next = h.ask('新账号分析');
  assert.equal(h.calls[1].header.Authorization, 'other-student-token');
  assert.deepEqual(plain(h.calls[1].data.history), []);
  h.reply(1);
  await next;
});

test('leaving page aborts pending request and suppresses all late page updates', async () => {
  const h = loadPage();
  const pending = h.ask('学习概况');
  h.page.onUnload();
  h.page.setData = () => assert.fail('unloaded page must not receive updates');
  await pending;
  h.reply(0, '迟到的回答');
  await tick();
  assert.equal(h.aborted(), 1);
  assert.equal(h.page._history.length, 0);
  const reopened = loadPage();
  assert.equal(reopened.page.data.messages.length, 1);
});

test('changing server clears old conversation and ignores late response from old server', async () => {
  const h = loadPage();
  const first = h.ask('概况');
  h.reply(0, '旧服务上的结果');
  await first;
  const pending = h.ask('趋势');
  h.storage.baseUrl = 'http://new-server:8081';
  h.page.onShow();
  await pending;
  assert.equal(h.aborted(), 1);
  assert.equal(h.page.data.messages.length, 1);
  const next = h.ask('重新分析');
  assert.equal(h.calls[2].url, 'http://new-server:8081/api/student/agent/chat');
  assert.deepEqual(plain(h.calls[2].data.history), []);
  h.reply(2);
  await next;
});

test('new conversation clears both display and submitted history', async () => {
  const h = loadPage();
  const first = h.ask('概况');
  h.reply(0);
  await first;
  h.page.clearConversation();
  const next = h.ask('趋势');
  assert.deepEqual(plain(h.calls[1].data.history), []);
  h.reply(1);
  await next;
});

test('malformed answer is not saved as evidence and can be retried', async () => {
  const h = loadPage();
  const pending = h.ask('概况');
  h.calls[0].success({ statusCode: 200, data: { code: 200, data: { answer: 'fabricated', status: 'unexpected' } } });
  await pending;
  assert.equal(h.page._history.length, 0);
  assert.equal(h.page.data.inputValue, '概况');
  assert.match(h.page.data.messages.at(-1).content, /有效回答/);
});

test('history preserves latest complete pairs within backend count and combined length limits', () => {
  const exchanges = [];
  for (let i = 0; i < 15; i++) exchanges.push(
    { role: 'user', content: String(i) + 'u'.repeat(1000) },
    { role: 'assistant', content: String(i) + 'a'.repeat(1000) }
  );
  const selected = chat.buildHistory(exchanges, 'q'.repeat(2000));
  assert.ok(selected.length <= 20);
  assert.equal(selected.length % 2, 0);
  assert.ok(selected.reduce((sum, entry) => sum + entry.content.length, 2000) <= 20000);
  assert.deepEqual(selected.at(-1), exchanges.at(-1));
  assert.equal(selected[0].role, 'user');
});

test('oversized answer is displayed elsewhere but not truncated into false history or sent over limit', () => {
  const previous = [{ role: 'user', content: '概况' }, { role: 'assistant', content: '已有结果' }];
  const selected = chat.appendExchange(previous, '详细说明', 'x'.repeat(4001));
  assert.deepEqual(selected, previous);
});


test('refusal is shown as a scope response and retained for a valid followup', async () => {
  const h = loadPage();
  const pending = h.ask('帮我查一下其他同学的成绩');
  const answer = '我只能查询当前登录账号的学业信息，不能查看其他同学的成绩。';
  h.reply(0, answer, 'REFUSED');
  await pending;
  assert.equal(h.page.data.messages.at(-1).notice, '仅支持查询本人');
  assert.equal(h.page.data.messages.at(-1).content, answer);
  assert.equal(h.page.data.asking, false);
  const next = h.ask('那分析我自己的');
  assert.deepEqual(plain(h.calls[1].data.history), [
    { role: 'user', content: '帮我查一下其他同学的成绩' },
    { role: 'assistant', content: answer }
  ]);
  h.reply(1);
  await next;
});

test('unsupported and clarification replies do not show missing analysis warnings', async () => {
  for (const [status, notice] of [
    ['OUT_OF_SCOPE', '当前能力范围'], ['NEEDS_CLARIFICATION', '请补充问题']
  ]) {
    const h = loadPage();
    const pending = h.ask('下一步呢');
    h.reply(0, '目前支持学习概况、近期课程和成绩趋势。', status);
    await pending;
    assert.equal(h.page.data.messages.at(-1).notice, notice);
    assert.equal(h.page.data.messages.at(-1).failed, undefined);
    assert.equal(h.page.data.asking, false);
    assert.equal(h.page._history.length, 2);
  }
});
