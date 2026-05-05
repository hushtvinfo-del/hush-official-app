/**
 * Login page — no shell. Vertically centred card with brand
 * mark + email/password form.
 */
import { useState } from "react";
import { useNavigate, Navigate } from "react-router-dom";
import { useAuth } from "@/admin/AuthContext";

export default function LoginPage() {
  const { user, ready, login } = useAuth();
  const nav = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  // If we're already logged in, jump to dashboard. Wait for the
  // auth bootstrap to finish so we don't flicker.
  if (ready && user) return <Navigate to="/" replace />;

  async function onSubmit(e) {
    e.preventDefault();
    setBusy(true);
    setErr("");
    const res = await login(email.trim(), password);
    setBusy(false);
    if (!res.ok) setErr(res.error || "Login failed");
    else nav("/", { replace: true });
  }

  return (
    <div className="hush-bg-screen" style={{
      minHeight: "100vh",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      padding: "24px",
    }}>
      <form
        onSubmit={onSubmit}
        data-testid="login-form"
        style={{
          width: "100%",
          maxWidth: 380,
          background: "var(--hush-bg-1)",
          border: "1px solid var(--hush-stroke-2)",
          borderRadius: "var(--hush-radius)",
          padding: "32px 28px",
          boxShadow: "0 24px 48px rgba(0,0,0,0.5)",
        }}
      >
        <div style={{ textAlign: "center", marginBottom: 28 }}>
          <span className="hush-brand" style={{ fontSize: 32 }}>
            <span className="hush-brand-h">hush</span>
            <span className="hush-brand-tv">tv.</span>
          </span>
          <p style={{
            margin: "8px 0 0",
            fontSize: 12,
            color: "var(--hush-text-dim)",
            letterSpacing: "1.5px",
            textTransform: "uppercase",
            fontWeight: 700,
          }}>
            Admin Panel
          </p>
        </div>

        <div className="hush-field" style={{ marginBottom: 14 }}>
          <label className="hush-field-label" htmlFor="login-email">
            Email
          </label>
          <input
            id="login-email"
            type="email"
            className="hush-input"
            data-testid="login-email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            autoComplete="email"
            required
          />
        </div>
        <div className="hush-field" style={{ marginBottom: 18 }}>
          <label className="hush-field-label" htmlFor="login-password">
            Password
          </label>
          <input
            id="login-password"
            type="password"
            className="hush-input"
            data-testid="login-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
          />
        </div>

        {err && (
          <div
            className="hush-pill is-blocked"
            data-testid="login-error"
            style={{ marginBottom: 14, width: "100%", justifyContent: "center" }}
          >
            {err}
          </div>
        )}

        <button
          type="submit"
          className="hush-btn hush-btn-primary"
          data-testid="login-submit"
          disabled={busy}
          style={{ width: "100%" }}
        >
          {busy ? "Signing in…" : "Sign in"}
        </button>
      </form>
    </div>
  );
}
