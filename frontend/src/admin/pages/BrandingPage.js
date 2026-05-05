/**
 * Branding page — per-reseller logo, splash text, accent color,
 * app name. Saved with PATCH /api/admin/config.
 */
import { useEffect, useState } from "react";
import { Save } from "lucide-react";
import { toast } from "sonner";
import { api, formatApiError } from "@/admin/api";
import { useAuth } from "@/admin/AuthContext";

export default function BrandingPage() {
  const { resellerId } = useAuth();
  const [cfg, setCfg] = useState(null);
  const [busy, setBusy] = useState(false);
  const [draft, setDraft] = useState({});

  useEffect(() => {
    if (!resellerId) return;
    api.get("/admin/config", { params: { reseller_id: resellerId } })
      .then(({ data }) => {
        setCfg(data);
        setDraft({ ...(data.branding || {}) });
      })
      .catch((e) => toast.error(formatApiError(e)));
  }, [resellerId]);

  async function save() {
    setBusy(true);
    try {
      await api.patch(
        "/admin/config",
        { branding: draft },
        { params: { reseller_id: resellerId } },
      );
      toast.success("Branding saved");
    } catch (e) { toast.error(formatApiError(e)); }
    finally { setBusy(false); }
  }

  if (!cfg) return <div className="hush-empty">Loading…</div>;

  const fields = [
    { key: "app_name", label: "App display name", placeholder: "HushTV" },
    { key: "splash_text", label: "Splash text", placeholder: "HushTV" },
    { key: "logo_url", label: "Logo URL (PNG, transparent)", placeholder: "https://…/logo.png" },
    { key: "accent_color", label: "Accent color (hex)", placeholder: "#06B6D4" },
  ];

  return (
    <div data-testid="branding-page">
      <div className="hush-page-title">
        <div>
          <h1>Branding</h1>
          <p>Customise how this reseller's app looks at boot and in the rail.</p>
        </div>
        <button
          className="hush-btn hush-btn-primary"
          data-testid="branding-save"
          onClick={save}
          disabled={busy}
        >
          <Save size={14} /> {busy ? "Saving…" : "Save"}
        </button>
      </div>

      <div className="hush-card">
        <div className="hush-form-row">
          {fields.map((f) => (
            <div className="hush-field" key={f.key}>
              <label className="hush-field-label">{f.label}</label>
              <input
                className="hush-input"
                data-testid={`branding-${f.key}`}
                value={draft[f.key] || ""}
                placeholder={f.placeholder}
                onChange={(e) => setDraft({ ...draft, [f.key]: e.target.value })}
              />
            </div>
          ))}
        </div>

        {/* Live preview chip */}
        <div style={{ marginTop: 22, padding: "16px 18px",
                      background: "var(--hush-bg-0)",
                      border: "1px dashed var(--hush-stroke-2)",
                      borderRadius: "var(--hush-radius)" }}>
          <div className="hush-stat-label" style={{ marginBottom: 8 }}>Live preview</div>
          <div style={{
            display: "flex", alignItems: "center", gap: 12,
            padding: "12px 14px",
            background: "linear-gradient(180deg, #05080F 0%, #0B1424 60%, #05080F 100%)",
            borderRadius: 12,
            border: `1px solid ${draft.accent_color || "var(--hush-cyan)"}`,
          }}>
            {draft.logo_url ? (
              <img
                src={draft.logo_url}
                alt=""
                style={{ height: 32 }}
                onError={(e) => { e.target.style.display = "none"; }}
              />
            ) : null}
            <span style={{ fontSize: 22, fontWeight: 900, letterSpacing: -0.5 }}>
              <span style={{ color: "#fff" }}>hush</span>
              <span style={{ color: draft.accent_color || "var(--hush-cyan)" }}>tv.</span>
            </span>
            {draft.app_name && (
              <span style={{
                fontSize: 11, fontWeight: 700,
                letterSpacing: 1.5, textTransform: "uppercase",
                color: "var(--hush-text-dim)",
                marginLeft: 8,
              }}>
                {draft.app_name}
              </span>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
