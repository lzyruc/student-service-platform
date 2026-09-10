const { request, BASE_URL } = require('../../utils/request.js');

const getStudentNo = () => wx.getStorageSync('studentNo') || wx.getStorageSync('account');
const getOpenFileType = (fileType) => {
  const raw = String(fileType || '').toLowerCase();
  if (!raw) return '';
  if (raw.includes('pdf')) return 'pdf';
  if (raw.includes('word') || raw.includes('docx')) return 'docx';
  if (raw.includes('doc')) return 'doc';
  if (raw.includes('excel') || raw.includes('xlsx')) return 'xlsx';
  if (raw.includes('xls')) return 'xls';
  if (raw.includes('pptx')) return 'pptx';
  if (raw.includes('powerpoint') || raw.includes('ppt')) return 'ppt';
  if (raw.includes('text') || raw.includes('txt')) return 'txt';
  return '';
};

Page({
  data: {
    notices: []
  },

  onShow() {
    this.fetchNotices();
  },

  fetchNotices() {
    request('/api/student/notice/list', 'GET', { studentNo: getStudentNo() })
      .then(res => {
        this.setData({ notices: res || [] });
      });
  },

  downloadAttachment(e) {
    const { fileid, filetype } = e.currentTarget.dataset;
    if (!fileid) return;

    const openFileType = getOpenFileType(filetype);

    wx.showLoading({ title: '正在打开附件...' });
    wx.downloadFile({
      url: `${BASE_URL}/api/file/download/${fileid}`,
      header: { 'Authorization': wx.getStorageSync('token') },
      success: (res) => {
        if (res.statusCode !== 200) {
          wx.showToast({ title: '附件下载失败', icon: 'none' });
          return;
        }

        if (String(filetype || '').startsWith('image/')) {
          wx.previewImage({
            urls: [res.tempFilePath],
            fail: () => wx.showToast({ title: '图片预览失败', icon: 'none' })
          });
        } else {
          wx.openDocument({
            filePath: res.tempFilePath,
            fileType: openFileType,
            showMenu: true,
            fail: (err) => {
              console.log('openDocument 失败：', err);
              wx.showToast({ title: '附件打开失败', icon: 'none' });
            }
          });
        }
      },
      fail: () => wx.showToast({ title: '附件下载失败', icon: 'none' }),
      complete: () => wx.hideLoading()
    });
  },

  confirmRead(e) {
    const noticeId = e.currentTarget.dataset.id;
    wx.showLoading({ title: '确认中...' });

    request('/api/student/notice/confirm', 'POST', { 
      notificationId: noticeId, 
      studentNo: getStudentNo()
    }).then(() => {
      wx.hideLoading();
      wx.showToast({ title: '回执成功', icon: 'success' });
      this.fetchNotices();
    }).catch(() => {
      wx.hideLoading();
      wx.showToast({ title: '回执提交失败', icon: 'none' });
    });
  }
})