/**
 * DVR Cluster page — list of registered DVR nodes with live stats,
 * cluster-wide summary, and a "Register new server" form. Shows a
 * yellow urgency banner when any node is past 90% disk usage.
 *
 * Super-admin only (guarded at App.js route level).
 */
import { useEffect, useState } from "react";
import {
  HardDrive, RefreshCw, PlusCircle, AlertTriangle, Trash2, Activity,
} from "lucide-react";
import { toast } from "sonner";
import { api, formatApiError } from "@/admin/api";

// "1.23 TB" / "456 GB" / "789 MB"
function formatBytes(b) {
  if (!b || b <= 0) return "—";
  const tb = b / 1099511627776;
  if (tb >= 1) return tb.toFixed(2) + " TB";
  const gb = b / 1073741824;
  if (gb >= 1) return gb.toFixed(1) + " GB";
  const mb = b / 1048576;
  return mb.toFixed(0) + " MB";
}

function formatDuration(s) {
  if (!s || s <= 0) return "—";
  const d = Math.floor(s / 86400);
  const h = Math.floor((s % 86400) / 3600);
  const m = Math.floor((s % 3600) / 60);
  if (d > 0) return `${d}d ${h}h`;
  if (h > 0) return `${h}h ${m}m`;
  return `${m}m`;
}

function diskPillClass(pct) {
  if (pct >= 90) return "is-blocked";   // red
  if (pct >= 75) return "is-offline";   // amber-ish
  return "is-online";                   // green
}

export default function DvrClusterPage() {
  const [items, setItems] = useState([]);
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshTick, setRefreshTick] = useState(0);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    api.get("/admin/dvr/servers")
      .then(({ data }) => {
        if (!alive) return;
        setItems(data.items || []);
        setSummary(data.summary || null);
      })
      .catch((e) => { if (alive) toast.error(formatApiError(e)); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [refreshTick]);

  // Auto-refresh every 10 s so disk-full warnings surface quickly.
  useEffect(() => {
    const t = setInterval(() => setRefreshTick((n) => n + 1), 10_000);
    return () => clearInterval(t);
  }, []);

  async function removeServer(id, label) {
    if (!window.confirm(`Remove "${label}" from the cluster?\n\nThis only removes it from the admin panel; recordings on the node are untouched.`))
      return;
    try {
      await api.delete(`/admin/dvr/servers/${id}`);
      toast.success("Server removed from cluster");
      setRefreshTick((n) => n + 1);
    } catch (e) { toast.error(formatApiError(e)); }
  }

  return (
    <div data-testid="dvr-cluster-page">
      <div className="hush-page-title">
        <div>
          <h1>DVR Cluster</h1>
          <p>
            Registered recording servers — live disk usage, active
            captures, and health per node.
          </p>
        </div>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
          <button
            className="hush-btn hush-btn-secondary"
            data-testid="dvr-refresh"
            onClick={() => setRefreshTick((n) => n + 1)}
          >
            <RefreshCw size={14} /> Refresh
          </button>
          <button
            className="hush-btn hush-btn-primary"
            data-testid="dvr-add-server-btn"
            onClick={() => setShowForm((s) => !s)}
          >
            <PlusCircle size={14} /> Add server
          </button>
        </div>
      </div>

      {summary?.add_server_urgent && (
        <div
          className="hush-card"
          data-testid="dvr-urgent-banner"
          style={{
            background: "rgba(239, 68, 68, 0.12)",
            borderColor: "rgba(239, 68, 68, 0.55)",
            display: "flex",
            alignItems: "center",
            gap: 12,
            marginBottom: 16,
          }}
        >
          <AlertTriangle size={20} color="#fca5a5" />
          <div>
            <div style={{ fontWeight: 700, color: "#fca5a5" }}>
              A node is past 90% full — add a new server.
            </div>
            <div style={{ fontSize: 12, color: "var(--hush-text-dim)" }}>
              Once full, new recordings on that node will fail. Bring
              another server online and register it below.
            </div>
          </div>
        </div>
      )}

      {summary && (
        <div
          data-testid="dvr-summary-stats"
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))",
            gap: 12,
            marginBottom: 16,
          }}
        >
          <StatCard label="Servers online" value={`${summary.servers_online}/${summary.servers_total}`} />
          <StatCard
            label="Cluster storage"
            value={formatBytes(summary.disk_total_bytes)}
            sub={`${formatBytes(summary.disk_free_bytes)} free · ${summary.pct_used.toFixed(1)}% used`}
          />
          <StatCard label="Active recordings" value={summary.active_recordings} icon={<Activity size={14} />} />
          <StatCard label="Total captures" value={summary.total_recordings} sub={`${summary.users_with_data} users with data`} />
        </div>
      )}

      {showForm && (
        <AddServerForm
          onClose={() => setShowForm(false)}
          onAdded={() => { setShowForm(false); setRefreshTick((n) => n + 1); }}
        />
      )}

      {loading && items.length === 0 ? (
        <div className="hush-card">
          <div className="hush-skeleton" style={{ marginBottom: 12 }} />
          <div className="hush-skeleton" />
        </div>
      ) : items.length === 0 ? (
        <div className="hush-empty" data-testid="dvr-empty">
          No DVR servers registered yet. Click <strong>Add server</strong> to
          register the first node.
        </div>
      ) : (
        <div className="hush-table-wrap">
          <table className="hush-table" data-testid="dvr-servers-table">
            <thead>
              <tr>
                <th>Status</th>
                <th>Server</th>
                <th>Disk</th>
                <th>Usage</th>
                <th>Active</th>
                <th>Captures</th>
                <th>Uptime</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {items.map((s) => (
                <tr key={s.id} data-testid={`dvr-server-row-${s.id}`}>
                  <td data-label="Status">
                    {s.online
                      ? <span className="hush-pill is-online">Online</span>
                      : <span className="hush-pill is-blocked" title={s.error || ""}>Offline</span>}
                  </td>
                  <td data-label="Server">
                    <div style={{ fontWeight: 600, display: "flex", alignItems: "center", gap: 6 }}>
                      <HardDrive size={14} style={{ color: "var(--hush-cyan)" }} />
                      {s.label}
                    </div>
                    <div style={{ fontSize: 11, color: "var(--hush-text-dim)" }}>
                      {s.ip}
                    </div>
                  </td>
                  <td data-label="Disk">
                    {formatBytes(s.disk_total_bytes)}
                    <div style={{ fontSize: 11, color: "var(--hush-text-dim)" }}>
                      {formatBytes(s.disk_free_bytes)} free
                    </div>
                  </td>
                  <td data-label="Usage" style={{ minWidth: 140 }}>
                    <span className={`hush-pill ${diskPillClass(s.pct_used)}`}>
                      {s.pct_used.toFixed(1)}%
                    </span>
                    <UsageBar pct={s.pct_used} />
                  </td>
                  <td data-label="Active">{s.active_recordings}</td>
                  <td data-label="Captures">
                    {s.total_recordings}
                    <div style={{ fontSize: 11, color: "var(--hush-text-dim)" }}>
                      {s.users_with_data} users
                    </div>
                  </td>
                  <td data-label="Uptime">{formatDuration(s.uptime_s)}</td>
                  <td data-label="">
                    <button
                      className="hush-btn hush-btn-danger"
                      data-testid={`dvr-remove-${s.id}`}
                      onClick={() => removeServer(s.id, s.label)}
                    >
                      <Trash2 size={13} /> Remove
                    </button>
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

function StatCard({ label, value, sub, icon }) {
  return (
    <div className="hush-card" style={{ padding: 16 }}>
      <div style={{ fontSize: 11, textTransform: "uppercase", letterSpacing: 1.2, color: "var(--hush-text-dim)", display: "flex", alignItems: "center", gap: 6 }}>
        {icon}{label}
      </div>
      <div style={{ fontSize: 26, fontWeight: 800, marginTop: 4 }}>{value}</div>
      {sub && <div style={{ fontSize: 11, color: "var(--hush-text-dim)" }}>{sub}</div>}
    </div>
  );
}

function UsageBar({ pct }) {
  const color = pct >= 90 ? "#ef4444" : pct >= 75 ? "#fbbf24" : "#22c55e";
  return (
    <div style={{ marginTop: 6, height: 4, background: "rgba(255,255,255,0.1)", borderRadius: 2 }}>
      <div style={{
        height: "100%", width: `${Math.min(100, Math.max(0, pct)).toFixed(1)}%`,
        background: color, borderRadius: 2, transition: "width 0.3s",
      }} />
    </div>
  );
}

function AddServerForm({ onClose, onAdded }) {
  const [form, setForm] = useState({
    ip: "",
    label: "",
    stats_token: "",
    notes: "",
  });
  const [saving, setSaving] = useState(false);

  async function submit(e) {
    e.preventDefault();
    if (!form.ip.trim() || !form.label.trim() || !form.stats_token.trim()) {
      toast.error("IP, label, and stats token are required");
      return;
    }
    setSaving(true);
    try {
      await api.post("/admin/dvr/servers", form);
      toast.success("Server registered");
      onAdded();
    } catch (e) { toast.error(formatApiError(e)); }
    finally { setSaving(false); }
  }

  return (
    <form
      className="hush-card"
      data-testid="dvr-add-server-form"
      onSubmit={submit}
      style={{ marginBottom: 16, display: "grid", gap: 10 }}
    >
      <div style={{ fontWeight: 700, fontSize: 15 }}>Register a new DVR server</div>
      <div style={{ fontSize: 12, color: "var(--hush-text-dim)", marginBottom: 4 }}>
        The node must already be running <code>/opt/hushdvr/dvr_service.py</code> with
        Nginx proxying <code>/api/dvr/*</code> to it. The stats token is the
        <code> HUSHDVR_STATS_TOKEN</code> value you set in the node's systemd
        unit.
      </div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        <div className="hush-field">
          <label className="hush-field-label">Public IP</label>
          <input
            className="hush-input" data-testid="dvr-form-ip"
            placeholder="e.g. 216.152.148.150"
            value={form.ip}
            onChange={(e) => setForm({ ...form, ip: e.target.value })}
          />
        </div>
        <div className="hush-field">
          <label className="hush-field-label">Label</label>
          <input
            className="hush-input" data-testid="dvr-form-label"
            placeholder="e.g. DVR-East-1"
            value={form.label}
            onChange={(e) => setForm({ ...form, label: e.target.value })}
          />
        </div>
      </div>
      <div className="hush-field">
        <label className="hush-field-label">Stats token</label>
        <input
          className="hush-input" data-testid="dvr-form-token"
          type="password"
          placeholder="HUSHDVR_STATS_TOKEN value on the node"
          value={form.stats_token}
          onChange={(e) => setForm({ ...form, stats_token: e.target.value })}
        />
      </div>
      <div className="hush-field">
        <label className="hush-field-label">Notes (optional)</label>
        <input
          className="hush-input" data-testid="dvr-form-notes"
          placeholder="e.g. 83 TB RAID-0, east datacenter"
          value={form.notes}
          onChange={(e) => setForm({ ...form, notes: e.target.value })}
        />
      </div>
      <div style={{ display: "flex", gap: 8, justifyContent: "flex-end", marginTop: 4 }}>
        <button
          type="button"
          className="hush-btn hush-btn-secondary"
          onClick={onClose}
          data-testid="dvr-form-cancel"
        >Cancel</button>
        <button
          type="submit"
          className="hush-btn hush-btn-primary"
          disabled={saving}
          data-testid="dvr-form-submit"
        >{saving ? "Verifying…" : "Register server"}</button>
      </div>
    </form>
  );
}
