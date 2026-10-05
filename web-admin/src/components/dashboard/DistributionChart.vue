<template>
  <div
    v-if="values.length"
    ref="host"
    class="chart"
    role="img"
    :aria-label="title + '：' + values.map(i => i.name + ' ' + i.value).join('，')"
  ></div>
  <el-empty v-else :description="emptyText" :image-size="60" />
</template>
<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from "vue";
import * as echarts from "echarts";
const props = withDefaults(
  defineProps<{ title: string; values: { name: string; value: number }[]; kind?: "bar" | "donut"; emptyText?: string }>(),
  { kind: "bar", emptyText: "暂无可统计数据" }
);
const host = ref<HTMLElement>();
let chart: echarts.ECharts | undefined;
let observer: ResizeObserver | undefined;
const draw = async () => {
  await nextTick();
  if (!host.value) {
    chart?.dispose();
    chart = undefined;
    return;
  }
  if (!chart) {
    chart = echarts.init(host.value);
    observer?.disconnect();
    observer = new ResizeObserver(() => chart?.resize());
    observer.observe(host.value);
  }
  const base = {
    color: ["#425bd8", "#8d9ce8", "#b8c6f2", "#64ac96", "#e1ac6e"],
    tooltip: { trigger: props.kind === "bar" ? "axis" : "item" },
    textStyle: { fontFamily: "inherit", color: "#77839a" }
  };
  chart.setOption(
    props.kind === "donut"
      ? {
          ...base,
          legend: { bottom: 0, left: "center", icon: "circle", itemWidth: 8, itemHeight: 8 },
          series: [
            {
              type: "pie",
              radius: ["55%", "76%"],
              center: ["50%", "43%"],
              label: { show: false },
              itemStyle: { borderColor: "#fff", borderWidth: 4, borderRadius: 5 },
              data: props.values
            }
          ]
        }
      : {
          ...base,
          grid: { left: 40, right: 16, top: 20, bottom: 35 },
          xAxis: { type: "category", data: props.values.map(i => i.name), axisLine: { show: false }, axisTick: { show: false } },
          yAxis: { type: "value", minInterval: 1, splitLine: { lineStyle: { color: "#edf0f5", type: "dashed" } } },
          series: [
            { type: "bar", barMaxWidth: 32, itemStyle: { borderRadius: [5, 5, 0, 0] }, data: props.values.map(i => i.value) }
          ]
        },
    true
  );
};
onMounted(draw);
watch(() => props.values, draw, { deep: true });
onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>
<style scoped>
.chart {
  height: 250px;
  width: 100%;
}
</style>
