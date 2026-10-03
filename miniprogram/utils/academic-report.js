const normalizeCourseName = value => String(value || '').normalize('NFKC').replace(/\s+/g, '').toLowerCase();

const normalizeAcademicReport = (report, courseCount) => {
  const warningLevel = String(report.warning_level || report.riskLevel || '未知');
  const normalizedLevel = warningLevel.toLowerCase();
  let riskLevel = 'low';
  if (warningLevel.includes('严重') || normalizedLevel === 'high') riskLevel = 'high';
  else if (warningLevel.includes('一般') || normalizedLevel === 'medium') riskLevel = 'medium';
  const missing = report.missing_core_courses || [];
  const failed = report.failed_courses || [];
  const issues = new Set([
    ...missing.map(normalizeCourseName),
    ...failed.map(course => normalizeCourseName(typeof course === 'string' ? course : course.name))
  ].filter(Boolean));
  return {
    warningLevel, riskLevel, issueCount: issues.size,
    courseCount: courseCount || 0,
    earnedCredits: report.total_earned_credits || 0,
    officialGpa: report.official_gpa || 0,
    coreCourseCount: (report.core_courses || []).length,
    pendingCourseCount: (report.pending_core_courses || []).length,
    suggestions: report.course_suggestions || report.suggestions || [],
    notes: report.analysis_notes || []
  };
};

module.exports = { normalizeAcademicReport };
