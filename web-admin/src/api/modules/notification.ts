import http from "@/api";

export namespace BackendNotification {
  export interface SaveRequest {
    id?: number;
    title: string;
    content?: string;
    tags?: string;
    is_urgent?: boolean;
    file_id?: number | null;
  }

  export interface NotificationItem {
    id: number;
    title: string;
    content?: string;
    tags?: string;
    is_urgent?: boolean;
    file_id?: number | null;
    original_name?: string;
    file_type?: string;
    publisher_id?: number | null;
    confirmed_count?: number;
    total_count?: number;
    created_at?: string;
    updated_at?: string;
  }

  export interface ReceiptItem {
    id?: number | null;
    notification_id: number;
    student_no: string;
    student_name?: string;
    is_confirmed: boolean;
    confirmed_at?: string;
    created_at?: string;
  }
}

export const saveNotification = (params: BackendNotification.SaveRequest) => {
  return http.post<number>("/notification/save", params, { cancel: false });
};

export const listNotifications = (params?: { keyword?: string }, options: { silent?: boolean } = {}) => {
  return http.get<BackendNotification.NotificationItem[]>("/notification/list", params, {
    cancel: false,
    loading: false,
    ...options
  });
};

export const deleteNotification = (id: number | string) => {
  return http.delete<void>(`/notification/${encodeURIComponent(String(id))}`, {}, { cancel: false });
};

export const listNotificationReceipts = (id: number | string) => {
  return http.get<BackendNotification.ReceiptItem[]>(
    `/notification/${encodeURIComponent(String(id))}/receipts`,
    {},
    { cancel: false, loading: false }
  );
};
