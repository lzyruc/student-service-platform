const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { normalizeAcademicReport } = require('../utils/academic-report.js');

function loadPage(request, extraWx = {}, buildRequestUrl = url => 'http://current-server' + url) {
  let page;
  const toasts = [];
  const wx = {
    getStorageSync: () => 'current-token', showLoading() {}, hideLoading() {},
    showToast: toast => toasts.push(toast.title), ...extraWx
  };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../pages/academic/academic.js'), 'utf8'), {
    Page: definition => { page = definition; }, wx,
    require: name => name.includes('academic-report') ? { normalizeAcademicReport } : { request, buildRequestUrl }
  });
  page.setData = changes => Object.assign(page.data, changes);
  return { page, toasts };
}

test('reentering the page reads the saved transcript and analyzes without selecting or uploading a file', async () => {
  const transcript = { fileId: 22, originalName: 'saved.pdf', available: true };
  let analyses = 0;
  const api = async (url, method, data) => {
    if (url === '/api/student/transcript') return transcript;
    assert.equal(url, '/api/student/warning/analyze-saved');
    assert.equal(method, 'POST');
    assert.deepEqual(Object.keys(data), []);
    analyses++;
    return { data: { course_count: 45, report: { warning_level: '正常', total_earned_credits: 117 } } };
  };
  const forbidden = () => { throw new Error('must not upload during saved analysis'); };
  const { page } = loadPage(api, { chooseMessageFile: forbidden, uploadFile: forbidden });
  page.onShow();
  await new Promise(resolve => setImmediate(resolve));
  await page.analyzeSavedTranscript();
  assert.equal(page.data.report.courseCount, 45);
  assert.equal(page.data.report.earnedCredits, 117);
  const { page: reopened } = loadPage(api, { chooseMessageFile: forbidden, uploadFile: forbidden });
  await reopened.loadTranscript();
  await reopened.analyzeSavedTranscript();
  assert.equal(analyses, 2);
  assert.equal(reopened.data.transcript.fileId, 22);
});

test('upload is independent of analysis and the next page can reuse the persisted metadata', async () => {
  let saved = null;
  let chooserCalls = 0;
  let server = 'http://new-server:8081';
  const { page } = loadPage(async () => saved, {
    chooseMessageFile: options => { chooserCalls++; options.success({ tempFiles: [{ path: 'temporary-grades.pdf' }] }); },
    uploadFile: options => {
      assert.equal(options.url, server + '/api/student/transcript');
      assert.equal(options.header.Authorization, 'current-token');
      saved = { fileId: 33, originalName: 'grades.pdf', available: true };
      options.success({ statusCode: 200, data: JSON.stringify({ code: 200, data: saved }) });
      options.complete();
    }
  }, url => server + url);
  await page.loadTranscript();
  assert.equal(page.data.transcript, null);
  page.uploadTranscript();
  assert.equal(chooserCalls, 1);
  assert.equal(page.data.uploading, false);
  assert.equal(page.data.report, null);
  const { page: reopened } = loadPage(async () => saved);
  await reopened.loadTranscript();
  assert.equal(reopened.data.transcript.fileId, 33);
});

test('no saved transcript prevents analysis, while failed analysis keeps a saved file for retry', async () => {
  let calls = 0;
  const { page, toasts } = loadPage(async () => { calls++; throw { statusCode: 502, message: '分析服务暂不可用' }; });
  await page.analyzeSavedTranscript();
  assert.equal(calls, 0);
  assert.match(toasts.at(-1), /先上传/);
  page.data.transcript = { fileId: 22, available: true };
  await page.analyzeSavedTranscript();
  assert.equal(calls, 1);
  assert.equal(page.data.transcript.fileId, 22);
  assert.equal(page.data.analyzing, false);
  assert.equal(toasts.at(-1), '分析服务暂不可用');
});

test('repeated analysis clicks do not duplicate requests', async () => {
  let finish;
  let calls = 0;
  const { page } = loadPage(() => { calls++; return new Promise(resolve => { finish = resolve; }); });
  page.data.transcript = { fileId: 22, available: true };
  const pending = page.analyzeSavedTranscript();
  await page.analyzeSavedTranscript();
  assert.equal(calls, 1);
  assert.equal(page.data.analyzing, true);
  finish({ data: { course_count: 45, report: {} } });
  await pending;
  assert.equal(page.data.analyzing, false);
});

test('upload cancellation unlocks the page and a failed replacement preserves the old file', () => {
  const original = { fileId: 22, originalName: 'old.pdf', available: true };
  const { page } = loadPage(async () => original, {
    chooseMessageFile: options => options.fail({ errMsg: 'cancel' })
  });
  page.data.transcript = original;
  page.uploadTranscript();
  assert.equal(page.data.uploading, false);
  assert.equal(page.data.transcript.fileId, 22);
  const { page: failing } = loadPage(async () => original, {
    chooseMessageFile: options => options.success({ tempFiles: [{ path: 'new.pdf' }] }),
    uploadFile: options => { options.success({ statusCode: 400, data: JSON.stringify({ code: 400, message: '文件内容不是 PDF' }) }); options.complete(); }
  });
  failing.data.transcript = original;
  failing.uploadTranscript();
  assert.equal(failing.data.transcript.fileId, 22);
  assert.equal(failing.data.uploading, false);
});

test('metadata loading failure offers retry instead of assuming a stored file is absent', async () => {
  const { page } = loadPage(() => Promise.reject({ message: '登录已失效' }));
  await page.loadTranscript();
  assert.equal(page.data.transcriptError, '登录已失效');
  assert.equal(page.data.loadingTranscript, false);
});
