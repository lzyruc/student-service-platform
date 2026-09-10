package com.college.student_service_platform.service;

import com.college.student_service_platform.dto.StudentImportItem;
import com.college.student_service_platform.dto.StudentImportResult;
import com.college.student_service_platform.dto.StudentListItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class StudentService {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;
    private final String defaultStudentPassword;
    private final AtomicLong idSeq = new AtomicLong(System.currentTimeMillis());

    public StudentService(JdbcTemplate jdbcTemplate, PasswordService passwordService,
                          @Value("${security.default-student-password:}") String defaultStudentPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
        this.defaultStudentPassword = normalize(defaultStudentPassword);
    }

    @Transactional
    public StudentImportResult importStudents(List<StudentImportItem> students) {
        if (students == null || students.isEmpty()) {
            return new StudentImportResult(0, 0);
        }

        int inserted = 0;
        int updated = 0;

        for (StudentImportItem s : students) {
            String studentNo = normalize(s.getStudentNo());
            String name = normalize(s.getName());
            String roleCode = normalize(s.getRoleCode());
            if (roleCode.equals("admin")) {
                String username = studentNo.isEmpty() ? name : studentNo;
                if (username.isEmpty()) {
                    continue;
                }

                int status = s.getStatus() == null ? 1 : (s.getStatus() == 0 ? 0 : 1);
                String password = encodeIfPresent(s.getPassword());
                String wechatOpenid = normalize(s.getWechatOpenid());
                Timestamp now = Timestamp.valueOf(LocalDateTime.now());

                int adminUpdated = jdbcTemplate.update(
                        """
                                UPDATE t_user
                                SET password = COALESCE(?, password), status = ?, wechat_openid = ?, updated_at = ?
                                WHERE username = ? AND role_code = 'admin'
                                """,
                        password,
                        status,
                        wechatOpenid.isEmpty() ? null : wechatOpenid,
                        now,
                        username
                );

                if (adminUpdated == 0) {
                    Long userId = idSeq.incrementAndGet();
                    jdbcTemplate.update(
                            """
                                    INSERT INTO t_user
                                    (id, username, password, role_code, student_no, wechat_openid, status, created_at, updated_at)
                                    VALUES (?, ?, ?, 'admin', NULL, ?, ?, ?, ?)
                                    """,
                            userId,
                            username,
                            password == null ? encodeRequiredPassword(s.getPassword()) : password,
                            wechatOpenid.isEmpty() ? null : wechatOpenid,
                            status,
                            now,
                            now
                    );
                    inserted += 1;
                } else {
                    updated += 1;
                }
                continue;
            }

            roleCode = "student";

            String className = normalize(s.getClassName());
            String major = normalize(s.getMajor());
            String grade = normalize(s.getGrade());
            if (studentNo.isEmpty() || name.isEmpty() || className.isEmpty() || major.isEmpty() || grade.isEmpty()) {
                continue;
            }

            String gender = normalize(s.getGender());
            if (gender.isEmpty()) gender = "未知";
            String ethnicity = normalize(s.getEthnicity());
            String politicalStatus = normalize(s.getPoliticalStatus());
            if (politicalStatus.isEmpty()) politicalStatus = "未知";
            String contact = normalize(s.getContact());
            String idCardNo = normalize(s.getIdCardNo());
            String educationLevel = normalize(s.getEducationLevel());
            if (educationLevel.isEmpty()) educationLevel = "本科";
            String joinLeagueDate = normalize(s.getJoinLeagueDate());
            String leagueMemberNo = normalize(s.getLeagueMemberNo());
            String joinPartyDate = normalize(s.getJoinPartyDate());
            String partyBranchName = normalize(s.getPartyBranchName());

            int status = s.getStatus() == null ? 1 : (s.getStatus() == 0 ? 0 : 1);
            String password = encodeIfPresent(s.getPassword());
            String wechatOpenid = normalize(s.getWechatOpenid());

            Timestamp now = Timestamp.valueOf(LocalDateTime.now());

            int partyStageId = 0;
            if (s.getPartyStageId() != null) {
                partyStageId = s.getPartyStageId();
            }
            if (partyStageId < 0 || partyStageId > 5) {
                throw new IllegalArgumentException("党团流程阶段不合法");
            }

            int studentUpdated = jdbcTemplate.update(
                    """
                            UPDATE t_student
                            SET name = ?, id_card_no = ?, gender = ?, ethnicity = ?, political_status = ?, party_stage_id = ?, class_name = ?, major = ?, grade = ?, education_level = ?, contact = ?, status = ?, updated_at = ?
                            WHERE student_no = ?
                            """,
                    name,
                    idCardNo.isEmpty() ? null : idCardNo,
                    gender,
                    ethnicity,
                    politicalStatus,
                    partyStageId,
                    className,
                    major,
                    grade,
                    educationLevel,
                    contact,
                    status,
                    now,
                    studentNo
            );

            if (studentUpdated == 0) {
                Long studentId = idSeq.incrementAndGet();
                jdbcTemplate.update(
                        """
                                INSERT INTO t_student
                                (id, student_no, name, id_card_no, gender, ethnicity, political_status, party_stage_id, class_name, major, grade, education_level, contact, status, created_at, updated_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        studentId,
                        studentNo,
                        name,
                        idCardNo.isEmpty() ? null : idCardNo,
                        gender,
                        ethnicity,
                        politicalStatus,
                        partyStageId,
                        className,
                        major,
                        grade,
                        educationLevel,
                        contact,
                        status,
                        now,
                        now
                );
                inserted += 1;
            } else {
                updated += 1;
            }

            upsertPoliticalInfo(studentNo, joinLeagueDate, leagueMemberNo, joinPartyDate, partyBranchName, now);

            int userUpdated = jdbcTemplate.update(
                    """
                            UPDATE t_user
                            SET username = ?, password = COALESCE(?, password), role_code = ?, wechat_openid = ?, status = ?, updated_at = ?
                            WHERE student_no = ?
                            """,
                    studentNo,
                    password,
                    roleCode,
                    wechatOpenid.isEmpty() ? null : wechatOpenid,
                    status,
                    now,
                    studentNo
            );

            if (userUpdated == 0) {
                Long userId = idSeq.incrementAndGet();
                jdbcTemplate.update(
                        """
                                INSERT INTO t_user
                                (id, username, password, role_code, student_no, wechat_openid, status, created_at, updated_at)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                                """,
                        userId,
                        studentNo,
                        password == null ? encodeRequiredPassword(s.getPassword()) : password,
                        roleCode,
                        studentNo,
                        wechatOpenid.isEmpty() ? null : wechatOpenid,
                        status,
                        now,
                        now
                );
            }

        }

        return new StudentImportResult(inserted, updated);
    }

    public List<StudentListItem> listStudents(String keyword) {
        String k = normalize(keyword).toLowerCase();
        boolean hasKeyword = !k.isEmpty();

        String sql = """
                SELECT
                  s.student_no,
                  s.name,
                  s.id_card_no,
                  s.gender,
                  s.ethnicity,
                  s.political_status,
                  COALESCE(s.party_stage_id, 0) AS party_stage_id,
                  COALESCE(ps.stage_name, '未申请') AS party_stage,
                  s.class_name,
                  s.major,
                  s.grade,
                  s.education_level,
                  s.contact,
                  pi.join_league_date,
                  pi.league_member_no,
                  pi.join_party_date,
                  pi.party_branch_name,
                  COALESCE(u.role_code, 'student') AS role_code,
                  COALESCE(u.status, s.status) AS status,
                  s.created_at,
                  s.updated_at
                FROM t_student s
                LEFT JOIN t_user u ON u.student_no = s.student_no
                LEFT JOIN t_process_stage ps ON ps.stage_id = s.party_stage_id
                LEFT JOIN t_student_political_info pi ON pi.student_no = s.student_no
                """;

        List<Object> args = new ArrayList<>();
        if (hasKeyword) {
            sql += """
                    WHERE LOWER(s.student_no) LIKE ?
                       OR LOWER(s.name) LIKE ?
                       OR LOWER(s.class_name) LIKE ?
                       OR LOWER(s.major) LIKE ?
                    """;
            String like = "%" + k + "%";
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }

        sql += " ORDER BY s.updated_at DESC";

        return jdbcTemplate.query(sql, args.toArray(), (rs, rowNum) -> {
            StudentListItem item = new StudentListItem();
            item.setStudentNo(rs.getString("student_no"));
            item.setName(rs.getString("name"));
            item.setIdCardNo(rs.getString("id_card_no"));
            item.setGender(rs.getString("gender"));
            item.setEthnicity(rs.getString("ethnicity"));
            item.setPoliticalStatus(rs.getString("political_status"));
            item.setClassName(rs.getString("class_name"));
            item.setMajor(rs.getString("major"));
            item.setGrade(rs.getString("grade"));
            item.setEducationLevel(rs.getString("education_level"));
            item.setContact(rs.getString("contact"));
            item.setJoinLeagueDate(toDateString(rs.getDate("join_league_date")));
            item.setLeagueMemberNo(rs.getString("league_member_no"));
            item.setJoinPartyDate(toDateString(rs.getDate("join_party_date")));
            item.setPartyBranchName(rs.getString("party_branch_name"));
            item.setRoleCode(rs.getString("role_code"));
            item.setStatus(rs.getInt("status"));
            item.setPartyStageId(rs.getInt("party_stage_id"));
            item.setPartyStage(rs.getString("party_stage"));
            Timestamp createdAt = rs.getTimestamp("created_at");
            Timestamp updatedAt = rs.getTimestamp("updated_at");
            if (createdAt != null) item.setCreatedAt(createdAt.toLocalDateTime());
            if (updatedAt != null) item.setUpdatedAt(updatedAt.toLocalDateTime());
            return item;
        });
    }

    @Transactional
    public void deleteByStudentNo(String studentNo) {
        String no = normalize(studentNo);
        if (no.isEmpty()) return;
        jdbcTemplate.update("DELETE FROM t_student_political_info WHERE student_no = ?", no);
        jdbcTemplate.update("DELETE FROM t_student WHERE student_no = ?", no);
        jdbcTemplate.update("DELETE FROM t_user WHERE student_no = ?", no);
    }

    private void upsertPoliticalInfo(
            String studentNo,
            String joinLeagueDate,
            String leagueMemberNo,
            String joinPartyDate,
            String partyBranchName,
            Timestamp now
    ) {
        LocalDate joinLeague = parseDate(joinLeagueDate);
        LocalDate joinParty = parseDate(joinPartyDate);

        int updated = jdbcTemplate.update(
                """
                        UPDATE t_student_political_info
                        SET join_league_date = ?, league_member_no = ?, join_party_date = ?, party_branch_name = ?, updated_at = ?
                        WHERE student_no = ?
                        """,
                joinLeague == null ? null : java.sql.Date.valueOf(joinLeague),
                leagueMemberNo.isEmpty() ? null : leagueMemberNo,
                joinParty == null ? null : java.sql.Date.valueOf(joinParty),
                partyBranchName.isEmpty() ? null : partyBranchName,
                now,
                studentNo
        );

        if (updated > 0) return;

        Long id = idSeq.incrementAndGet();
        jdbcTemplate.update(
                """
                        INSERT INTO t_student_political_info
                        (id, student_no, join_league_date, league_member_no, join_party_date, party_branch_name, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                studentNo,
                joinLeague == null ? null : java.sql.Date.valueOf(joinLeague),
                leagueMemberNo.isEmpty() ? null : leagueMemberNo,
                joinParty == null ? null : java.sql.Date.valueOf(joinParty),
                partyBranchName.isEmpty() ? null : partyBranchName,
                now,
                now
        );
    }

    private LocalDate parseDate(String v) {
        String s = normalize(v);
        if (s.isEmpty()) return null;
        try {
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String toDateString(java.sql.Date d) {
        if (d == null) return "";
        return d.toLocalDate().toString();
    }

    private String normalize(String v) {
        return v == null ? "" : v.trim();
    }

    private String encodeIfPresent(String password) {
        String raw = normalize(password);
        return raw.isEmpty() ? null : passwordService.encode(raw);
    }

    private String encodeRequiredPassword(String requestedPassword) {
        String raw = normalize(requestedPassword);
        if (raw.isEmpty()) raw = defaultStudentPassword;
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("新建账号必须提供密码，或配置 DEFAULT_STUDENT_PASSWORD");
        }
        return passwordService.encode(raw);
    }
}
