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

const request = (url, method = "GET", data = {}) => {
  const doRequest = (fullUrl) => {
    return new Promise((resolve, reject) => {
      wx.request({
        url: fullUrl,
        method: method,
        data: data,
        timeout: 15000,
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
            reject({ statusCode: res.statusCode, data: res.data });
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
    });
  };

  return doRequest(buildRequestUrl(url));
};

module.exports = { request, BASE_URL };
