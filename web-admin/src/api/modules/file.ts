import http from "@/api";

export namespace BackendFile {
  export type BusinessType = "policy" | "template" | "notice" | "certificate" | "transcript" | "other";

  export interface FileUploadResponse {
    id: number;
    originalName: string;
    storedName: string;
    filePath: string;
    fileType: string;
    fileSize: number;
    businessType: string;
  }
}

export const uploadFile = (params: FormData) => {
  return http.post<BackendFile.FileUploadResponse>("/file/upload", params, { cancel: false });
};

export const getDownloadUrl = (fileId: number | string) => {
  return `/api/file/download/${fileId}`;
};
