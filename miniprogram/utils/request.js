// 微信开发者工具本地调试地址；真机/生产环境请通过 Storage 的 baseUrl 配置 HTTPS 域名。
const DEFAULT_BASE_URL = "http://127.0.0.1:8081";

const normalizeBaseUrl = (raw) => {
  if (typeof raw !== "string") return "";
  let v = raw.trim();
  if (!v) return "";
  if (!/^https?:\/\//i.test(v)) v = `http://${v}`;
  v = v.replace(/\/+$/, "");
  v = v.replace(/\/api$/i, "");
  return v;
};

const getBaseUrl = () => {
  try {
    const cfg = wx.getStorageSync("baseUrl");
    const normalized = normalizeBaseUrl(cfg);
    if (normalized) return normalized;
  } catch (e) {}
  return DEFAULT_BASE_URL;
};

const BASE_URL = getBaseUrl();

const buildRequestUrl = (url) => {
  const u = String(url ?? "").trim();
  if (!u) return getBaseUrl();
  if (/^https?:\/\//i.test(u)) return u;
  const path = u.startsWith("/") ? u : `/${u}`;
  return getBaseUrl() + path;
};

const request = (url, method = "GET", data = {}, options = {}) => {
  const doRequest = (fullUrl) => {
    return new Promise((resolve, reject) => {
      const task = wx.request({
        url: fullUrl,
        method: method,
        data: data,
        timeout: options.timeout || 15000,
        header: {
          "Content-Type": "application/json",
          Authorization: wx.getStorageSync("token") || "",
        },
        success: (res) => {
          if (
            res &&
            typeof res.statusCode === "number" &&
            res.statusCode >= 400
          ) {
            reject({
              statusCode: res.statusCode,
              data: res.data,
              message: (res.data && res.data.message) || `请求失败（${res.statusCode}）`
            });
            return;
          }
          if (res.data && res.data.code === 200) {
            resolve(res.data.data);
          } else {
            wx.showToast({
              title: (res.data && res.data.message) || "请求错误",
              icon: "none",
            });
            reject(res.data);
          }
        },
        fail: (err) => reject(err),
      });
      if (typeof options.onTask === "function") options.onTask(task);
    });
  };

  return doRequest(buildRequestUrl(url));
};

const inferDocumentType = (response) => {
  const headers = {};
  Object.keys(response.header || {}).forEach(key => { headers[key.toLowerCase()] = response.header[key]; });
  const filename = String(headers['content-disposition'] || '');
  const extension = filename.match(/\.(docx|doc|xlsx|xls|pptx|ppt|pdf)(?:[;"\s]|$)/i);
  if (extension) return extension[1].toLowerCase();
  const contentType = String(headers['content-type'] || '').toLowerCase();
  if (contentType.includes('wordprocessingml')) return 'docx';
  if (contentType.includes('msword')) return 'doc';
  if (contentType.includes('pdf')) return 'pdf';
  return undefined;
};

// 证明由后端生成 DOCX；下载后的临时路径未必包含扩展名。
// 根据下载响应识别格式，不能把所有文件都当成 PDF 打开。
const downloadDocument = (url, options = {}) => new Promise((resolve, reject) => {
  const fullUrl = buildRequestUrl(url);
  const token = wx.getStorageSync('token');
  if (!token) {
    reject(new Error('请先登录再下载证明'));
    return;
  }
  wx.downloadFile({
    url: fullUrl,
    timeout: 60000,
    header: { Authorization: token },
    success: (res) => {
      if (res.statusCode !== 200) {
        const messages = { 401: '登录已失效，请重新登录', 403: '无权下载这份证明', 404: '证明文件不存在，请联系管理员' };
        reject(new Error(messages[res.statusCode] || `下载失败（${res.statusCode}）`));
        return;
      }
      const documentOptions = {
        filePath: res.tempFilePath,
        showMenu: true,
        success: () => resolve(res.tempFilePath),
        fail: (error) => reject(new Error(`文件已下载，但无法打开：${error.errMsg || '文档文件异常'}`))
      };
      const fileType = inferDocumentType(res) || options.fileType;
      if (fileType) documentOptions.fileType = fileType;
      wx.openDocument(documentOptions);
    },
    fail: (error) => {
      const detail = error.errMsg || '网络连接失败';
      const message = /domain list|合法域名/i.test(detail)
        ? '请在微信后台配置 downloadFile 合法域名'
        : /https?:\/\/(127\.0\.0\.1|localhost)(:|\/|$)/i.test(fullUrl)
          ? '真机无法访问电脑的127.0.0.1，请配置电脑地址'
          : `下载失败：${detail}`;
      reject(new Error(message));
    }
  });
});

const downloadPdf = (url) => downloadDocument(url, { fileType: 'pdf' });

module.exports = { request, BASE_URL, buildRequestUrl, downloadPdf, downloadDocument };
