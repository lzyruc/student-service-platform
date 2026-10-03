const test = require('node:test');
const assert = require('node:assert/strict');
const { normalizeAcademicReport } = require('../utils/academic-report.js');

test('a failed core course contributes one issue even if older responses list it twice', () => {
  const report = normalizeAcademicReport({
    warning_level: '一般预警', missing_core_courses: ['高等数学II'],
    failed_courses: [{ name: '高等数学Ⅱ', score: '50' }]
  }, 45);
  assert.equal(report.issueCount, 1);
  assert.equal(report.riskLevel, 'medium');
  assert.equal(report.courseCount, 45);
});

test('future courses and uncertainty notes remain separate from academic issues', () => {
  const report = normalizeAcademicReport({
    warning_level: '正常', core_courses: ['思想道德与法治'],
    pending_core_courses: ['操作系统'], unscheduled_core_courses: ['计算机网络'],
    analysis_notes: ['尚未到计划学期'], total_earned_credits: 117, official_gpa: 3.65
  }, 45);
  assert.equal(report.issueCount, 0);
  assert.equal(report.pendingCourseCount, 1);
  assert.equal(report.coreCourseCount, 1);
  assert.equal(report.earnedCredits, 117);
  assert.deepEqual(report.notes, ['尚未到计划学期']);
});
