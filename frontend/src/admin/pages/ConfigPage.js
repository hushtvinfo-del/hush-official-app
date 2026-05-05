/**
 * Config page — Xtream URL, feature flags, force-min-version,
 * maintenance mode. Saved with PATCH /api/admin/config.
 */
import { useEffect, useState } from "react";
import { Save, AlertTriangle } from "lucide-react";
import { toast } from "sonner";
import { api, formatApiError } from "@/admin/api";
import { useAuth } from "@/admin/AuthContext";

const FEATURES = [
  { key: "hush_plus", label: "Hush+ (premium add-ons)" },
  { key: "requests",  label: "Episode / Movie Requests" },
  { key: "search",    label: "Master search (TMDB)" },
  { key: "epg",       label: "Live TV EPG" },
  { key: "pip",       label: "Picture-in-Picture" },
];

export default function ConfigPage() {
  const { resellerId } = useAuth();
  const [cfg, setCfg] = useState(null);
  const [busy, setBusy] = useState(false);
  const [xtream, setXtream] = useState("");
  const [minVer, setMinVer] = useState("");
  const [maintMode, setMaintMode] = useState(false);
  const [maintMsg, setMaintMsg] = useState("");
  const [flags, setFlags] = useState({});

  useEffect(() => {
    if (!resellerId) return;
    api.get("/admin/config", { params: { reseller_id: resellerId } })
      .then(({ data }) => {
        setCfg(data);
        setXtream(data.xtream_default || "");
        setMinVer(data.min_app_version || "");
        setMaintMode(!!data.maintenance_mode);
        setMaintMsg(data.maintenance_message || "");
        setFlags({ ...(data.feature_flags || {}) });
      })
      .catch((e) => toast.error(formatApiError(e)));
  }, [resellerId]);

  async function save() {
    setBusy(true);
    try {
      await api.patch(
        "/admin/config",
        {
          xtream_default: xtream.trim(),
          min_app_version: minVer.trim(),
          maintenance_mode: maintMode,
          maintenance_message: maintMsg,
          feature_flags: flags,
        },
        { params: { reseller_id: resellerId } },
      );
      toast.success("Config saved");
    } catch (e) { toast.error(formatApiError(e)); }
    finally { setBusy(false); }
  }

  if (!cfg) return <div className="hush-empty">Loading…</div>;

  return (
    <div data-testid="config-page">
      <div className="hush-page-title">
        <div>
          <h1>App Config</h1>
          <p>Default Xtream DNS, feature toggles, and maintenance mode.</p>
        </div>
        <button
          className="hush-btn hush-btn-primary"
          data-testid="config-save"
          onClick={save}
          disabled={busy}
        >
          <Save size={14} /> {busy ? "Saving…" : "Save"}
        </button>
      </div>

      <div className="hush-card">
        <h3 style={{ margin: "0 0 12px", fontSize: 14, fontWeight: 800,
                     textTransform: "uppercase", letterSpacing: 1.5,
                     color: "var(--hush-text-dim)" }}>
          Defaults
        </h3>
        <div className="hush-form-row">
          <div className="hush-field">
            <label className="hush-field-label">Default Xtream DNS</label>
            <input
              className="hush-input"
              data-testid="config-xtream"
              value={xtream}
              onChange={(e) => setXtream(e.target.value)}
              placeholder="iptv.example.com:25461"
            />
          </div>
          <div className="hush-field">
            <label className="hush-field-label">Force minimum app version</label>
            <input
              className="hush-input"
              data-testid="config-min-version"
              value={minVer}
              onChange={(e) => setMinVer(e.target.value)}
              placeholder="1.42.95"
            />
          </div>
        </div>
      </div>

      <div className="hush-card">
        <h3 style={{ margin: "0 0 12px", fontSize: 14, fontWeight: 800,
                     textTransform: "uppercase", letterSpacing: 1.5,
                     color: "var(--hush-text-dim)" }}>
          Feature flags
        </h3>
        <div style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))",
          gap: 12,
        }}>
          {FEATURES.map((f) => (
            <label key={f.key} className="hush-toggle"
                   data-testid={`flag-${f.key}`}
                   style={{
                     padding: "10px 12px",
                     border: "1px solid var(--hush-stroke)",
                     borderRadius: "var(--hush-radius-sm)",
                     justifyContent: "space-between",
                     width: "100%",
                   }}>
              <span style={{ fontSize: 13 }}>{f.label}</span>
              <span style={{ display: "inline-flex", alignItems: "center" }}>
                <input
                  type="checkbox"
                  checked={!!flags[f.key]}
                  onChange={(e) => setFlags({ ...flags, [f.key]: e.target.checked })}
                />
                <span className="hush-toggle-track">
                  <span className="hush-toggle-thumb" />
                </span>
              </span>
            </label>
          ))}
        </div>
      </div>

      <div className="hush-card">
        <h3 style={{ margin: "0 0 12px", fontSize: 14, fontWeight: 800,
                     textTransform: "uppercase", letterSpacing: 1.5,
                     color: "var(--hush-text-dim)" }}>
          Maintenance mode
        </h3>
        <label className="hush-toggle" data-testid="maintenance-toggle">
          <input
            type="checkbox"
            checked={maintMode}
            onChange={(e) => setMaintMode(e.target.checked)}
          />
          <span className="hush-toggle-track">
            <span className="hush-toggle-thumb" />
          </span>
          <span style={{ display: "inline-flex", alignItems: "center", gap: 6 }}>
            <AlertTriangle size={14} style={{ color: "var(--hush-amber)" }} />
            <span>Show maintenance banner inside the app</span>
          </span>
        </label>
        <div className="hush-field" style={{ marginTop: 12 }}>
          <label className="hush-field-label">Banner message</label>
          <textarea
            className="hush-textarea"
            data-testid="maintenance-msg"
            value={maintMsg}
            onChange={(e) => setMaintMsg(e.target.value)}
            placeholder="We'll be back at 4 AM EST."
          />
        </div>
      </div>
    </div>
  );
}
