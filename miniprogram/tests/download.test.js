const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

function loadRequest(wx) {
  const sandbox = { wx, module: { exports: {} } };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../utils/request.js'), 'utf8'), sandbox);
  return sandbox.module.exports;
}

test('download uses current server address and opens extensionless PDF with explicit type', async () => {
  const storage = { token: 'student-token', baseUrl: 'http://127.0.0.1:8081' };
  const wx = {
    getStorageSync: key => storage[key],
    downloadFile: options => {
      assert.equal(options.url, 'http://192.168.1.10:8081/api/file/download/123');
      assert.equal(options.header.Authorization, 'student-token');
      options.success({ statusCode: 200, tempFilePath: '/tmp/random-name' });
    },
    openDocument: options => {
      assert.equal(options.fileType, 'pdf');
      assert.equal(options.showMenu, true);
      options.success();
    }
  };
  const { downloadPdf } = loadRequest(wx);
  storage.baseUrl = 'http://192.168.1.10:8081/api/';
  assert.equal(await downloadPdf('/api/file/download/123'), '/tmp/random-name');
});

test('HTTP 401 never opens an error response as a PDF', async () => {
  const wx = {
    getStorageSync: key => key === 'token' ? 'expired-token' : '',
    downloadFile: options => options.success({ statusCode: 401, tempFilePath: '/tmp/error.json' }),
    openDocument: () => assert.fail('must not open an unauthenticated response')
  };
  await assert.rejects(loadRequest(wx).downloadPdf('/api/file/download/123'), /登录已失效/);
});

test('document viewer errors are surfaced instead of silently completing the download', async () => {
  const wx = {
    getStorageSync: key => key === 'token' ? 'student-token' : '',
    downloadFile: options => options.success({ statusCode: 200, tempFilePath: '/tmp/file' }),
    openDocument: options => options.fail({ errMsg: 'openDocument:fail invalid file' })
  };
  await assert.rejects(loadRequest(wx).downloadPdf('/api/file/download/123'), /文件已下载，但无法打开/);
});

test('certificate DOCX is opened as DOCX even when the server uses generic MIME type', async () => {
  const wx = {
    getStorageSync: key => key === 'token' ? 'student-token' : '',
    downloadFile: options => options.success({
      statusCode: 200,
      tempFilePath: '/tmp/random-download',
      header: {
        'Content-Type': 'application/octet-stream',
        'Content-Disposition': "attachment; filename*=UTF-8''certificate_20260001.docx"
      }
    }),
    openDocument: options => {
      assert.equal(options.fileType, 'docx');
      options.success();
    }
  };
  assert.equal(await loadRequest(wx).downloadDocument('/api/file/download/123'), '/tmp/random-download');
});

test('certificate MIME type determines DOCX when filename header is unavailable', async () => {
  const wx = {
    getStorageSync: key => key === 'token' ? 'student-token' : '',
    downloadFile: options => options.success({
      statusCode: 200,
      tempFilePath: '/tmp/random-download',
      header: { 'content-type': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' }
    }),
    openDocument: options => {
      assert.equal(options.fileType, 'docx');
      options.success();
    }
  };
  await loadRequest(wx).downloadDocument('/api/file/download/123');
});
