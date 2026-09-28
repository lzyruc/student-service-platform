import http from "@/api";

export namespace PolicyDocumentApi {
  export type Audience = "ALL" | "UNDERGRADUATE" | "POSTGRADUATE";
  export type DocStatus = "DRAFT" | "PUBLISHED" | "ARCHIVED";
  export type IngestStatus = "PENDING" | "PROCESSING" | "READY" | "FAILED";

  export interface SaveRequest {
    title: string;
    category: string;
    audience: Audience;
    version: string;
    effectiveDate?: string | null;
    expiryDate?: string | null;
    tags: string[];
    content?: string | null;
    keywords?: string | null;
    officialUrl?: string | null;
    remark?: string | null;
    fileId: number;
  }

  export interface Item extends SaveRequest {
    id: number;
    fileName: string;
    fileType?: string;
    fileSize?: number;
    createdBy?: number;
    creatorName?: string;
    docStatus: DocStatus;
    ingestStatus: IngestStatus;
    ingestError?: string;
    chunkCount: number;
    contentHash?: string;
    lastIngestedAt?: string;
    createdAt: string;
    updatedAt: string;
  }

  export interface PageResponse {
    records: Item[];
    total: number;
    page: number;
    pageSize: number;
  }

  export interface PageQuery {
    page: number;
    pageSize: number;
    keyword?: string;
    category?: string;
    audience?: Audience | "";
    docStatus?: DocStatus | "";
    ingestStatus?: IngestStatus | "";
  }
}

export const createPolicyDocument = (params: PolicyDocumentApi.SaveRequest) => {
  return http.post<PolicyDocumentApi.Item>("/admin/knowledge/documents", params, { cancel: false });
};

export const updatePolicyDocument = (id: number, params: PolicyDocumentApi.SaveRequest) => {
  return http.put<PolicyDocumentApi.Item>(`/admin/knowledge/documents/${id}`, params, { cancel: false });
};

export const listPolicyDocuments = (params: PolicyDocumentApi.PageQuery) => {
  return http.get<PolicyDocumentApi.PageResponse>("/admin/knowledge/documents", params, {
    cancel: false,
    loading: false
  });
};

export const publishPolicyDocument = (id: number) => {
  return http.post<PolicyDocumentApi.Item>(`/admin/knowledge/documents/${id}/publish`, {}, { cancel: false });
};

export const deletePolicyDocument = (id: number) => {
  return http.delete<void>(`/admin/knowledge/documents/${id}`, {}, { cancel: false });
};
