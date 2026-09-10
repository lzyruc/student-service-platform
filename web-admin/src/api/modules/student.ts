import http from "@/api";

export namespace BackendStudent {
  export interface StudentImportItem {
    studentNo: string;
    name: string;
    idCardNo?: string;
    gender?: string;
    ethnicity?: string;
    politicalStatus?: string;
    className: string;
    major: string;
    grade: string;
    educationLevel?: string;
    contact?: string;
    joinLeagueDate?: string;
    leagueMemberNo?: string;
    joinPartyDate?: string;
    partyBranchName?: string;
    password?: string;
    roleCode?: string;
    status?: number;
    wechatOpenid?: string;
    partyStageId?: number;
  }

  export interface StudentImportResult {
    inserted: number;
    updated: number;
  }

  export interface StudentListItem {
    studentNo: string;
    name: string;
    idCardNo?: string;
    gender: string;
    ethnicity: string;
    politicalStatus: string;
    className: string;
    major: string;
    grade: string;
    educationLevel?: string;
    contact: string;
    joinLeagueDate?: string;
    leagueMemberNo?: string;
    joinPartyDate?: string;
    partyBranchName?: string;
    roleCode: string;
    status: number;
    partyStageId?: number;
    partyStage?: string;
    createdAt?: string;
    updatedAt?: string;
  }
}

export const importStudents = (students: BackendStudent.StudentImportItem[]) => {
  return http.post<BackendStudent.StudentImportResult>("/student/import", { students }, { cancel: false });
};

export const listStudents = (params?: { keyword?: string }) => {
  return http.get<BackendStudent.StudentListItem[]>("/student/list", params, { cancel: false, loading: false });
};

export const deleteStudent = (studentNo: string) => {
  return http.delete<void>(`/student/${encodeURIComponent(studentNo)}`, {}, { cancel: false });
};
