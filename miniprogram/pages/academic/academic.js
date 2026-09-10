const { BASE_URL } = require('../../utils/request.js');

const getStudentNo = () => wx.getStorageSync('studentNo') || wx.getStorageSync('account');

Page({
  data: { report: null },
  uploadTranscript() {
    const studentNo = getStudentNo();
    wx.chooseMessageFile({
      count: 1,
      type: 'file',
      extension: ['pdf'],
      success: (res) => {
        wx.showLoading({ title: 'AI 引擎比对中...' });
        wx.uploadFile({
          url: BASE_URL + '/api/student/warning/analyze',
          filePath: res.tempFiles[0].path,
          name: 'file',
          formData: { studentNo },
          header: { 'Authorization': wx.getStorageSync('token') },
          success: (uploadRes) => {
            wx.hideLoading();
            let result;
            try {
              result = JSON.parse(uploadRes.data);
            } catch (e) {
              wx.showToast({ title: '响应解析失败', icon: 'none' });
              return;
            }
            if (result.code === 200) {
              const payload = result.data || {};
              const pythonData = payload.pythonResponse && payload.pythonResponse.data ? payload.pythonResponse.data : {};
              const payloadData = payload.data || {};
              const report = pythonData.report || payloadData.report || payload.report || payload || {};

              const warningLevel = String(report.warning_level || report.riskLevel || '');
              const missingCoreCourses = report.missing_core_courses || [];

              this.setData({
                report: {
                  riskLevel: warningLevel.includes('低') || warningLevel.includes('正常') || warningLevel.toLowerCase() === 'low' ? 'low' : 'high',
                  missingCredits: report.missingCredits || report.missing_credits || missingCoreCourses.length,
                  suggestions: report.course_suggestions || report.suggestions || payloadData.suggestions || []
                }
              });
              wx.showToast({ title: '分析完成', icon: 'success' });
            } else {
              wx.showToast({ title: result.message || '解析失败', icon: 'none' });
            }
          },
          fail: () => {
            wx.hideLoading();
            wx.showToast({ title: '网络请求失败', icon: 'none' });
          }
        });
      }
    });
  }
});
