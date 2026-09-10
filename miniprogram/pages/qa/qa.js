const { request, BASE_URL } = require('../../utils/request.js');

const getStudentNo = () => wx.getStorageSync('studentNo') || wx.getStorageSync('account');

const pickAnswer = (payload) => {
  if (!payload) return '';
  if (typeof payload === 'string') return payload;
  if (payload.answer) return payload.answer;
  if (payload.data && payload.data.answer) return payload.data.answer;
  if (payload.data && payload.data.data && payload.data.data.answer) return payload.data.data.answer;
  if (payload.result && payload.result.answer) return payload.result.answer;
  return '';
};

Page({
  data: {
    inputValue: '',
    bottomId: '',
    chatList: [{ role: 'ai', content: '同学你好，我是智能助手，已连接政策数据库。' }],
    templates: []
  },

  onLoad() {
    this.fetchTemplates();
  },

  fetchTemplates() {
    request('/api/file/list', 'GET', { businessType: 'template' })
      .then(res => {
        this.setData({ templates: Array.isArray(res) ? res : [] });
      });
  },

  onInput(e) {
    this.setData({ inputValue: e.detail.value });
  },

  askAI() {
    const question = this.data.inputValue.trim();
    if (!question) return;
    const studentNo = getStudentNo();

    const newChatList = [...this.data.chatList, { role: 'user', content: question }];
    this.setData({ chatList: newChatList, inputValue: '', bottomId: 'scroll-bottom' });

    wx.showNavigationBarLoading();
    request('/api/student/ai/ask', 'POST', { question, studentNo }).then(res => {
      const answer = pickAnswer(res) || '未获取到答复。';
      this.setData({
        chatList: [...this.data.chatList, { role: 'ai', content: answer }],
        bottomId: 'scroll-bottom'
      });
    }).catch(err => {
      this.setData({
        chatList: [...this.data.chatList, { role: 'ai', content: '知识库服务连接异常。' }],
        bottomId: 'scroll-bottom'
      });
    }).finally(() => {
      wx.hideNavigationBarLoading();
    });
  },

  downloadTemplate(e) {
    const fileId = e.currentTarget.dataset.id;
    wx.showLoading({ title: '准备下载...' });
    wx.downloadFile({
      url: `${BASE_URL}/api/file/download/${fileId}`,
      header: { 'Authorization': wx.getStorageSync('token') },
      success: (res) => {
        if (res.statusCode === 200) {
          wx.openDocument({ filePath: res.tempFilePath, showMenu: true });
        }
      },
      complete: () => wx.hideLoading()
    });
  }
})