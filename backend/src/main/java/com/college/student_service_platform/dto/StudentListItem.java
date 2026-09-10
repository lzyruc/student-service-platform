package com.college.student_service_platform.dto;

import java.time.LocalDateTime;

public class StudentListItem {

    private String studentNo;
    private String name;
    private String idCardNo;
    private String gender;
    private String ethnicity;
    private String politicalStatus;
    private String className;
    private String major;
    private String grade;
    private String educationLevel;
    private String contact;
    private String joinLeagueDate;
    private String leagueMemberNo;
    private String joinPartyDate;
    private String partyBranchName;
    private String roleCode;
    private Integer status;
    private Integer partyStageId;
    private String partyStage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public String getStudentNo() {
        return studentNo;
    }

    public void setStudentNo(String studentNo) {
        this.studentNo = studentNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIdCardNo() {
        return idCardNo;
    }

    public void setIdCardNo(String idCardNo) {
        this.idCardNo = idCardNo;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getEthnicity() {
        return ethnicity;
    }

    public void setEthnicity(String ethnicity) {
        this.ethnicity = ethnicity;
    }

    public String getPoliticalStatus() {
        return politicalStatus;
    }

    public void setPoliticalStatus(String politicalStatus) {
        this.politicalStatus = politicalStatus;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getEducationLevel() {
        return educationLevel;
    }

    public void setEducationLevel(String educationLevel) {
        this.educationLevel = educationLevel;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getJoinLeagueDate() {
        return joinLeagueDate;
    }

    public void setJoinLeagueDate(String joinLeagueDate) {
        this.joinLeagueDate = joinLeagueDate;
    }

    public String getLeagueMemberNo() {
        return leagueMemberNo;
    }

    public void setLeagueMemberNo(String leagueMemberNo) {
        this.leagueMemberNo = leagueMemberNo;
    }

    public String getJoinPartyDate() {
        return joinPartyDate;
    }

    public void setJoinPartyDate(String joinPartyDate) {
        this.joinPartyDate = joinPartyDate;
    }

    public String getPartyBranchName() {
        return partyBranchName;
    }

    public void setPartyBranchName(String partyBranchName) {
        this.partyBranchName = partyBranchName;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getPartyStageId() {
        return partyStageId;
    }

    public void setPartyStageId(Integer partyStageId) {
        this.partyStageId = partyStageId;
    }

    public String getPartyStage() {
        return partyStage;
    }

    public void setPartyStage(String partyStage) {
        this.partyStage = partyStage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
