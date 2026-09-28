const { request, BASE_URL } = require('../../utils/request.js');

const getStudentNo = () => wx.getStorageSync('studentNo') || wx.getStorageSync('account');

Page({
  data: {
    approvalCertTypes: ['在校证明', '请假条', '用章申请'],
    approvalTypeIndex: 0,
    approvalReason: '',
    approvalStartDate: '',
    approvalEndDate: '',
    approvalHistoryList: [],
    expandedCertId: null,
  },

  onShow() {
    this.fetchHistory();
  },

  bindApprovalTypeChange(e) {
    const idx = Number(e.detail.value);
    this.setData({ approvalTypeIndex: Number.isFinite(idx) ? idx : 0 });
  },

  toggleReason(e) {
    const id = e.currentTarget.dataset.id;
    this.setData({ expandedCertId: this.data.expandedCertId === id ? null : id });
  },

  onApprovalReasonInput(e) {
    this.setData({ approvalReason: e.detail.value });
  },

  onApprovalStartDateChange(e) {
    this.setData({ approvalStartDate: e.detail.value });
  },

  onApprovalEndDateChange(e) {
    this.setData({ approvalEndDate: e.detail.value });
  },

  fetchHistory() {
    request('/api/student/certificate/history', 'GET', { studentNo: getStudentNo() })
      .then(res => {
        const approvalHistoryList = Array.isArray(res)
          ? res.map(item => ({
              id: item.id,
              certificateType: item.certificateType || item.certificatetype,
              applyStatus: item.applyStatus || item.applystatus,
              extraData: item.extraData || item.extradata || '未填写申请事由',
              createdAt: item.createdAt || item.createdat,
              fileId: item.fileId || item.fileid
            }))
          : [];
        this.setData({ approvalHistoryList });
      });
  },

  submitApprovalCert() {
    wx.showLoading({ title: '提交申请中...' });
    const payload = {
      reason: this.data.approvalReason || '无',
      startAt: this.data.approvalStartDate || '',
      endAt: this.data.approvalEndDate || ''
    };
    request('/api/student/certificate/apply', 'POST', {
      studentNo: getStudentNo(),
      certificateType: this.data.approvalCertTypes[this.data.approvalTypeIndex],
      extraData: JSON.stringify(payload)
    }).then(() => {
      wx.hideLoading();
      wx.showToast({ title: '已提交', icon: 'success' });
      this.setData({ approvalReason: '', approvalStartDate: '', approvalEndDate: '', expandedCertId: null });
      this.fetchHistory();
    }).catch(err => {
      wx.hideLoading();
      wx.showToast({ title: err.message || err.errMsg || '提交失败', icon: 'none' });
    });
  },

  downloadCert(e) {
    const fileId = e.currentTarget.dataset.fileid;
    wx.showLoading({ title: '正在获取文件...' });
    wx.downloadFile({
      url: `${BASE_URL}/api/file/download/${fileId}`,
      header: { Authorization: wx.getStorageSync('token') },
      success: (res) => {
        if (res.statusCode === 200) {
          wx.openDocument({ filePath: res.tempFilePath, showMenu: true });
        } else {
          wx.showToast({ title: '文件下载失败', icon: 'none' });
        }
      },
      complete: () => wx.hideLoading()
    });
  }
});
