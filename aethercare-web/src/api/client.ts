// axios instance：附帶 access token、401 自動 refresh、refresh 失敗 → logout 並導 /login。
import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import { useAuthStore } from '../stores/auth';

// 空字串 = 同源；dev 時走 vite proxy 到 :8080，prod / tunnel 時用同 host。
const BASE_URL = '';

interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
  _isRefresh?: boolean;
}

export const httpClient: AxiosInstance = axios.create({
  baseURL: BASE_URL,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' },
});

// 共用 refresh 鎖：避免 N 個並發 401 觸發 N 次 refresh。
let refreshPromise: Promise<string | null> | null = null;

async function performRefresh(): Promise<string | null> {
  const auth = useAuthStore();
  const rt = auth.refreshToken;
  if (!rt) return null;
  try {
    const resp = await axios.post(
      `${BASE_URL}/api/v1/auth/refresh`,
      { refreshToken: rt },
      { headers: { 'Content-Type': 'application/json' } },
    );
    auth.applyLoginPayload(resp.data);
    return resp.data.accessToken as string;
  } catch (err) {
    auth.clear();
    return null;
  }
}

httpClient.interceptors.request.use((config) => {
  const auth = useAuthStore();
  if (auth.accessToken && config.headers) {
    config.headers.Authorization = `Bearer ${auth.accessToken}`;
  }
  return config;
});

httpClient.interceptors.response.use(
  (resp) => resp,
  async (error: AxiosError) => {
    const original = error.config as RetryConfig | undefined;
    const status = error.response?.status;

    // refresh 自己 401 → 直接放棄（已在 performRefresh 清掉 store）
    if (original?._isRefresh) {
      return Promise.reject(error);
    }

    // login 路徑 401 → 不嘗試 refresh，讓 LoginView 自己顯示錯誤
    if (original?.url?.includes('/api/v1/auth/login')) {
      return Promise.reject(error);
    }

    if (status === 401 && original && !original._retry) {
      original._retry = true;
      if (!refreshPromise) {
        refreshPromise = performRefresh().finally(() => {
          refreshPromise = null;
        });
      }
      const newToken = await refreshPromise;
      if (newToken && original.headers) {
        original.headers.Authorization = `Bearer ${newToken}`;
        return httpClient.request(original);
      }
      // refresh 失敗 → 導 /login（用 location 避免依賴 router 注入）
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  },
);

export async function getJson<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
  const resp = await httpClient.get<T>(url, config);
  return resp.data;
}

export async function postJson<T, B = unknown>(
  url: string,
  body?: B,
  config?: AxiosRequestConfig,
): Promise<T> {
  const resp = await httpClient.post<T>(url, body, config);
  return resp.data;
}
