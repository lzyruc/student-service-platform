const { request } = require('../../utils/request.js');

Page({
  data: {
    account: '',
    password: ''
  },

  onAccountInput(e) {
    this.setData({ account: e.detail.value });
  },

  onPasswordInput(e) {
    this.setData({ password: e.detail.value });
  },

  handleLogin() {
    const { account, password } = this.data;
    if (!account) return wx.showToast({ title: '请输入学号', icon: 'none' });
    if (!password) return wx.showToast({ title: '请输入密码', icon: 'none' });

    wx.showLoading({ title: '数据库校验中...' });

    request('/api/auth/login', 'POST', {
      username: account,
      password: password,
      role: 'student'
    }).then(res => {
      wx.hideLoading();
      const studentNo = res.student_no || res.studentNo || res.STUDENT_NO || account;
      wx.setStorageSync('token', res.token);
      wx.setStorageSync('account', account);
      wx.setStorageSync('role', 'student');
      wx.setStorageSync('studentNo', studentNo);
      wx.setStorageSync('username', res.username || res.USERNAME || '');

      wx.showToast({ title: '登录成功', icon: 'success' });
      setTimeout(() => {
        wx.switchTab({ url: '/pages/main/main' });
      }, 800);
    }).catch(err => {
      wx.hideLoading();
      console.error('登录失败：', err);
      let errMsg = '登录失败，请检查账号或后端服务';
      if (err && err.message) errMsg = err.message;
      if (err && err.errMsg) errMsg = err.errMsg;
      wx.showToast({ title: errMsg, icon: 'none' });
    });
  }
});
