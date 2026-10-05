const knownStates = ['running', 'done', 'warning', 'error'];
function updateSteps(steps, event) {
  if (!event || typeof event.id !== 'string' || typeof event.label !== 'string' || !knownStates.includes(event.state)) return steps;
  const next = { id: event.id.slice(0, 64), label: event.label.slice(0, 80), state: event.state, detail: String(event.detail || '').slice(0, 160) };
  const index = steps.findIndex(step => step.id === next.id);
  if (index < 0) return [...steps, next];
  return steps.map((step,i) => i === index ? next : step);
}
function endSteps(steps, failed) {
  return steps.map(step => step.state === 'running' ? { ...step, state: failed ? 'error' : 'warning', detail: failed ? '本次执行未完成' : '未收到此步骤的完成记录' } : step);
}
function normalizeEvidence(data) {
  if (!data || !['overview','trend','recent'].includes(data.kind)) return null;
  if (data.kind === 'overview') return { kind: data.kind, metrics: (data.metrics || []).slice(0,4).map(m => ({ label: String(m.label || ''), value: m.value === null || m.value === undefined ? '—' : String(m.value) })) };
  if (data.kind === 'trend') {
    const semesters=(data.semesters || []).slice(0,8);const values=semesters.map(s=>s.weightedGpa).filter(v=>typeof v==='number');const scale=Math.max(1,...values);
    return { kind:data.kind, semesters:semesters.map(s=>({ semester:String(s.semester || ''), value:typeof s.weightedGpa==='number'?s.weightedGpa.toFixed(2):'—', width:typeof s.weightedGpa==='number'?Math.round(s.weightedGpa/scale*100):0, sample:String(s.gpaCourseCount || 0) })) };
  }
  return { kind:data.kind, semester:String(data.semester || ''), courses:(data.courses || []).slice(0,10).map(c=>({name:String(c.name || ''),score:c.score===null||c.score===undefined?'未知':String(c.score),reason:String(c.reason || '')})) };
}
module.exports={ updateSteps,endSteps,normalizeEvidence };
