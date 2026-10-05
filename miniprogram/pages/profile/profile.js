const { request } = require('../../utils/request.js');

Page({
  data: {
    account: '',
    role: '',
    studentNo: '',
    name: '',
    userInfo: {},
    earnedCredits: 0,
    showCourses: false,
    myCourses: [], loading: false, loadError: false, initial: '学'
  },

  onShow() {
    this.setData({
      account: wx.getStorageSync('account') || '',
      studentNo: wx.getStorageSync('studentNo') || wx.getStorageSync('account') || '',
      role: wx.getStorageSync('role') || 'student'
    });
    this.fetchRealData();
  },

  toggleCourses() {
    this.setData({ showCourses: !this.data.showCourses });
  },

  fetchRealData() {
    if (this.data.loading) return;
    this.setData({ loading: true, loadError: false });
    request('/api/student/info', 'GET', { account: this.data.studentNo })
      .then(res => {
        this.setData({
          studentNo: this.data.studentNo,
          name: (res.userInfo && res.userInfo.name) || '学生',
          initial: ((res.userInfo && res.userInfo.name) || '学').slice(0, 1),
          userInfo: res.userInfo || {},
          myCourses: res.courses || [],
          earnedCredits: res.totalCredits || 0
        });
      })
      .catch(err => {
        this.setData({ loadError: true });
        console.log('/api/student/info 查询失败：', err);
        wx.showToast({ title: '学业数据读取失败', icon: 'none' });
        this.setData({
          name: '未知',
          userInfo: {},
          myCourses: [],
          earnedCredits: 0
        });
      }).finally(() => this.setData({ loading: false }));
  },

  handleLogout() {
    wx.showModal({
      title: '提示',
      content: '确定安全退出当前系统账号？',
      confirmColor: '#ff4d4f',
      success: (res) => {
        if (res.confirm) {
          ['token', 'account', 'studentNo', 'username', 'role'].forEach(key => wx.removeStorageSync(key));
          wx.reLaunch({ url: '/pages/login/login' });
        }
      }
    });
  }
});
