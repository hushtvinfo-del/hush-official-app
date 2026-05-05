/**
 * Auth context — single source of truth for the logged-in admin
 * user. Bootstraps once on mount via /api/auth/me; from then on
 * components read `user`, `ready` and call `login`/`logout`.
 *
 * The selected reseller (for super-admin) is also stored here so
 * every page that filters by reseller picks it up automatically.
 */
import {
  createContext, useCallback, useContext, useEffect, useMemo,
  useState,
} from "react";
import { api, formatApiError } from "@/admin/api";

const AuthCtx = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);
  // Active reseller_id for filtering. Defaults to the user's own
  // reseller. Super-admins can switch via the top-bar dropdown.
  const [resellerId, setResellerId] = useState(null);

  useEffect(() => {
    let cancelled = false;
    api
      .get("/auth/me")
      .then(({ data }) => {
        if (!cancelled) {
          setUser(data);
          setResellerId(data.reseller_id);
        }
      })
      .catch(() => { /* not logged in */ })
      .finally(() => { if (!cancelled) setReady(true); });
    return () => { cancelled = true; };
  }, []);

  const login = useCallback(async (email, password) => {
    try {
      const { data } = await api.post("/auth/login", { email, password });
      setUser(data);
      setResellerId(data.reseller_id);
      return { ok: true };
    } catch (err) {
      return { ok: false, error: formatApiError(err) };
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await api.post("/auth/logout");
    } catch (_) { /* ignore */ }
    setUser(null);
    setResellerId(null);
  }, []);

  const value = useMemo(
    () => ({ user, ready, login, logout, resellerId, setResellerId }),
    [user, ready, login, logout, resellerId],
  );
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthCtx);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
