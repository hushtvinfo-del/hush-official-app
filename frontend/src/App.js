/**
 * HushTV Admin Panel — root.
 *
 * One-page React 19 app served from /app/frontend. The backend is
 * shared with the rest of the suite at REACT_APP_BACKEND_URL.
 *
 * Routes:
 *   /login      — no auth required
 *   /           — Dashboard (auth required, default home)
 *   /devices    — Connected device list + block/unblock
 *   /broadcasts — Compose + history
 *   /branding   — Per-reseller branding config
 *   /config     — Xtream URL, feature toggles, maintenance mode
 *   /resellers  — (super-admin only) list + create
 *   /audit      — Audit log
 *
 * Layout
 *   Top app bar (sticky) + collapsible left rail. Mobile (<768px)
 *   collapses the rail to a hamburger sheet. Designed to be
 *   navigable on a TV remote (every interactive element is
 *   keyboard-focusable; arrow keys + Enter work).
 */
import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { Toaster } from "sonner";
import { AuthProvider, useAuth } from "@/admin/AuthContext";
import LoginPage from "@/admin/pages/LoginPage";
import AdminShell from "@/admin/AdminShell";
import DashboardPage from "@/admin/pages/DashboardPage";
import DevicesPage from "@/admin/pages/DevicesPage";
import BroadcastsPage from "@/admin/pages/BroadcastsPage";
import BrandingPage from "@/admin/pages/BrandingPage";
import ConfigPage from "@/admin/pages/ConfigPage";
import ResellersPage from "@/admin/pages/ResellersPage";
import AuditPage from "@/admin/pages/AuditPage";
import DiagnosticsPage from "@/admin/pages/DiagnosticsPage";
import DvrClusterPage from "@/admin/pages/DvrClusterPage";
import "@/App.css";

function Protected({ children }) {
  const { user, ready } = useAuth();
  if (!ready) {
    // Render nothing during the auth-bootstrap roundtrip — it's
    // fast enough that a flash of the login screen would be
    // worse UX than a blank moment.
    return (
      <div data-testid="auth-loading" className="hush-bg-screen min-h-screen" />
    );
  }
  if (!user) return <Navigate to="/login" replace />;
  return children;
}

function SuperOnly({ children }) {
  const { user } = useAuth();
  if (user?.role !== "super_admin")
    return <Navigate to="/" replace />;
  return children;
}

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Toaster
          position="top-right"
          theme="dark"
          toastOptions={{ className: "hush-toast" }}
        />
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/"
            element={
              <Protected>
                <AdminShell />
              </Protected>
            }
          >
            <Route index element={<DashboardPage />} />
            <Route path="devices" element={<DevicesPage />} />
            <Route path="broadcasts" element={<BroadcastsPage />} />
            <Route path="diagnostics" element={<DiagnosticsPage />} />
            <Route path="branding" element={<BrandingPage />} />
            <Route path="config" element={<ConfigPage />} />
            <Route
              path="resellers"
              element={
                <SuperOnly>
                  <ResellersPage />
                </SuperOnly>
              }
            />
            <Route
              path="dvr-cluster"
              element={
                <SuperOnly>
                  <DvrClusterPage />
                </SuperOnly>
              }
            />
            <Route path="audit" element={<AuditPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
