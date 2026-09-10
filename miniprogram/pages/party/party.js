const { request } = require('../../utils/request.js');

const getStudentNo = () => wx.getStorageSync('studentNo') || wx.getStorageSync('account');

const getField = (item, camelKey, snakeKey) => item[camelKey] !== undefined ? item[camelKey] : item[snakeKey];

const normalizeStatus = (status) => {
  const text = String(status || '未开始');
  if (text.includes('已完成')) return { code: 'completed', text: '已完成' };
  if (text.includes('进行中')) return { code: 'active', text: '进行中' };
  if (text.includes('暂停')) return { code: 'paused', text: '暂停' };
  return { code: 'pending', text: '未开始' };
};

Page({
  data: {
    currentStageName: '',
    steps: []
  },

  onShow() {
    this.fetchRealDBProgress();
  },

  fetchRealDBProgress() {
    wx.showNavigationBarLoading();
    request('/api/student/party/progress', 'GET', {
      studentNo: getStudentNo()
    }).then(res => {
      const progressList = Array.isArray(res) ? res : [];
      const steps = progressList.map((item, index) => {
        const status = normalizeStatus(getField(item, 'stageStatus', 'stage_status'));
        const startDate = getField(item, 'startDate', 'start_date');
        return {
          stageId: getField(item, 'stageId', 'stage_id') || index + 1,
          orderNum: getField(item, 'orderNum', 'order_num') || index + 1,
          stageName: getField(item, 'stageName', 'stage_name') || '未命名阶段',
          description: getField(item, 'stageDescription', 'description') || '',
          time: startDate ? String(startDate).slice(0, 10) : '',
          status: status.code,
          statusText: status.text
        };
      });

      const activeStage = steps.find(item => item.status === 'active');
      const pausedStage = steps.find(item => item.status === 'paused');
      const completedSteps = steps.filter(item => item.status === 'completed');
      const lastCompleted = completedSteps[completedSteps.length - 1];
      const currentStageName = activeStage
        ? activeStage.stageName
        : pausedStage
          ? `暂停：${pausedStage.stageName}`
          : lastCompleted
            ? `已完成：${lastCompleted.stageName}`
            : '尚未开启流程';

      this.setData({ steps, currentStageName });
    }).catch(err => {
      console.error('流程获取失败', err);
      wx.showToast({ title: '党团进度读取失败', icon: 'none' });
      this.setData({ currentStageName: '读取失败', steps: [] });
    }).finally(() => wx.hideNavigationBarLoading());
  }
});