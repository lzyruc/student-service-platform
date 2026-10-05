<template>
  <div class="product-page dashboard">
    <PageHeader
      eyebrow="STUDENT INTELLIGENCE"
      title="学生服务概览"
      description="把学生事务、学业支持与 AI 能力，连接到同一个工作台。"
    >
      <span class="connection-pill" :class="{ online: health === true }"
        ><i />{{ health === true ? "后端已连接" : health === false ? "连接待检查" : "正在检查连接" }}</span
      >
      <el-button :loading="loading" @click="load">刷新概览</el-button>
    </PageHeader>
    <div v-if="errors.length" class="partial-error" role="alert">
      <el-icon><Warning /></el-icon>{{ errors.join("、") }}暂时无法读取，其余可用数据仍然展示。<el-button link @click="load"
        >重试</el-button
      >
    </div>
    <div class="kpi-grid">
      <KpiCard
        label="学生档案"
        icon="User"
        :value="students === null ? null : studentRows.length"
        unit="人"
        :loading="loading"
        caption="当前登记的学生，不含管理员"
      />
      <KpiCard
        label="待处理审批"
        icon="DocumentChecked"
        :value="applies === null ? null : pendingCount"
        unit="项"
        tone="orange"
        :loading="loading"
        caption="等待审核的电子证明申请"
      />
      <KpiCard
        label="已发布通知"
        icon="Bell"
        :value="notices === null ? null : notices.length"
        unit="条"
        :loading="loading"
        caption="已发布的通知与确认回执"
      />
      <KpiCard
        label="可检索政策"
        icon="Collection"
        :value="readyPolicies"
        unit="份"
        tone="green"
        :loading="loading"
        caption="已发布并完成知识库入库"
      />
    </div>
    <div class="dashboard-grid">
      <section class="product-panel">
        <div class="panel-heading">
          <div>
            <h2>学生年级分布</h2>
            <p>来自当前学生档案 · 人数</p>
          </div>
          <el-icon><Histogram /></el-icon>
        </div>
        <el-skeleton v-if="loading" animated :rows="6" /><DistributionChart
          v-else
          title="学生年级分布"
          :values="grades"
          :empty-text="students === null ? '学生档案暂时不可用' : '还没有学生档案'"
        />
      </section>
      <section class="product-panel">
        <div class="panel-heading">
          <div>
            <h2>通知确认概况</h2>
            <p>按通知汇总回执 · 人次</p>
          </div>
          <el-icon><CircleCheck /></el-icon>
        </div>
        <el-skeleton v-if="loading" animated :rows="6" /><DistributionChart
          v-else
          title="通知确认概况"
          kind="donut"
          :values="receipts"
          :empty-text="notices === null ? '通知回执暂时不可用' : '暂无通知接收记录'"
        />
      </section>
      <section class="product-panel activities">
        <div class="panel-heading">
          <div>
            <h2>最近业务动态</h2>
            <p>真实记录的创建或更新时间</p>
          </div>
          <span class="small-label">{{ activityUnavailable ? "暂不可用" : `最近 ${activities.length} 条` }}</span>
        </div>
        <el-skeleton v-if="loading" animated :rows="4" /><el-empty
          v-else-if="!activities.length"
          :description="activityUnavailable ? '业务动态暂时不可用' : '还没有业务动态'"
          :image-size="50"
        /><button v-for="(item, index) in activities" :key="item.key" class="activity" @click="router.push(item.path)">
          <span class="activity-dot" :class="item.kind"
            ><el-icon><component :is="item.icon" /></el-icon></span
          ><span class="activity-text"
            ><strong>{{ item.title }}</strong
            ><small>{{ item.subtitle }}</small></span
          ><time>{{ formatTime(item.time) }}</time>
        </button>
      </section>
      <section class="product-panel shortcuts">
        <div class="panel-heading">
          <div>
            <h2>待办与快捷操作</h2>
            <p>从今天的业务开始</p>
          </div>
        </div>
        <button @click="router.push('/college/examineApprove')" class="todo">
          <span class="todo-num">{{ applies === null ? "—" : pendingCount }}</span>
          <div>
            <strong>电子证明待审核</strong>
            <p>查看申请、审批并生成证明</p>
          </div>
          <el-icon><ArrowRight /></el-icon>
        </button>
        <div class="quick-grid">
          <button @click="router.push('/college/studentInformation')">
            <el-icon><User /></el-icon>学生档案</button
          ><button @click="router.push('/college/notification')">
            <el-icon><Bell /></el-icon>发布通知</button
          ><button @click="router.push('/college/trainingPlan')">
            <el-icon><Reading /></el-icon>培养方案</button
          ><button @click="router.push('/college/knowledgeBase')">
            <el-icon><Collection /></el-icon>政策管理
          </button>
        </div>
      </section>
    </div>
    <div class="ai-heading">
      <div>
        <div class="product-kicker">AI, CONNECTED TO YOUR WORK</div>
        <h2>让已有数据，成为学生可用的答案</h2>
      </div>
      <span class="product-caption">学业分析仅面向学生本人 · 请在小程序中使用智能助手</span>
    </div>
    <div class="capability-grid">
      <CapabilityCard
        title="学业分析 Agent"
        icon="MagicStick"
        badge="学生端"
        description="读取本人学业资料，调用工具并解释结果。分析过程可实时查看。"
        @open="capabilityVisible = true"
      /><CapabilityCard
        title="政策知识库 RAG"
        icon="Collection"
        badge="已接入"
        description="统一维护政策、发布与入库，让学生问答有可追溯依据。"
        @open="router.push('/college/knowledgeBase')"
      /><CapabilityCard
        title="成绩趋势与课程关注"
        icon="TrendCharts"
        badge="成绩单统计"
        description="按课程学分统计学期 GPA 与成绩变化，帮助学生关注低分课程。"
        @open="capabilityVisible = true"
      />
    </div>
    <div class="data-footnote">
      数据范围：学生档案、通知回执、证明申请与已发布政策。此页面暂不提供全校学业风险汇总与历史增长分析。
    </div>
    <el-dialog v-model="capabilityVisible" title="学业智能助手 · 学生端专属" width="600px"
      ><p class="capability-intro">学生在小程序服务大厅打开“学业智能助手”，即可基于已保存的成绩单和匹配培养方案提问。</p>
      <ol class="capability-flow">
        <li>验证当前学生身份</li>
        <li>按需读取学业信息与分析快照</li>
        <li>查询整体评估、近期课程或成绩趋势</li>
        <li>解释工具结果，展示真实执行记录</li>
      </ol>
      <el-alert
        title="管理端账号不代替学生身份查询个人成绩；普通 Agent 查询不会生成正式预警记录。"
        type="info"
        :closable="false"
      /><template #footer
        ><el-button @click="capabilityVisible = false">了解了</el-button
        ><el-button
          type="primary"
          @click="
            router.push('/college/trainingPlan');
            capabilityVisible = false;
          "
          >维护培养方案</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<script setup lang="ts" name="home">
import { ref, computed, onMounted } from "vue";
import { useRouter } from "vue-router";
import http from "@/api";
import PageHeader from "@/components/product/PageHeader.vue";
import KpiCard from "@/components/dashboard/KpiCard.vue";
import DistributionChart from "@/components/dashboard/DistributionChart.vue";
import CapabilityCard from "@/components/dashboard/CapabilityCard.vue";
import { listStudents, BackendStudent } from "@/api/modules/student";
import { listNotifications, BackendNotification } from "@/api/modules/notification";
import { listCertificateApplies, BackendCertificate } from "@/api/modules/certificate";
import { listPolicyDocuments, PolicyDocumentApi } from "@/api/modules/policyDocument";
const router = useRouter();
const loading = ref(false),
  health = ref<boolean | null>(null),
  errors = ref<string[]>([]),
  capabilityVisible = ref(false);
const students = ref<BackendStudent.StudentListItem[] | null>(null),
  notices = ref<BackendNotification.NotificationItem[] | null>(null),
  applies = ref<BackendCertificate.ApplyItem[] | null>(null),
  policies = ref<PolicyDocumentApi.Item[] | null>(null),
  readyPolicies = ref<number | null>(null);
const studentRows = computed(() => (students.value || []).filter(s => s.roleCode === "student"));
const pendingCount = computed(() => (applies.value || []).filter(a => a.applyStatus === "待审核").length);
const grades = computed(() => {
  const counts = new Map<string, number>();
  studentRows.value.forEach(s => {
    const grade = s.grade ? `${s.grade}级` : "未填年级";
    counts.set(grade, (counts.get(grade) || 0) + 1);
  });
  return [...counts].sort((a, b) => a[0].localeCompare(b[0])).map(([name, value]) => ({ name, value }));
});
const receipts = computed(() => {
  const rows = notices.value || [];
  const confirmed = rows.reduce((n, r) => n + Math.max(0, r.confirmed_count || 0), 0),
    total = rows.reduce((n, r) => n + Math.max(0, r.total_count || 0), 0);
  return total > 0
    ? [
        { name: "已确认", value: confirmed },
        { name: "待确认", value: Math.max(0, total - confirmed) }
      ]
    : [];
});
const activityUnavailable = computed(() => notices.value === null && applies.value === null && policies.value === null);
const activities = computed(() =>
  [
    ...(notices.value || []).map(n => ({
      key: "n" + n.id,
      title: n.title,
      subtitle: "通知已发布",
      time: n.created_at || "",
      icon: "Bell",
      kind: "blue",
      path: "/college/notification"
    })),
    ...(applies.value || []).map(a => ({
      key: "a" + a.id,
      title: a.certificateType,
      subtitle: a.applyStatus,
      time: a.updatedAt || a.createdAt || "",
      icon: "DocumentChecked",
      kind: "orange",
      path: "/college/examineApprove"
    })),
    ...(policies.value || []).map(p => ({
      key: "p" + p.id,
      title: p.title,
      subtitle: p.ingestStatus === "READY" ? "政策已入库" : "政策已更新",
      time: p.updatedAt || p.createdAt,
      icon: "Collection",
      kind: "green",
      path: "/college/knowledgeBase"
    }))
  ]
    .filter(i => Number.isFinite(Date.parse(i.time)))
    .sort((a, b) => Date.parse(b.time) - Date.parse(a.time))
    .slice(0, 5)
);
const formatTime = (time: string) => time.replace("T", " ").slice(5, 16);
const load = async () => {
  if (loading.value) return;
  loading.value = true;
  errors.value = [];
  const tasks = [
    {
      name: "学生档案",
      run: async () => {
        students.value = (await listStudents(undefined, { silent: true })).data;
      },
      fail: () => (students.value = null)
    },
    {
      name: "证明申请",
      run: async () => {
        applies.value = (await listCertificateApplies(undefined, { silent: true })).data;
      },
      fail: () => (applies.value = null)
    },
    {
      name: "通知回执",
      run: async () => {
        notices.value = (await listNotifications(undefined, { silent: true })).data;
      },
      fail: () => (notices.value = null)
    },
    {
      name: "政策知识库",
      run: async () => {
        const [all, ready] = await Promise.all([
          listPolicyDocuments({ page: 1, pageSize: 5 }, { silent: true }),
          listPolicyDocuments({ page: 1, pageSize: 1, docStatus: "PUBLISHED", ingestStatus: "READY" }, { silent: true })
        ]);
        policies.value = all.data.records;
        readyPolicies.value = ready.data.total;
      },
      fail: () => {
        policies.value = null;
        readyPolicies.value = null;
      }
    },
    {
      name: "后端连接",
      run: async () => {
        await http.get("/health", {}, { loading: false, cancel: false, silent: true });
        health.value = true;
      },
      fail: () => (health.value = false)
    }
  ];
  await Promise.all(
    tasks.map(async t => {
      try {
        await t.run();
      } catch {
        t.fail();
        errors.value.push(t.name);
      }
    })
  );
  loading.value = false;
};
onMounted(load);
</script>
<style scoped>
.dashboard {
  padding-bottom: 16px;
}
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}
.dashboard-grid {
  display: grid;
  grid-template-columns: 1.25fr 1fr;
  gap: 20px;
}
.panel-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18px;
}
.panel-heading h2 {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
}
.panel-heading p {
  font-size: 12px;
  color: var(--product-muted);
  margin: 7px 0 0;
}
.panel-heading > .el-icon {
  color: var(--product-muted);
  font-size: 19px;
}
.small-label {
  font-size: 11px;
  color: var(--product-muted);
}
.connection-pill {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: var(--product-muted);
  background: var(--product-surface);
  border: 1px solid var(--product-line);
  border-radius: 20px;
  padding: 8px 12px;
}
.connection-pill i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #a0aaba;
}
.connection-pill.online i {
  background: #32a581;
}
.partial-error {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 18px;
  padding: 12px 16px;
  border-radius: 8px;
  background: #fff7ed;
  color: #97621e;
  font-size: 12px;
}
.activity {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  text-align: left;
  background: none;
  border: 0;
  border-top: 1px solid var(--product-line);
  padding: 14px 0;
  cursor: pointer;
  color: var(--product-text);
}
.activity:hover .activity-text strong {
  color: var(--product-primary);
}
.activity-dot {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 9px;
  background: #eef2ff;
  color: #425bd8;
  flex-shrink: 0;
}
.activity-dot.orange {
  background: #fff4e8;
  color: #b87e33;
}
.activity-dot.green {
  background: #eaf7f1;
  color: #298b6b;
}
.activity-text {
  display: flex;
  flex-direction: column;
  gap: 5px;
  min-width: 0;
}
.activity-text strong {
  font-size: 12px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 330px;
}
.activity-text small,
.activity time {
  color: var(--product-muted);
  font-size: 11px;
}
.activity time {
  margin-left: auto;
  white-space: nowrap;
}
.todo {
  display: flex;
  align-items: center;
  gap: 18px;
  width: 100%;
  padding: 18px;
  background: #f8f9ff;
  border: 1px solid #e6eaf9;
  border-radius: 10px;
  text-align: left;
  cursor: pointer;
  color: var(--product-text);
}
.todo-num {
  font-size: 30px;
  font-weight: 600;
  color: var(--product-primary);
}
.todo strong {
  font-size: 13px;
  font-weight: 600;
}
.todo p {
  color: var(--product-muted);
  font-size: 12px;
  margin: 6px 0;
}
.todo > .el-icon {
  margin-left: auto;
}
.quick-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 18px;
}
.quick-grid button {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 14px;
  background: var(--product-surface);
  border: 1px solid var(--product-line);
  border-radius: 8px;
  cursor: pointer;
  font-size: 12px;
  color: var(--product-text);
}
.quick-grid button:hover {
  border-color: #aab7ec;
  color: var(--product-primary);
}
.quick-grid .el-icon {
  font-size: 16px;
  color: var(--product-muted);
}
.ai-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 20px;
  margin: 30px 0 16px;
}
.ai-heading h2 {
  font-size: 18px;
  letter-spacing: -0.3px;
  margin: 8px 0 0;
}
.capability-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.data-footnote {
  font-size: 11px;
  color: var(--product-muted);
  margin-top: 18px;
  line-height: 1.7;
}
.capability-intro,
.capability-flow {
  font-size: 14px;
  line-height: 2;
  color: var(--product-text);
}
@media (max-width: 1100px) {
  .capability-grid {
    grid-template-columns: 1fr;
  }
  .ai-heading {
    display: block;
  }
  .ai-heading > span {
    display: block;
    margin-top: 10px;
  }
  .activity-text strong {
    max-width: 220px;
  }
}
@media (max-width: 800px) {
  .kpi-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
