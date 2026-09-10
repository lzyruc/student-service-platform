App({
  onLaunch() {
    console.log('平台小程序初始化完成');
    // 这里后续可以加上检查本地 Storage 中是否有 Token 的逻辑
  },
  globalData: {
    userInfo: null,
    token: null
  }
});