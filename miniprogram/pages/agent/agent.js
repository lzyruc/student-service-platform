const { request, buildRequestUrl } = require('../../utils/request.js');
const { MAX_MESSAGE, REQUEST_TIMEOUT, buildHistory, appendExchange, errorMessage } = require('../../utils/agent-chat.js');
const ENDPOINT = '/api/student/agent/chat';
const greeting = () => [{ id: 'greeting', role: 'assistant', content: '你好！我可以根据你已保存的成绩单和培养方案，分析学习情况、关注课程和成绩趋势。' }];

Page({
  data: {
    inputValue: '', asking: false, bottomId: '', messages: greeting(),
    suggestions: ['分析我的学习情况', '我有没有挂科风险', '最近哪些课程表现不好', '我的成绩趋势怎么样', '哪些课程需要重点关注']
  },

  onLoad() {
    this._unloaded = false;
    this._requestVersion = 0;
    this._nextId = 0;
    this._history = [];
    this._sessionToken = '';
    this._sessionUrl = '';
  },

  onShow() { this.ensureSession(); },

  onUnload() {
    this._unloaded = true;
    this.cancelPending();
    this._history = [];
    this._sessionToken = '';
  },

  cancelPending() {
    this._requestVersion++;
    if (this._requestTask && typeof this._requestTask.abort === 'function') this._requestTask.abort();
    this._requestTask = null;
    wx.hideNavigationBarLoading();
  },

  clearConversation() {
    this.cancelPending();
    this._history = [];
    if (!this._unloaded) this.setData({ inputValue: '', asking: false, bottomId: '', messages: greeting() });
  },

  ensureSession() {
    const token = wx.getStorageSync('token');
    const role = wx.getStorageSync('role');
    if (!token || (role && role !== 'student')) {
      this.clearConversation();
      wx.showToast({ title: '请先使用学生账号登录', icon: 'none' });
      wx.reLaunch({ url: '/pages/login/login' });
      return false;
    }
    const url = buildRequestUrl(ENDPOINT);
    if (token !== this._sessionToken || url !== this._sessionUrl) this.clearConversation();
    this._sessionToken = token;
    this._sessionUrl = url;
    return true;
  },

  isCurrent(version) {
    if (this._unloaded || version !== this._requestVersion) return false;
    const role = wx.getStorageSync('role');
    if (wx.getStorageSync('token') !== this._sessionToken || (role && role !== 'student') || buildRequestUrl(ENDPOINT) !== this._sessionUrl) {
      this.clearConversation();
      return false;
    }
    return true;
  },

  onInput(event) { this.setData({ inputValue: event.detail.value }); },

  askSuggested(event) {
    if (this.data.asking) return;
    this.setData({ inputValue: event.currentTarget.dataset.question });
    return this.sendMessage();
  },

  manageTranscript() { wx.navigateTo({ url: '/pages/academic/academic' }); },

  addMessage(role, content, notice = '') {
    const id = 'message-' + (++this._nextId);
    this.setData({ messages: [...this.data.messages, { id, role, content, notice }], bottomId: id });
  },

  async sendMessage() {
    if (this._unloaded || this.data.asking || !this.ensureSession()) return;
    const question = this.data.inputValue.trim();
    if (!question) return;
    if (question.length > MAX_MESSAGE) {
      wx.showToast({ title: '问题请控制在2000字以内', icon: 'none' });
      return;
    }
    const history = buildHistory(this._history, question);
    const version = ++this._requestVersion;
    this.addMessage('user', question);
    this.setData({ inputValue: '', asking: true, bottomId: 'agent-thinking' });
    wx.showNavigationBarLoading();
    try {
      const result = await request(ENDPOINT, 'POST', { message: question, history }, {
        timeout: REQUEST_TIMEOUT,
        onTask: task => { if (this.isCurrent(version)) this._requestTask = task; }
      });
      if (!this.isCurrent(version)) return;
      if (!result || typeof result.answer !== 'string' || !result.answer.trim()
          || !['COMPLETED', 'DATA_UNAVAILABLE', 'TOOL_LIMIT', 'REFUSED', 'OUT_OF_SCOPE', 'NEEDS_CLARIFICATION'].includes(result.status)) {
        throw new Error('暂未收到有效回答，请稍后重试。');
      }
      const notices = { TOOL_LIMIT: '本次仅取得部分结果', DATA_UNAVAILABLE: '暂未取得完整分析',
        REFUSED: '仅支持查询本人', OUT_OF_SCOPE: '当前能力范围', NEEDS_CLARIFICATION: '请补充问题' };
      const notice = notices[result.status] || '';
      this.addMessage('assistant', result.answer, notice);
      if (result.status !== 'DATA_UNAVAILABLE') this._history = appendExchange(history, question, result.answer);
    } catch (error) {
      if (!this.isCurrent(version)) return;
      if (error && (error.statusCode === 401 || error.code === 401)) {
        this.clearConversation();
        ['token', 'account', 'studentNo', 'username', 'role'].forEach(key => wx.removeStorageSync(key));
        wx.showToast({ title: '登录已失效，请重新登录', icon: 'none' });
        wx.reLaunch({ url: '/pages/login/login' });
        return;
      }
      this.addMessage('assistant', errorMessage(error), '本次未完成');
      this.setData({ inputValue: question });
    } finally {
      if (this.isCurrent(version)) {
        this._requestTask = null;
        this.setData({ asking: false });
        wx.hideNavigationBarLoading();
      }
    }
  }
});
