const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const plain = value => JSON.parse(JSON.stringify(value));
const tick = () => new Promise(resolve => setImmediate(resolve));
const ROOT = '/api/student/agent/conversations';

// Exercise the real page + request/stream wrappers; only HTTP and WeChat UI are mocked.
function loadPage(overrides = {}, seed = []) {
  const storage = { token: 'student-token', role: 'student', baseUrl: 'http://current-server:8081', ...overrides };
  const calls = [], apiCalls = [], redirects = [], toasts = [], chunkHandlers = [], modals = [];
  const rows = new Map(seed.map(row => [row.id, { owner: 'student-token', title: '已保存的会话', messages: [], ...plain(row) }]));
  let nextId = Math.max(0, ...rows.keys()) + 1, messageId = 100, aborted = 0, failNextApi = null;
  const wx = {
    getStorageSync: key => storage[key], removeStorageSync: key => { delete storage[key]; },
    showToast: options => toasts.push(options.title), showModal: options => modals.push(options),
    showNavigationBarLoading() {}, hideNavigationBarLoading() {},
    reLaunch: options => redirects.push(options.url), navigateTo() {},
    request: options => {
      apiCalls.push(options);
      const chat = options.url.endsWith('/chat/stream');
      let index;
      if (chat) { index = calls.length; calls.push(options); }
      else queueMicrotask(() => {
        if (failNextApi) { const code = failNextApi; failNextApi = null; options.success({ statusCode: code, data: { code, message: 'API unavailable' } }); return; }
        const url = new URL(options.url), owner = options.header.Authorization, id = Number(url.pathname.split('/').at(-1));
        let result, code = 200;
        if (url.pathname === ROOT && options.method === 'POST') {
          result = { id: nextId++, title: '新会话', messages: [], owner }; rows.set(result.id, result);
        } else if (url.pathname === ROOT && options.method === 'GET') {
          const all = [...rows.values()].filter(row => row.owner === owner).sort((a, b) => b.id - a.id);
          const page = options.data.page, size = options.data.pageSize;
          result = { records: all.slice((page-1)*size,page*size).map(({ id, title }) => ({ id, title })), total: all.length };
        } else {
          const row = rows.get(id);
          if (!row || row.owner !== owner) code = 404;
          else if (options.method === 'GET') {
            const limit = options.data.limit, before = options.data.beforeMessageId;
            const history = row.messages.filter(message => !before || message.id < before);
            const messages = history.slice(-limit);
            result = { conversation: { id, title: row.title }, messages, hasMore: history.length > limit, nextBeforeMessageId: history.length > limit ? messages[0].id : null };
          } else if (options.method === 'PATCH') { row.title = options.data.title; result = { id, title: row.title }; }
          else if (options.method === 'DELETE') { rows.delete(id); result = null; }
        }
        options.success({ statusCode: code, data: { code, data: result, message: code === 404 ? 'not owned' : 'ok' } });
      });
      return { onChunkReceived: callback => { chunkHandlers[index] = callback; }, abort: () => { aborted++; options.fail({ errMsg: 'request:fail abort' }); } };
    }
  };
  const module = { exports: {} };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../utils/request.js'), 'utf8'), { module, wx, require: name => require(path.join(__dirname, '../utils', name)) });
  let page;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/agent/agent.js'), 'utf8'), {
    Page: definition => { page = definition; }, wx,
    require: name => name.includes('request') ? module.exports : require(path.join(__dirname, '../pages/agent', name))
  });
  page.setData = changes => Object.assign(page.data, changes);
  page.onLoad(); const ready = page.onShow();
  const persist = (index, answer) => {
    const call = calls[index], row = rows.get(call.data.conversationId);
    if (!row || row.owner !== call.header.Authorization) return;
    row.messages.push({ id: ++messageId, role: 'USER', content: call.data.message }, { id: ++messageId, role: 'ASSISTANT', content: answer });
    if (row.title === '新会话') row.title = call.data.message.slice(0,100);
  };
  const reply = (index, answer = '依据工具数据给出的回答', status = 'COMPLETED', id = calls[index].data.conversationId) => {
    if (id === calls[index].data.conversationId) persist(index,answer);
    calls[index].success({ statusCode: 200, data: { code: 200, data: { conversationId: id, answer, status, toolRounds: 1, toolCalls: [] } } });
  };
  const ask = async question => { await ready; page.data.inputValue = question; return page.sendMessage(); };
  const chunk = (index, event) => { if (event.type === 'result') persist(index,event.data.answer); const bytes = Buffer.from(JSON.stringify(event)+'\n'); chunkHandlers[index]({ data: bytes.buffer.slice(bytes.byteOffset,bytes.byteOffset+bytes.byteLength) }); };
  return { page, ready, storage, calls, apiCalls, redirects, toasts, modals, rows, reply, ask, chunk,
    finish: index => calls[index].success({ statusCode: 200, data: '' }),
    failApi: code => { failNextApi = code; }, aborted: () => aborted };
}
const start = async (h, question = '分析最近学习情况') => { const pending = h.ask(question); await tick(); return { pending }; };

test('creates owned conversation and sends only ID/message with JWT, real stream and bounded timeout', async () => {
  const h = loadPage(), { pending } = await start(h);
  assert.equal(h.calls.length,1);
  assert.equal(h.calls[0].url,'http://current-server:8081/api/student/agent/chat/stream');
  assert.equal(h.calls[0].header.Authorization,'student-token');
  assert.equal(h.calls[0].timeout,240000); assert.equal(h.calls[0].enableChunked,true);
  assert.deepEqual(plain(h.calls[0].data),{ conversationId:1,message:'分析最近学习情况' });
  assert.deepEqual(plain(h.apiCalls.find(call => call.method === 'POST' && call.url.endsWith(ROOT)).data),{});
  await h.ask('重复点击'); assert.equal(h.calls.length,1);
  h.reply(0); await pending;
  assert.equal(h.page.data.asking,false); assert.equal(h.page.data.conversationTitle,'分析最近学习情况');
  assert.equal(h.page.data.messages.at(-1).content,'依据工具数据给出的回答');
});

test('followup relies on same server conversation and never sends client history or identity', async () => {
  const h = loadPage(); let { pending } = await start(h,'最近表现'); h.reply(0,'已保存回答'); await pending;
  ({ pending } = await start(h,'趋势呢'));
  assert.deepEqual(plain(h.calls[1].data),{ conversationId:1,message:'趋势呢' });
  assert.equal(h.page._history,undefined); h.reply(1); await pending;
  assert.equal(h.rows.get(1).messages.length,4);
});

test('reopening page restores server history and ignores illegal roles', async () => {
  const h = loadPage({},[{ id:7,messages:[{ id:1,role:'USER',content:'我的问题' },{ id:2,role:'ASSISTANT',content:'已保存回答' },{ id:3,role:'SYSTEM',content:'fake' }] }]);
  await h.ready; assert.equal(h.page.data.activeConversationId,7);
  assert.deepEqual(plain(h.page.data.messages.map(m => m.content)),['我的问题','已保存回答']);
  const { pending } = await start(h,'再分析'); assert.equal(h.calls[0].data.conversationId,7); h.reply(0); await pending;
});

test('new conversation keeps existing server history and lazily creates a new ID', async () => {
  const h = loadPage({},[{ id:7,messages:[{ id:1,role:'USER',content:'已有问题' }] }]); await h.ready;
  h.page.newConversation(); assert.equal(h.rows.size,1); assert.equal(h.page.data.activeConversationId,null);
  const { pending } = await start(h); assert.equal(h.calls[0].data.conversationId,8); h.reply(0); await pending;
  assert.equal(h.rows.size,2); assert.equal(h.apiCalls.filter(call => call.method === 'DELETE').length,0);
});

test('rename and confirmed delete use server API and reload only own list', async () => {
  const h = loadPage({},[{ id:7 }]); await h.ready;
  const event = { currentTarget:{ dataset:{ id:7 } } };
  h.page.renameConversation(event); await h.modals.pop().success({ confirm:true,content:'趋势复盘' });
  assert.equal(h.page.data.conversationTitle,'趋势复盘');
  assert.deepEqual(plain(h.apiCalls.find(call => call.method === 'PATCH').data),{ title:'趋势复盘' });
  h.page.deleteConversation(event); await h.modals.pop().success({ confirm:true });
  assert.equal(h.rows.size,0); assert.equal(h.page.data.activeConversationId,null); assert.equal(h.page.data.conversationList.length,0);
});

test('history pagination prepends earlier server messages in chronological order', async () => {
  const messages = Array.from({ length:60 },(_,i) => ({ id:i+1,role:i%2 ? 'ASSISTANT' : 'USER',content:'m'+(i+1) }));
  const h = loadPage({},[{ id:7,messages }]); await h.ready;
  assert.equal(h.page.data.messages[0].content,'m21'); assert.equal(h.page.data.historyHasMore,true);
  await h.page.loadEarlierMessages(); assert.equal(h.page.data.messages.length,60);
  assert.equal(h.page.data.messages[0].content,'m1'); assert.equal(h.page.data.messages.at(-1).content,'m60');
  assert.equal(h.page.data.historyHasMore,false);
});

test('failed chat restores question, then refresh removes transient failed UI messages', async () => {
  const h = loadPage(); let { pending } = await start(h,'正常问题'); h.reply(0,'已保存回答'); await pending;
  ({ pending } = await start(h,'风险问题')); h.calls[1].success({ statusCode:504,data:{ code:504 } }); await pending;
  assert.match(h.page.data.messages.at(-1).content,/超时/); assert.equal(h.page.data.inputValue,'风险问题');
  assert.match(h.page.data.messages.at(-1).notice,/刷新会话确认/); assert.equal(h.rows.get(1).messages.length,2);
  await h.page.refreshCurrentConversation(); assert.equal(h.page.data.messages.length,2);
  assert.equal(h.page.data.messages.at(-1).content,'已保存回答');
});

test('conflicting turn prompts refresh; a missing conversation clears selected ID', async () => {
  for (const code of [409,404]) {
    const h = loadPage({},[{ id:7 }]); const { pending } = await start(h);
    h.calls[0].success({ statusCode:code,data:{ code } }); await pending;
    assert.match(h.page.data.messages.at(-1).content,code === 409 ? /刷新/ : /新建会话/);
    assert.equal(h.page.data.activeConversationId,code === 409 ? 7 : null);
  }
});

test('failed conversation creation never starts model request', async () => {
  const h = loadPage(); await h.ready; h.failApi(503);
  await h.ask('分析'); assert.equal(h.calls.length,0); assert.equal(h.page.data.asking,false);
  assert.equal(h.page.data.inputValue,'分析'); assert.equal(h.page.data.activeConversationId,null);
});

test('anonymous and administrator are redirected without making any conversation API call', async () => {
  for (const overrides of [{ token:'' },{ role:'admin' }]) {
    const h = loadPage(overrides); await h.ready; await h.ask('查询');
    assert.equal(h.apiCalls.length,0); assert.equal(h.redirects.at(-1),'/pages/login/login');
  }
});

test('expired JWT clears identity and histories but retains selected server URL', async () => {
  const h = loadPage(), { pending } = await start(h);
  h.calls[0].success({ statusCode:401,data:{ code:401 } }); await pending;
  assert.equal(h.storage.token,undefined); assert.equal(h.storage.baseUrl,'http://current-server:8081');
  assert.equal(h.page.data.activeConversationId,null); assert.equal(h.page.data.conversationList.length,0);
  assert.equal(h.redirects.at(-1),'/pages/login/login');
});

test('changed account cancels in-flight result and restores only the new account list', async () => {
  const h = loadPage({},[{ id:8,owner:'other-token',messages:[{ id:30,role:'ASSISTANT',content:'其他账号自己的会话' }] }]);
  const { pending } = await start(h,'本人问题'); h.storage.token='other-token';
  await h.page.onShow(); h.reply(0,'旧账号迟到回答'); await pending;
  assert.ok(h.aborted()>0); assert.equal(h.page.data.activeConversationId,8);
  assert.equal(h.page.data.messages.at(-1).content,'其他账号自己的会话');
  assert.ok(!h.page.data.messages.some(m => m.content === '旧账号迟到回答'));
  assert.deepEqual(plain(h.page.data.conversationList.map(c => c.id)),[8]);
});

test('changing server address aborts old request and ignores late response', async () => {
  const h = loadPage(), { pending } = await start(h); h.storage.baseUrl='http://next-server:8081';
  await h.page.onShow(); h.reply(0,'旧服务迟到回答'); await pending;
  assert.ok(h.aborted()>0); assert.ok(h.apiCalls.at(-1).url.startsWith('http://next-server:8081'));
  assert.ok(!h.page.data.messages.some(m => m.content === '旧服务迟到回答'));
});

test('page unload aborts stream and late result never enters UI', async () => {
  const h = loadPage(), { pending } = await start(h); h.page.onUnload(); h.reply(0,'迟到回答'); await pending;
  assert.ok(h.aborted()>0); assert.ok(!h.page.data.messages.some(m => m.content === '迟到回答'));
});

test('real execution steps and Tool evidence display before the final answer', async () => {
  const h = loadPage(), { pending } = await start(h);
  h.chunk(0,{ type:'progress',data:{ id:'tool-1',label:'读取成绩趋势',state:'running',detail:'正在执行' } });
  assert.equal(h.page.data.executionSteps[0].state,'running');
  h.chunk(0,{ type:'evidence',data:{ kind:'overview',metrics:[{ label:'GPA',value:3.7 }] } });
  assert.equal(h.page.data.evidence[0].metrics[0].value,'3.7');
  h.chunk(0,{ type:'progress',data:{ id:'tool-1',label:'读取成绩趋势',state:'done' } });
  h.chunk(0,{ type:'result',data:{ conversationId:1,answer:'分析完成',status:'COMPLETED',toolRounds:1,toolCalls:[] } });
  h.finish(0); await pending;
  assert.equal(h.page.data.messages.at(-1).steps[0].state,'done');
  assert.equal(h.page.data.messages.at(-1).evidence[0].metrics[0].value,'3.7');
  assert.equal(h.rows.get(1).messages.length,2);
});

test('truncated stream cannot become a successful assistant answer', async () => {
  const h = loadPage(), { pending } = await start(h);
  h.chunk(0,{ type:'progress',data:{ id:'tool-1',label:'工具调用',state:'running' } });
  h.finish(0); await pending;
  assert.match(h.page.data.messages.at(-1).content,/中断/); assert.equal(h.rows.get(1).messages.length,0);
  assert.equal(h.page.data.executionSteps[0].state,'error');
});

test('mismatched conversation response is not accepted and oversize input never starts API', async () => {
  const h = loadPage(), { pending } = await start(h); h.reply(0,'错误会话回答','COMPLETED',999); await pending;
  assert.ok(!h.page.data.messages.some(m => m.content === '错误会话回答')); assert.equal(h.rows.get(1).messages.length,0);
  await h.ask('x'.repeat(2001)); assert.equal(h.calls.length,1); assert.match(h.toasts.at(-1),/2000/);
});

test('scope refusals and unavailable-data explanations remain ordinary saved text', async () => {
  for (const status of ['REFUSED','DATA_UNAVAILABLE','TOOL_LIMIT','NEEDS_CLARIFICATION','OUT_OF_SCOPE']) {
    const h = loadPage(), { pending } = await start(h); h.reply(0,'真实边界说明',status); await pending;
    assert.ok(h.page.data.messages.at(-1).notice); assert.equal(h.rows.get(1).messages.at(-1).content,'真实边界说明');
    await h.page.refreshCurrentConversation(); assert.equal(h.page.data.messages.at(-1).content,'真实边界说明');
  }
});
