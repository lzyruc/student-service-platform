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
    myCourses: []
  },

  onShow() {
    this.setData({
      account: wx.getStorageSync('account') || '20240001',
      studentNo: wx.getStorageSync('studentNo') || wx.getStorageSync('account') || '20240001',
      role: wx.getStorageSync('role') || 'student'
    });
    this.fetchRealData();
  },

  toggleCourses() {
    this.setData({ showCourses: !this.data.showCourses });
  },

  fetchRealData() {
    request('/api/student/info', 'GET', { account: this.data.studentNo })
      .then(res => {
        this.setData({
          studentNo: this.data.studentNo,
          name: (res.userInfo && res.userInfo.name) || '未知',
          userInfo: res.userInfo || { name: '未知', major: '信息学院' },
          myCourses: res.courses || [],
          earnedCredits: res.totalCredits || 0
        });
      })
      .catch(err => {
        console.log('/api/student/info 查询失败：', err);
        wx.showToast({ title: '学业数据读取失败', icon: 'none' });
        this.setData({
          name: '未知',
          userInfo: { name: '未知', major: '信息学院' },
          myCourses: [],
          earnedCredits: 0
        });
      });
  },

  handleLogout() {
    wx.showModal({
      title: '提示',
      content: '确定安全退出当前系统账号？',
      confirmColor: '#ff4d4f',
      success: (res) => {
        if (res.confirm) {
          wx.clearStorageSync();
          wx.reLaunch({ url: '/pages/login/login' });
        }
      }
    });
  }
});
