import { defineStore } from 'pinia';
import * as authApi from '../api/auth';
import type { LoginResponse } from '../api/types';

const STORAGE_KEY = 'aethercare.auth.v1';

interface PersistShape {
  accessToken: string | null;
  accessExpiresAt: number | null;
  refreshToken: string | null;
  userId: number | null;
  username: string | null;
  roles: string[];
}

interface AuthState extends PersistShape {
  initialized: boolean;
}

function emptyState(): AuthState {
  return {
    accessToken: null,
    accessExpiresAt: null,
    refreshToken: null,
    userId: null,
    username: null,
    roles: [],
    initialized: false,
  };
}

function loadFromStorage(): PersistShape | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    return JSON.parse(raw) as PersistShape;
  } catch {
    return null;
  }
}

function saveToStorage(state: PersistShape): void {
  if (typeof window === 'undefined') return;
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function clearStorage(): void {
  if (typeof window === 'undefined') return;
  window.localStorage.removeItem(STORAGE_KEY);
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => emptyState(),
  getters: {
    isAuthenticated(state): boolean {
      return !!state.accessToken && !!state.refreshToken;
    },
    displayName(state): string {
      return state.username ?? '';
    },
  },
  actions: {
    initFromStorage() {
      if (this.initialized) return;
      const persisted = loadFromStorage();
      if (persisted) {
        this.accessToken = persisted.accessToken;
        this.accessExpiresAt = persisted.accessExpiresAt;
        this.refreshToken = persisted.refreshToken;
        this.userId = persisted.userId;
        this.username = persisted.username;
        this.roles = persisted.roles ?? [];
      }
      this.initialized = true;
    },

    applyLoginPayload(payload: LoginResponse) {
      this.accessToken = payload.accessToken;
      this.refreshToken = payload.refreshToken;
      this.accessExpiresAt = Date.now() + payload.accessExpiresIn * 1000;
      this.userId = payload.userId;
      this.username = payload.username;
      this.roles = Array.isArray(payload.roles) ? [...payload.roles] : [];
      this.persist();
    },

    persist() {
      saveToStorage({
        accessToken: this.accessToken,
        accessExpiresAt: this.accessExpiresAt,
        refreshToken: this.refreshToken,
        userId: this.userId,
        username: this.username,
        roles: this.roles,
      });
    },

    clear() {
      this.accessToken = null;
      this.accessExpiresAt = null;
      this.refreshToken = null;
      this.userId = null;
      this.username = null;
      this.roles = [];
      clearStorage();
    },

    async login(username: string, password: string) {
      const payload = await authApi.login(username, password);
      this.applyLoginPayload(payload);
    },

    async logout() {
      const rt = this.refreshToken;
      this.clear();
      if (rt) {
        try {
          await authApi.logout(rt);
        } catch {
          // 後端撤銷失敗也不阻擋登出 UI
        }
      }
    },
  },
});
