<template>
  <div class="product-login">
    <section class="login-story">
      <div class="login-brand"><span class="product-brand-mark">学</span><span>学生综合服务平台</span></div>
      <div class="story-content">
        <div class="product-kicker">校园事务管理</div>
        <h1>学生事务，<br />一处办理。</h1>
        <p>管理学生档案、通知与证明申请，<br />维护培养方案和政策知识库。</p>
        <div class="story-pillars"><span>学生档案</span><span>事务协同</span><span>学业与政策</span></div>
      </div>
      <small>学生综合服务平台</small>
    </section>
    <section class="login-form">
      <div class="product-kicker">管理端登录</div>
      <h2>欢迎回到工作台</h2>
      <p>使用管理员账号登录，开始处理校园事务。</p>
      <LoginForm />
      <div class="login-note">
        <el-icon><Lock /></el-icon>学生请在微信小程序使用本人账号登录
      </div>
    </section>
  </div>
</template>
<script setup lang="ts" name="login">
import LoginForm from "./components/LoginForm.vue";
</script>
<style scoped>
.product-login {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  background: #fff;
  color: #302624;
}
.login-story {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px 10%;
  background: #fff2ed;
  position: relative;
  overflow: hidden;
}
.login-story::after {
  content: "";
  position: absolute;
  width: 360px;
  height: 360px;
  border: 1px solid #f1c9bd;
  border-radius: 50%;
  right: -160px;
  bottom: 90px;
  box-shadow:
    0 0 0 50px #fff2ed,
    0 0 0 51px #f5d9cf;
  pointer-events: none;
}
.login-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 16px;
  font-weight: 600;
}
.story-content {
  z-index: 1;
}
.story-content h1 {
  font-size: 54px;
  line-height: 1.3;
  font-weight: 650;
  letter-spacing: -2px;
  margin: 18px 0 24px;
}
.story-content p {
  color: #7a8499;
  font-size: 15px;
  line-height: 2;
}
.story-pillars {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 32px;
}
.story-pillars span {
  padding: 8px 12px;
  font-size: 11px;
  color: #65718b;
  border: 1px solid #dce3f2;
  border-radius: 6px;
}
.login-story small {
  color: #98a2b5;
  letter-spacing: 2px;
  font-size: 10px;
}
.login-form {
  max-width: 360px;
  width: calc(100% - 64px);
  align-self: center;
  justify-self: center;
  padding: 48px 0;
}
.login-form h2 {
  font-size: 28px;
  font-weight: 600;
  margin: 14px 0;
}
.login-form > p {
  font-size: 13px;
  color: #7a8499;
  margin-bottom: 36px;
  line-height: 1.7;
}
.login-note {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #98a2b5;
  margin-top: 32px;
}
.login-form :deep(.el-button) {
  border-radius: 8px;
}
.login-form :deep(.login-btn) {
  display: flex;
  gap: 12px;
}
.login-form :deep(.el-button) {
  flex: 1;
  margin: 0;
}
@media (max-width: 850px) {
  .product-login {
    grid-template-columns: 1fr;
  }
  .login-story {
    display: none;
  }
}
</style>
