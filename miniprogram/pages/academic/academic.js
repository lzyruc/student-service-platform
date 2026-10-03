const { request, buildRequestUrl } = require('../../utils/request.js');
const { normalizeAcademicReport } = require('../../utils/academic-report.js');

Page({
  data: {
    report: null, transcript: null, loadingTranscript: false,
    uploading: false, analyzing: false, transcriptError: ''
  },
  onShow() {
    this.loadTranscript();
  },
  async loadTranscript() {
    if (this.data.loadingTranscript || this.data.uploading || this.data.analyzing) return;
    this.setData({ loadingTranscript: true, transcriptError: '' });
    try {
      const transcript = await request('/api/student/transcript');
      const changed = !this.data.transcript || !transcript || this.data.transcript.fileId !== transcript.fileId;
      this.setData({ transcript, ...(changed ? { report: null } : {}) });
    } catch (error) {
      this.setData({ transcript: null, report: null, transcriptError: error.message || '成绩单信息加载失败，请重试' });
    } finally {
      this.setData({ loadingTranscript: false });
    }
  },
  uploadTranscript() {
    if (this.data.uploading || this.data.analyzing || this.data.loadingTranscript) return;
    this.setData({ uploading: true });
    wx.chooseMessageFile({
      count: 1, type: 'file', extension: ['pdf'],
      success: (res) => {
        if (!res.tempFiles || !res.tempFiles.length) {
          this.setData({ uploading: false });
          return;
        }
        wx.showLoading({ title: '正在保存成绩单...' });
        wx.uploadFile({
          url: buildRequestUrl('/api/student/transcript'),
          filePath: res.tempFiles[0].path, name: 'file', timeout: 60000,
          header: { Authorization: wx.getStorageSync('token') || '' },
          success: (response) => {
            let result;
            try { result = JSON.parse(response.data); }
            catch (error) {
              wx.showToast({ title: '响应解析失败，请重新加载确认是否已保存', icon: 'none' });
              return;
            }
            if (response.statusCode >= 400 || result.code !== 200) {
              wx.showToast({ title: result.message || '成绩单保存失败', icon: 'none' });
              return;
            }
            this.setData({ transcript: result.data, report: null, transcriptError: '' });
            wx.showToast({ title: '已保存，点击课程分析', icon: 'none' });
          },
          fail: () => wx.showToast({ title: '上传失败，请检查网络', icon: 'none' }),
          complete: () => {
            wx.hideLoading();
            this.setData({ uploading: false });
          }
        });
      },
      fail: () => this.setData({ uploading: false })
    });
  },
  async analyzeSavedTranscript() {
    if (this.data.analyzing || this.data.uploading || this.data.loadingTranscript) return;
    if (!this.data.transcript || !this.data.transcript.available) {
      wx.showToast({ title: '请先上传可用的成绩单', icon: 'none' });
      return;
    }
    this.setData({ analyzing: true, report: null });
    wx.showLoading({ title: '正在进行课程分析...' });
    try {
      const payload = await request('/api/student/warning/analyze-saved', 'POST', {}, { timeout: 60000 });
      const pythonData = payload.pythonResponse && payload.pythonResponse.data ? payload.pythonResponse.data : {};
      const payloadData = payload.data || {};
      const report = pythonData.report || payloadData.report || payload.report || {};
      this.setData({ report: normalizeAcademicReport(report, pythonData.course_count || payloadData.course_count) });
    } catch (error) {
      if (error.statusCode === 404) this.setData({ transcriptError: error.message || '请重新加载成绩单信息' });
      wx.showToast({ title: error.message || '课程分析失败，可稍后重新分析', icon: 'none' });
    } finally {
      wx.hideLoading();
      this.setData({ analyzing: false });
    }
  }
});
