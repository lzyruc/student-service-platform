import http from "@/api";

export namespace BackendCertificate {
  export interface ApplySubmitRequest {
    studentNo: string;
    certificateType: string;
    extraData?: string;
  }

  export interface ApplyItem {
    id: number;
    studentNo: string;
    certificateType: string;
    applyStatus: string;
    extraData?: string | null;
    fileId?: number | null;
    createdAt?: string;
    updatedAt?: string;
    lastApproverName?: string | null;
    lastApprovalStatus?: string | null;
    lastOpinion?: string | null;
    lastHandledAt?: string | null;
  }

  export interface ApprovalTaskItem {
    id: number;
    applyId: number;
    nodeOrder: number;
    approverId?: number | null;
    approverName?: string | null;
    approvalStatus: string;
    opinion?: string | null;
    handledAt?: string | null;
    createdAt?: string | null;
    updatedAt?: string | null;
  }

  export interface ApplyDetail {
    apply: ApplyItem;
    tasks: ApprovalTaskItem[];
  }

  export interface DecisionRequest {
    decision: "approved" | "rejected" | string;
    opinion: string;
  }
}

export const submitCertificateApply = (params: BackendCertificate.ApplySubmitRequest) => {
  return http.post<number>("/certificate/apply/submit", params, { cancel: false });
};

export const listCertificateApplies = (params?: { status?: string; keyword?: string }) => {
  return http.get<BackendCertificate.ApplyItem[]>("/certificate/apply/list", params, { cancel: false, loading: false });
};

export const getCertificateApplyDetail = (id: number | string) => {
  return http.get<BackendCertificate.ApplyDetail>(
    `/certificate/apply/${encodeURIComponent(String(id))}`,
    {},
    { cancel: false, loading: false }
  );
};

export const decideCertificateApply = (id: number | string, params: BackendCertificate.DecisionRequest) => {
  return http.post<void>(`/certificate/apply/${encodeURIComponent(String(id))}/decision`, params, { cancel: false });
};

export const deleteCertificateApply = (id: number | string) => {
  return http.delete<void>(`/certificate/apply/${encodeURIComponent(String(id))}`, {}, { cancel: false });
};
