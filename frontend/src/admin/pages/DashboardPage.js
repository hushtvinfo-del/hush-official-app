/**
 * Dashboard — at-a-glance counts and a quick-broadcast composer.
 */
import { useEffect, useState } from "react";
import { Megaphone, Tv, ShieldOff, Send } from "lucide-react";
import { toast } from "sonner";
import { api, formatApiError } from "@/admin/api";
import { useAuth } from "@/admin/AuthContext";

export default function DashboardPage() {
  const { user, resellerId } = useAuth();
  const [summary, setSummary] = useState(null);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let alive = true;
    const params = resellerId ? { reseller_id: resellerId } : {};
    api.get("/admin/summary", { params }).then(({ data }) => {
      if (alive) setSummary(data);
    }).catch(() => {});
    const t = setInterval(() => setRefresh((n) => n + 1), 15000);
    return () => { alive = false; clearInterval(t); };
  }, [resellerId, refresh]);

  return (
    <div data-testid="dashboard-page">
      <div className="hush-page-title">
        <div>
          <h1>Dashboard</h1>
          <p>Live overview of your fleet and recent activity.</p>
        </div>
      </div>

      <div className="hush-grid-cards">
        <Stat
          testid="stat-online"
          label="Online now"
          value={summary?.online_devices ?? "–"}
          sub="Last 90 s"
          state="online"
          icon={Tv}
        />
        <Stat
          testid="stat-total-devices"
          label="Total devices"
          value={summary?.total_devices ?? "–"}
          sub="All time"
          icon={Tv}
        />
        <Stat
          testid="stat-blocked"
          label="Blocked devices"
          value={summary?.blocked_devices ?? "–"}
          sub="Manual + automatic"
          state={summary?.blocked_devices > 0 ? "warn" : ""}
          icon={ShieldOff}
        />
        <Stat
          testid="stat-broadcasts"
          label="Broadcasts (24h)"
          value={summary?.broadcasts_24h ?? "–"}
          sub="Sent in the last day"
          icon={Megaphone}
        />
        {user?.role === "super_admin" && (
          <Stat
            testid="stat-resellers"
            label="Resellers"
            value={summary?.total_resellers ?? "–"}
            sub="Active tenants"
            icon={Send}
          />
        )}
      </div>

      <QuickBroadcastCard onSent={() => setRefresh((n) => n + 1)} />
    </div>
  );
}

function Stat({ testid, label, value, sub, state, icon: Icon }) {
  return (
    <div
      className={
        "hush-stat" +
        (state === "online" ? " is-online" : "") +
        (state === "warn" ? " is-warn" : "")
      }
      data-testid={testid}
    >
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
        <span className="hush-stat-label">{label}</span>
        {Icon && <Icon size={16} style={{ color: "var(--hush-text-dim)" }} />}
      </div>
      <span className="hush-stat-value">{value}</span>
      <span className="hush-stat-sub">{sub}</span>
    </div>
  );
}

function QuickBroadcastCard({ onSent }) {
  const { resellerId } = useAuth();
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [severity, setSeverity] = useState("info");
  const [busy, setBusy] = useState(false);

  async function send() {
    if (!title.trim() || !body.trim()) {
      toast.error("Title and body are required");
      return;
    }
    setBusy(true);
    try {
      const params = resellerId ? { reseller_id: resellerId } : {};
      const { data } = await api.post(
        "/admin/broadcasts",
        { target_type: "all", title, body, severity },
        { params },
      );
      toast.success(`Broadcast sent to ${data.stats.target_count} device(s)`);
      setTitle(""); setBody("");
      onSent?.();
    } catch (err) {
      toast.error(formatApiError(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="hush-card" style={{ marginTop: 20 }} data-testid="quick-broadcast-card">
      <h3 style={{ margin: "0 0 12px", fontSize: 16, fontWeight: 800 }}>
        Quick broadcast
      </h3>
      <div className="hush-form-row">
        <div className="hush-field">
          <label className="hush-field-label">Title</label>
          <input
            className="hush-input"
            data-testid="qb-title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="Maintenance window tonight"
          />
        </div>
        <div className="hush-field">
          <label className="hush-field-label">Severity</label>
          <select
            className="hush-select"
            data-testid="qb-severity"
            value={severity}
            onChange={(e) => setSeverity(e.target.value)}
          >
            <option value="info">Info</option>
            <option value="warning">Warning</option>
            <option value="critical">Critical</option>
          </select>
        </div>
      </div>
      <div className="hush-field" style={{ marginTop: 12 }}>
        <label className="hush-field-label">Message body</label>
        <textarea
          className="hush-textarea"
          data-testid="qb-body"
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder="We'll be doing scheduled maintenance from 2 AM to 4 AM EST."
        />
      </div>
      <div style={{ marginTop: 12, display: "flex", justifyContent: "flex-end" }}>
        <button
          className="hush-btn hush-btn-primary"
          data-testid="qb-send"
          disabled={busy}
          onClick={send}
        >
          <Send size={14} />
          {busy ? "Sending…" : "Send to all online"}
        </button>
      </div>
    </div>
  );
}
