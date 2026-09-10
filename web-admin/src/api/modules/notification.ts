import http from "@/api";

export namespace BackendNotification {
  export interface SaveRequest {
    id?: number;
    title: string;
    content?: string;
    tags?: string;
    is_urgent?: boolean;
    file_id?: number | null;
    publisher_id?: number | null;
  }

  export interface NotificationItem {
    id: number;
    title: string;
    content?: string;
    tags?: string;
    is_urgent?: boolean;
    file_id?: number | null;
    publisher_id?: number | null;
    created_at?: string;
    updated_at?: string;
  }

  export interface ReceiptItem {
    id: number;
    notification_id: number;
    student_no: string;
    is_confirmed: boolean;
    confirmed_at?: string;
    created_at?: string;
  }
}

export const saveNotification = (params: BackendNotification.SaveRequest) => {
  return http.post<number>("/notification/save", params, { cancel: false });
};

export const listNotifications = (params?: { keyword?: string }) => {
  return http.get<BackendNotification.NotificationItem[]>("/notification/list", params, { cancel: false, loading: false });
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
