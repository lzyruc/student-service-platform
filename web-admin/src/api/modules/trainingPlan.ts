import http from "@/api";

export namespace BackendTrainingPlan {
  export interface SaveRequest {
    id?: number;
    major: string;
    grade: string;
    version: string;
    remark?: string;
    jsonContent: string;
    courseCount?: number;
    totalCredits?: number;
  }

  export interface PlanItem {
    id: number;
    major: string;
    grade: string;
    version: string;
    remark?: string;
    jsonContent: string;
    courseCount?: number;
    totalCredits?: number;
    createdAt?: string;
    updatedAt?: string;
  }
}

export const saveTrainingPlan = (params: BackendTrainingPlan.SaveRequest) => {
  return http.post<number>("/training-plan/save", params, { cancel: false });
};

export const listTrainingPlans = () => {
  return http.get<BackendTrainingPlan.PlanItem[]>("/training-plan/list", {}, { cancel: false, loading: false });
};

export const deleteTrainingPlan = (id: number | string) => {
  return http.delete<void>(`/training-plan/${encodeURIComponent(String(id))}`, {}, { cancel: false });
};
