/**
 * Resellers page (super-admin only) — list, create, regenerate
 * activation code, suspend / reactivate.
 */
import { useEffect, useState } from "react";
import { Plus, RefreshCw, Pause, Play } from "lucide-react";
import { toast } from "sonner";
import { api, formatApiError } from "@/admin/api";

export default function ResellersPage() {
  const [items, setItems] = useState([]);
  const [refresh, setRefresh] = useState(0);
  const [showNew, setShowNew] = useState(false);

  useEffect(() => {
    api.get("/admin/resellers").then(({ data }) => {
      setItems(data.items || []);
    }).catch((e) => toast.error(formatApiError(e)));
  }, [refresh]);

  async function regenerate(r) {
    try {
      const { data } = await api.post(`/admin/resellers/${r.id}/regenerate-code`);
      toast.success("New code: " + data.activation_code);
      setRefresh((n) => n + 1);
    } catch (e) { toast.error(formatApiError(e)); }
  }

  async function setStatus(r, status) {
    try {
      await api.patch(`/admin/resellers/${r.id}`, { status });
      toast.success(`${r.display_name} ${status}`);
      setRefresh((n) => n + 1);
    } catch (e) { toast.error(formatApiError(e)); }
  }

  return (
    <div data-testid="resellers-page">
      <div className="hush-page-title">
        <div>
          <h1>Resellers</h1>
          <p>White-label tenants. Each gets their own branding, config, and admin.</p>
        </div>
        <button
          className="hush-btn hush-btn-primary"
          data-testid="resellers-new"
          onClick={() => setShowNew(true)}
        >
          <Plus size={14} /> New reseller
        </button>
      </div>

      {showNew && (
        <NewResellerForm
          onCancel={() => setShowNew(false)}
          onCreated={() => { setShowNew(false); setRefresh((n) => n + 1); }}
        />
      )}

      {items.length === 0 ? (
        <div className="hush-empty">No resellers yet.</div>
      ) : (
        <div className="hush-table-wrap">
          <table className="hush-table" data-testid="resellers-table">
            <thead>
              <tr>
                <th>Reseller</th>
                <th>Slug</th>
                <th>Activation code</th>
                <th>Devices</th>
                <th>Plan</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((r) => (
                <tr key={r.id} data-testid={`reseller-row-${r.slug}`}>
                  <td data-label="Reseller">
                    <strong>{r.display_name}</strong>
                    <div style={{ fontSize: 11, color: "var(--hush-text-dim)" }}>
                      {r.owner_email}
                    </div>
                  </td>
                  <td data-label="Slug">
                    <code style={{ fontSize: 12 }}>{r.slug}</code>
                  </td>
                  <td data-label="Code">
                    <code style={{
                      fontSize: 14, fontWeight: 800,
                      letterSpacing: 1.5, color: "var(--hush-cyan-2)",
                    }}>{r.activation_code}</code>
                  </td>
                  <td data-label="Devices">{r.device_count}</td>
                  <td data-label="Plan">
                    <span className="hush-pill is-info">{r.plan_tier}</span>
                  </td>
                  <td data-label="Status">
                    {r.status === "active"
                      ? <span className="hush-pill is-online">active</span>
                      : <span className="hush-pill is-blocked">{r.status}</span>}
                  </td>
                  <td data-label="">
                    <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
                      <button
                        className="hush-btn hush-btn-secondary"
                        data-testid={`reseller-regen-${r.slug}`}
                        onClick={() => regenerate(r)}
                        title="Regenerate activation code"
                      >
                        <RefreshCw size={13} />
                      </button>
                      {r.status === "active" ? (
                        <button
                          className="hush-btn hush-btn-secondary"
                          data-testid={`reseller-suspend-${r.slug}`}
                          onClick={() => setStatus(r, "suspended")}
                          title="Suspend"
                        >
                          <Pause size={13} />
                        </button>
                      ) : (
                        <button
                          className="hush-btn hush-btn-secondary"
                          data-testid={`reseller-activate-${r.slug}`}
                          onClick={() => setStatus(r, "active")}
                          title="Reactivate"
                        >
                          <Play size={13} />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function NewResellerForm({ onCancel, onCreated }) {
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [plan, setPlan] = useState("standard");
  const [busy, setBusy] = useState(false);

  async function submit() {
    if (!name.trim() || !email.trim()) {
      toast.error("Name and email are required");
      return;
    }
    setBusy(true);
    try {
      const { data } = await api.post("/admin/resellers", {
        display_name: name.trim(),
        owner_email: email.trim(),
        plan_tier: plan,
      });
      toast.success(`Created — code ${data.activation_code}`);
      onCreated();
    } catch (e) { toast.error(formatApiError(e)); }
    finally { setBusy(false); }
  }

  return (
    <div className="hush-card" data-testid="new-reseller-form">
      <h3 style={{ margin: "0 0 12px", fontSize: 16, fontWeight: 800 }}>
        New reseller
      </h3>
      <div className="hush-form-row">
        <div className="hush-field">
          <label className="hush-field-label">Display name</label>
          <input
            className="hush-input"
            data-testid="new-reseller-name"
            value={name} onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="hush-field">
          <label className="hush-field-label">Owner email</label>
          <input
            className="hush-input"
            data-testid="new-reseller-email"
            type="email"
            value={email} onChange={(e) => setEmail(e.target.value)}
          />
        </div>
        <div className="hush-field">
          <label className="hush-field-label">Plan tier</label>
          <select
            className="hush-select"
            data-testid="new-reseller-plan"
            value={plan} onChange={(e) => setPlan(e.target.value)}
          >
            <option value="standard">Standard</option>
            <option value="pro">Pro</option>
            <option value="enterprise">Enterprise</option>
          </select>
        </div>
      </div>
      <div style={{ marginTop: 14, display: "flex", gap: 8, justifyContent: "flex-end" }}>
        <button
          className="hush-btn hush-btn-secondary"
          data-testid="new-reseller-cancel"
          onClick={onCancel}
        >Cancel</button>
        <button
          className="hush-btn hush-btn-primary"
          data-testid="new-reseller-submit"
          onClick={submit}
          disabled={busy}
        >
          {busy ? "Creating…" : "Create reseller"}
        </button>
      </div>
    </div>
  );
}
