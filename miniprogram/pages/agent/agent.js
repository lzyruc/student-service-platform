const { request, buildRequestUrl } = require('../../utils/request.js');
const { MAX_MESSAGE, REQUEST_TIMEOUT, errorMessage } = require('../../utils/agent-chat.js');
const { updateSteps, endSteps, normalizeEvidence } = require('../../utils/agent-execution.js');
const { formatMessage } = require('../../utils/message-format.js');
const ENDPOINT = '/api/student/agent/chat/stream';
const CONVERSATIONS = '/api/student/agent/conversations';
const greeting = () => [{ id: 'greeting', role: 'assistant', steps: [], evidence: [], content: '你好！我可以根据你已保存的成绩单和培养方案，分析学习情况、关注课程和成绩趋势。' }];
const validId = id => Number.isSafeInteger(Number(id)) && Number(id) > 0;
const savedMessages = items => (Array.isArray(items) ? items : []).filter(item => item && ['USER', 'ASSISTANT'].includes(item.role) && typeof item.content === 'string').map(item => ({ id: 'saved-' + item.id, role: item.role.toLowerCase(), content: item.content, nodes: formatMessage(item.content), steps: [], evidence: [], expanded: false }));

Page({
  data: {
    inputValue: '', asking: false, bottomId: '', messages: greeting(), executionSteps: [], evidence: [], currentStage: '', realtimeAvailable: false,
    activeConversationId: null, conversationTitle: '新会话', conversationList: [], conversationLoading: false, conversationError: false,
    loadingMessages: false, showConversations: false, conversationHasMore: false, conversationPage: 1, historyHasMore: false, nextBeforeMessageId: null,
    suggestions: ['分析我的学习情况', '我有没有挂科风险', '最近哪些课程表现不好', '我的成绩趋势怎么样', '哪些课程需要重点关注']
  },
  onLoad() { this._unloaded = false; this._requestVersion = 0; this._nextId = 0; this._sessionToken = ''; this._sessionUrl = ''; this._initialized = false; },
  async onShow() { if (this.ensureSession() && !this._initialized) await this.loadConversations(true); },
  onUnload() { this._unloaded = true; this.cancelPending(); this._sessionToken = ''; },
  cancelPending() {
    this._requestVersion++;
    if (this._requestTask && typeof this._requestTask.abort === 'function') this._requestTask.abort();
    this._requestTask = null; wx.hideNavigationBarLoading();
  },
  clearConversation() {
    this.cancelPending();
    if (!this._unloaded) this.setData({ inputValue: '', asking: false, loadingMessages: false, conversationLoading: false, bottomId: '', messages: greeting(), executionSteps: [], evidence: [], currentStage: '', activeConversationId: null, conversationTitle: '新会话', historyHasMore: false, nextBeforeMessageId: null, showConversations: false });
  },
  newConversation() { if (this.data.asking || this.data.loadingMessages || this.data.conversationLoading) return; this.clearConversation(); this._initialized = true; },
  ensureSession() {
    const token = wx.getStorageSync('token'), role = wx.getStorageSync('role');
    if (!token || (role && role !== 'student')) {
      this.clearConversation(); this.setData({ conversationList: [] });
      wx.showToast({ title: '请先使用学生账号登录', icon: 'none' }); wx.reLaunch({ url: '/pages/login/login' }); return false;
    }
    const url = buildRequestUrl(ENDPOINT);
    if (token !== this._sessionToken || url !== this._sessionUrl) {
      this.clearConversation(); this.setData({ conversationList: [], conversationError: false }); this._initialized = false;
    }
    this._sessionToken = token; this._sessionUrl = url; return true;
  },
  isCurrent(version) {
    if (this._unloaded || version !== this._requestVersion) return false;
    const role = wx.getStorageSync('role');
    if (wx.getStorageSync('token') !== this._sessionToken || (role && role !== 'student') || buildRequestUrl(ENDPOINT) !== this._sessionUrl) {
      this.clearConversation(); this.setData({ conversationList: [] }); this._initialized = false; return false;
    }
    return true;
  },
  expired(error) {
    if (!error || (error.statusCode !== 401 && error.code !== 401)) return false;
    this.clearConversation(); this.setData({ conversationList: [] }); this._initialized = false;
    ['token', 'account', 'studentNo', 'username', 'role'].forEach(key => wx.removeStorageSync(key));
    wx.showToast({ title: '登录已失效，请重新登录', icon: 'none' }); wx.reLaunch({ url: '/pages/login/login' }); return true;
  },
  ownedRequest(url, method, data, version, options = {}) {
    return request(url, method, data, { ...options, onTask: task => { if (this.isCurrent(version)) this._requestTask = task; } });
  },
  async loadConversations(selectLatest = false, page = 1) {
    if (!this.ensureSession() || this.data.conversationLoading) return;
    const version = this._requestVersion;
    this.setData({ conversationLoading: true, conversationError: false });
    try {
      const result = await this.ownedRequest(CONVERSATIONS, 'GET', { page, pageSize: 20 }, version);
      if (!this.isCurrent(version)) return;
      if (!result || !Array.isArray(result.records)) throw new Error('会话列表格式异常');
      const rows = result.records.filter(item => validId(item.id));
      this.setData({ conversationList: page === 1 ? rows : [...this.data.conversationList, ...rows], conversationHasMore: page * 20 < result.total, conversationPage: page, conversationLoading: false });
      this._initialized = true;
      if (selectLatest && !this.data.activeConversationId && rows.length) await this.selectConversationId(rows[0].id);
    } catch (error) {
      if (!this.isCurrent(version) || this.expired(error)) return;
      this.setData({ conversationError: true });
    } finally { if (this.isCurrent(version)) { this._requestTask = null; this.setData({ conversationLoading: false }); } }
  },
  async openConversations() { if (this.data.asking || this.data.loadingMessages) return; this.setData({ showConversations: true }); await this.loadConversations(false); },
  closeConversations() { this.setData({ showConversations: false }); },
  retryConversations() { return this.loadConversations(false); },
  loadMoreConversations() { if (this.data.conversationHasMore) return this.loadConversations(false, this.data.conversationPage + 1); },
  selectConversation(event) { return this.selectConversationId(Number(event.currentTarget.dataset.id)); },
  async selectConversationId(id) {
    if (!validId(id) || this.data.asking) return;
    this.clearConversation();
    const version = this._requestVersion;
    this.setData({ loadingMessages: true });
    try {
      const result = await this.ownedRequest(CONVERSATIONS + '/' + id, 'GET', { limit: 40 }, version);
      if (!this.isCurrent(version)) return;
      if (!result || !result.conversation || Number(result.conversation.id) !== Number(id)) throw new Error('会话内容格式异常');
      const messages = savedMessages(result.messages);
      this.setData({ activeConversationId: Number(id), conversationTitle: result.conversation.title, messages: messages.length ? messages : greeting(), historyHasMore: !!result.hasMore, nextBeforeMessageId: result.nextBeforeMessageId || null, bottomId: messages.length ? messages[messages.length - 1].id : '' });
    } catch (error) {
      if (!this.isCurrent(version) || this.expired(error)) return;
      this.setData({ conversationError: true }); wx.showToast({ title: '会话无法读取，请刷新列表', icon: 'none' });
    } finally { if (this.isCurrent(version)) { this._requestTask = null; this.setData({ loadingMessages: false }); } }
  },
  refreshCurrentConversation() { if (this.data.activeConversationId) return this.selectConversationId(this.data.activeConversationId); },
  async loadEarlierMessages() {
    if (!this.data.historyHasMore || this.data.loadingMessages || this.data.asking) return;
    const version = this._requestVersion, id = this.data.activeConversationId;
    this.setData({ loadingMessages: true });
    try {
      const result = await this.ownedRequest(CONVERSATIONS + '/' + id, 'GET', { beforeMessageId: this.data.nextBeforeMessageId, limit: 40 }, version);
      if (!this.isCurrent(version)) return;
      const earlier = savedMessages(result.messages);
      this.setData({ messages: [...earlier, ...this.data.messages], historyHasMore: !!result.hasMore, nextBeforeMessageId: result.nextBeforeMessageId || null });
    } catch (error) { if (this.isCurrent(version) && !this.expired(error)) wx.showToast({ title: '更早消息读取失败', icon: 'none' }); }
    finally { if (this.isCurrent(version)) { this._requestTask = null; this.setData({ loadingMessages: false }); } }
  },
  renameConversation(event) {
    const id = Number(event.currentTarget.dataset.id), row = this.data.conversationList.find(item => Number(item.id) === id);
    if (!row || this.data.asking || this.data.conversationLoading) return;
    const version = this._requestVersion;
    wx.showModal({ title: '重命名会话', editable: true, content: row.title, placeholderText: '输入 1～100 字标题', success: async result => {
      if (!result.confirm || !this.isCurrent(version)) return;
      const title = (result.content || '').trim();
      if (!title || Array.from(title).length > 100) return wx.showToast({ title: '标题需为 1～100 字', icon: 'none' });
      try {
        const renamed = await this.ownedRequest(CONVERSATIONS + '/' + id, 'PATCH', { title }, version);
        if (!this.isCurrent(version)) return;
        if (id === this.data.activeConversationId) this.setData({ conversationTitle: renamed.title });
        await this.loadConversations(false);
      } catch (error) { if (this.isCurrent(version) && !this.expired(error)) wx.showToast({ title: '重命名失败', icon: 'none' }); }
    }});
  },
  deleteConversation(event) {
    const id = Number(event.currentTarget.dataset.id);
    if (!validId(id) || this.data.asking || this.data.conversationLoading) return;
    const version = this._requestVersion;
    wx.showModal({ title: '删除会话', content: '删除后无法恢复这段会话，是否继续？', confirmColor: '#b75b5b', success: async result => {
      if (!result.confirm || !this.isCurrent(version)) return;
      try {
        await this.ownedRequest(CONVERSATIONS + '/' + id, 'DELETE', {}, version);
        if (!this.isCurrent(version)) return;
        if (id === this.data.activeConversationId) this.clearConversation();
        await this.loadConversations(false);
      } catch (error) { if (this.isCurrent(version) && !this.expired(error)) wx.showToast({ title: '删除失败', icon: 'none' }); }
    }});
  },
  onInput(event) { this.setData({ inputValue: event.detail.value }); },
  askSuggested(event) { if (this.data.asking) return; this.setData({ inputValue: event.currentTarget.dataset.question }); return this.sendMessage(); },
  toggleExecution(event) { const id = event.currentTarget.dataset.id; this.setData({ messages: this.data.messages.map(message => message.id === id ? { ...message, expanded: !message.expanded } : message) }); },
  manageTranscript() { wx.navigateTo({ url: '/pages/academic/academic' }); },
  addMessage(role, content, notice = '') {
    const id = 'message-' + (++this._nextId);
    this.setData({ messages: [...this.data.messages, { id, role, content, notice, nodes: formatMessage(content), steps: role === 'assistant' ? this.data.executionSteps : [], evidence: role === 'assistant' ? this.data.evidence : [], expanded: false }], bottomId: id });
  },
  async sendMessage() {
    if (this._unloaded || this.data.asking || this.data.loadingMessages || this.data.conversationLoading || !this.ensureSession()) return;
    const question = this.data.inputValue.trim();
    if (!question) return;
    if (question.length > MAX_MESSAGE) return wx.showToast({ title: '问题请控制在2000字以内', icon: 'none' });
    const version = ++this._requestVersion;
    this.addMessage('user', question);
    this.setData({ inputValue: '', asking: true, bottomId: 'agent-thinking', executionSteps: [], evidence: [], currentStage: '正在连接学业助手' });
    wx.showNavigationBarLoading();
    try {
      if (!this.data.activeConversationId) {
        const conversation = await this.ownedRequest(CONVERSATIONS, 'POST', {}, version);
        if (!this.isCurrent(version)) return;
        if (!conversation || !validId(conversation.id)) throw new Error('会话创建失败');
        this.setData({ activeConversationId: Number(conversation.id), conversationTitle: conversation.title });
      }
      const result = await request(ENDPOINT, 'POST', { conversationId: this.data.activeConversationId, message: question }, {
        timeout: REQUEST_TIMEOUT, stream: true,
        onEvent: event => {
          if (!this.isCurrent(version)) return;
          if (event.type === 'progress') { const steps = updateSteps(this.data.executionSteps, event.data); this.setData({ executionSteps: steps, currentStage: event.data.state === 'running' ? event.data.label : this.data.currentStage }); }
          else if (event.type === 'evidence') { const evidence = normalizeEvidence(event.data); if (evidence) this.setData({ evidence: [...this.data.evidence.filter(item => item.kind !== evidence.kind), evidence] }); }
        },
        onTask: task => { if (this.isCurrent(version)) { this._requestTask = task; this.setData({ realtimeAvailable: typeof task.onChunkReceived === 'function' }); } }
      });
      if (!this.isCurrent(version)) return;
      if (!result || Number(result.conversationId) !== this.data.activeConversationId || typeof result.answer !== 'string' || !result.answer.trim() || !['COMPLETED', 'DATA_UNAVAILABLE', 'TOOL_LIMIT', 'REFUSED', 'OUT_OF_SCOPE', 'NEEDS_CLARIFICATION'].includes(result.status)) throw new Error('暂未收到有效回答，请刷新会话后重试。');
      const notices = { TOOL_LIMIT: '本次仅取得部分结果', DATA_UNAVAILABLE: '暂未取得完整分析', REFUSED: '仅支持查询本人', OUT_OF_SCOPE: '当前能力范围', NEEDS_CLARIFICATION: '请补充问题' };
      this.setData({ executionSteps: endSteps(this.data.executionSteps, false) }); this.addMessage('assistant', result.answer, notices[result.status] || '');
      await this.loadConversations(false);
      const active = this.data.conversationList.find(item => Number(item.id) === this.data.activeConversationId);
      if (this.isCurrent(version) && active) this.setData({ conversationTitle: active.title });
    } catch (error) {
      if (!this.isCurrent(version) || this.expired(error)) return;
      this.setData({ executionSteps: endSteps(this.data.executionSteps, true) });
      this.addMessage('assistant', errorMessage(error), '本次未完整接收，请刷新会话确认保存状态'); this.setData({ inputValue: question });
      if (error && (error.statusCode === 404 || error.code === 404)) this.setData({ activeConversationId: null, conversationTitle: '新会话' });
    } finally { if (this.isCurrent(version)) { this._requestTask = null; this.setData({ asking: false }); wx.hideNavigationBarLoading(); } }
  }
});
