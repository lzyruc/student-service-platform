import { Login } from "@/api/interface/index";
import { PORT1 } from "@/api/config/servicePort";
import authMenuList from "@/assets/json/authMenuList.json";
import authButtonList from "@/assets/json/authButtonList.json";
import http from "@/api";

/**
 * @name 登录模块
 */
// 用户登录
export const loginApi = (params: Login.ReqLoginForm) => {
  return http.post<Login.ResLogin>(PORT1 + `/login`, params, { loading: false });
};

// 获取菜单列表
export const getAuthMenuListApi = () => {
  return authMenuList;
  // return http.get<Menu.MenuOptions[]>(PORT1 + `/menu/list`, {}, { loading: false });
};

// 获取按钮权限
export const getAuthButtonListApi = () => {
  return authButtonList;
  // return http.get<Login.ResAuthButtons>(PORT1 + `/auth/buttons`, {}, { loading: false });
};

// 用户退出登录
export const logoutApi = () => {
  return http.post(PORT1 + `/logout`);
};

export interface AdminMeResponse {
  id: number;
  username: string;
  role_code: string;
  student_no: string | null;
  wechat_openid: string | null;
  status: number;
  created_at: string | null;
  updated_at: string | null;
}

export const getAdminMeApi = () => {
  return http.get<AdminMeResponse>(PORT1 + `/me`, {}, { loading: false });
};

export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

export const changeAdminPasswordApi = (params: ChangePasswordRequest) => {
  return http.post<void>(PORT1 + `/user/change_password`, params, { cancel: false });
};
