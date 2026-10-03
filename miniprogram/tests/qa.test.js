const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function loadPage(request) {
  let definition;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/qa/qa.js'), 'utf8'), {
    Page: page => { definition = page; },
    require: () => ({ request }),
    wx: { getStorageSync: () => 'student', showNavigationBarLoading() {}, hideNavigationBarLoading() {} }
  });
  definition.setData = changes => Object.assign(definition.data, changes);
  definition.data.inputValue = '作弊有什么处分';
  return definition;
}

test('long answer uses its own timeout and cannot be submitted twice while waiting', async () => {
  let finish;
  let calls = 0;
  const pending = new Promise(resolve => { finish = resolve; });
  const page = loadPage((url, method, data, options) => {
    calls++;
    assert.equal(options.timeout, 120000);
    return pending;
  });
  page.askAI();
  page.data.inputValue = '重复发送';
  page.askAI();
  assert.equal(calls, 1);
  assert.equal(page.data.asking, true);
  finish({ answer: '根据政策处分' });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(page.data.asking, false);
  assert.equal(page.data.chatList.at(-1).content, '根据政策处分');
});

test('timeout is reported as waiting timeout instead of knowledge base connection failure', async () => {
  const page = loadPage(() => Promise.reject({ errMsg: 'request:fail timeout' }));
  page.askAI();
  await new Promise(resolve => setImmediate(resolve));
  assert.match(page.data.chatList.at(-1).content, /等待超时/);
  assert.equal(page.data.asking, false);
});

test('backend login errors remain visible to the student', async () => {
  const page = loadPage(() => Promise.reject({ statusCode: 401, message: '未登录' }));
  page.askAI();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(page.data.chatList.at(-1).content, '未登录');
});

test('ordinary request timeout is preserved and HTTP errors include the server message', async () => {
  const sandbox = {
    module: { exports: {} },
    wx: {
      getStorageSync: () => '',
      request: options => {
        assert.equal(options.timeout, 15000);
        options.success({ statusCode: 502, data: { code: 502, message: 'AI 问答服务超时或不可用' } });
      }
    }
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../utils/request.js'), 'utf8'), sandbox);
  await assert.rejects(sandbox.module.exports.request('/api/health'), error => error.message === 'AI 问答服务超时或不可用');
});
