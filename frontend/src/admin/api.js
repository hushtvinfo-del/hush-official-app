/**
 * Centralised axios instance + endpoint helpers for the admin
 * panel. We use httpOnly cookies (`withCredentials: true`) for
 * auth so there's no token stored in JS that XSS can grab.
 */
import axios from "axios";

const BACKEND_URL = process.env.REACT_APP_BACKEND_URL;
export const API_BASE = `${BACKEND_URL}/api`;

export const api = axios.create({
  baseURL: API_BASE,
  withCredentials: true,
  timeout: 15000,
});

/** Convert any FastAPI error payload into a user-readable string. */
export function formatApiError(err) {
  const detail = err?.response?.data?.detail;
  if (detail == null) return err?.message || "Something went wrong";
  if (typeof detail === "string") return detail;
  if (Array.isArray(detail))
    return detail
      .map((d) =>
        d && typeof d.msg === "string" ? d.msg : JSON.stringify(d),
      )
      .join(" · ");
  if (detail && typeof detail.msg === "string") return detail.msg;
  return String(detail);
}
