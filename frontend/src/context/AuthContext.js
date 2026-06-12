import React, { createContext, useContext, useState, useCallback } from 'react';
import * as authService from '../services/authService';

const AuthContext = createContext();

const STORAGE_KEYS = {
  ACCESS_TOKEN: 'nf_access_token',
  REFRESH_TOKEN: 'nf_refresh_token',
  USER: 'nf_user',
};

function loadFromStorage() {
  try {
    const accessToken = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
    const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN);
    const user = JSON.parse(localStorage.getItem(STORAGE_KEYS.USER) || 'null');
    if (accessToken && user) return { accessToken, refreshToken, user };
  } catch {}
  return { accessToken: null, refreshToken: null, user: null };
}

function saveToStorage(accessToken, refreshToken, user) {
  localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken);
  localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken);
  localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user));
}

function clearStorage() {
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
  localStorage.removeItem(STORAGE_KEYS.USER);
}

export function AuthProvider({ children }) {
  const [auth, setAuth] = useState(() => loadFromStorage());

  const login = useCallback(async (identifier, password) => {
    const data = await authService.login(identifier, password);
    const user = { username: identifier, userType: data.userType };
    saveToStorage(data.accessToken, data.refreshToken, user);
    setAuth({ accessToken: data.accessToken, refreshToken: data.refreshToken, user });
  }, []);

  const signup = useCallback(async (fields) => {
    await authService.signup(fields);
    // signup does not auto-login — caller should redirect to login
  }, []);

  const logout = useCallback(async () => {
    if (auth.refreshToken) {
      await authService.logout(auth.refreshToken);
    }
    clearStorage();
    setAuth({ accessToken: null, refreshToken: null, user: null });
  }, [auth.refreshToken]);

  // Called by apiClient when a 401 refresh succeeds — updates stored tokens
  const updateTokens = useCallback((accessToken, refreshToken) => {
    const user = auth.user;
    saveToStorage(accessToken, refreshToken, user);
    setAuth(prev => ({ ...prev, accessToken, refreshToken }));
  }, [auth.user]);

  const value = {
    user: auth.user,
    accessToken: auth.accessToken,
    refreshToken: auth.refreshToken,
    isLoggedIn: !!auth.accessToken,
    login,
    signup,
    logout,
    updateTokens,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
