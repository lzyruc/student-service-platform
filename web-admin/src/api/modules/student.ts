import http from "@/api";

export namespace BackendStudent {
  export interface StudentImportItem {
    studentNo: string;
    name: string;
    idCardNo?: string;
    gender?: string;
    ethnicity?: string;
    className: string;
    major: string;
    grade: string;
    educationLevel?: string;
    contact?: string;
    password?: string;
    roleCode?: string;
    status?: number;
    wechatOpenid?: string;
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
    className: string;
    major: string;
    grade: string;
    educationLevel?: string;
    contact: string;
    roleCode: string;
    status: number;
    createdAt?: string;
    updatedAt?: string;
  }
}

export const importStudents = (students: BackendStudent.StudentImportItem[]) => {
  return http.post<BackendStudent.StudentImportResult>("/student/import", { students }, { cancel: false });
};

export const listStudents = (params?: { keyword?: string }, options: { silent?: boolean } = {}) => {
  return http.get<BackendStudent.StudentListItem[]>("/student/list", params, { cancel: false, loading: false, ...options });
};

export const deleteStudent = (studentNo: string) => {
  return http.delete<void>(`/student/${encodeURIComponent(studentNo)}`, {}, { cancel: false });
};
