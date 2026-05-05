/**
 * Broadcasts page — list + composer with target picker.
 */
import { useEffect, useState } from "react";
import { Send, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { api, formatApiError } from "@/admin/api";
import { useAuth } from "@/admin/AuthContext";

export default function BroadcastsPage() {
  const { resellerId } = useAuth();
  const [items, setItems] = useState([]);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    const params = resellerId ? { reseller_id: resellerId } : {};
    api.get("/admin/broadcasts", { params }).then(({ data }) => {
      setItems(data.items || []);
    }).catch((e) => toast.error(formatApiError(e)));
  }, [resellerId, refresh]);

  return (
    <div data-testid="broadcasts-page">
      <div className="hush-page-title">
        <div>
          <h1>Broadcasts</h1>
          <p>Push messages to one device, a group, or every user.</p>
        </div>
        <button
          className="hush-btn hush-btn-secondary"
          data-testid="broadcasts-refresh"
          onClick={() => setRefresh((n) => n + 1)}
        >
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      <BroadcastComposer onSent={() => setRefresh((n) => n + 1)} />

      <h2 style={{ fontSize: 16, fontWeight: 800, margin: "28px 0 12px" }}>
        Recent
      </h2>
      {items.length === 0 ? (
        <div className="hush-empty" data-testid="broadcasts-empty">
          Nothing sent yet.
        </div>
      ) : (
        <div className="hush-table-wrap">
          <table className="hush-table" data-testid="broadcasts-table">
            <thead>
              <tr>
                <th>Severity</th>
                <th>Title</th>
                <th>Body</th>
                <th>Target</th>
                <th>Stats</th>
                <th>Sent</th>
              </tr>
            </thead>
            <tbody>
              {items.map((b) => (
                <tr key={b.id} data-testid={`broadcast-row-${b.id}`}>
                  <td data-label="Severity">
                    <span className={`hush-pill is-${b.severity}`}>{b.severity}</span>
                  </td>
                  <td data-label="Title">
                    <strong>{b.title}</strong>
                  </td>
                  <td data-label="Body" style={{ maxWidth: 360 }}>
                    {b.body}
                  </td>
                  <td data-label="Target">
                    {b.target_type === "all" && "All devices"}
                    {b.target_type === "device" && (b.target_device_id?.slice(0, 8) + "…")}
                    {b.target_type === "group" && "Group"}
                  </td>
                  <td data-label="Stats">
                    <span style={{ fontFamily: "monospace", fontSize: 12 }}>
                      {b.stats?.delivered || 0}/{b.stats?.target_count || 0} delivered
                      {" · "}
                      {b.stats?.displayed || 0} seen
                    </span>
                  </td>
                  <td data-label="Sent">
                    {b.sent_at ? new Date(b.sent_at).toLocaleString() : "queued"}
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

function BroadcastComposer({ onSent }) {
  const { resellerId } = useAuth();
  const [targetType, setTargetType] = useState("all");
  const [deviceId, setDeviceId] = useState("");
  const [appVersion, setAppVersion] = useState("");
  const [model, setModel] = useState("");
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [severity, setSeverity] = useState("info");
  const [busy, setBusy] = useState(false);

  async function send() {
    if (!title.trim() || !body.trim()) {
      toast.error("Title and body are required");
      return;
    }
    if (targetType === "device" && !deviceId.trim()) {
      toast.error("Enter a device ID");
      return;
    }
    setBusy(true);
    try {
      const filter = {};
      if (targetType === "group") {
        if (appVersion.trim()) filter.app_version = appVersion.trim();
        if (model.trim()) filter.model = model.trim();
      }
      const params = resellerId ? { reseller_id: resellerId } : {};
      const { data } = await api.post(
        "/admin/broadcasts",
        {
          target_type: targetType,
          target_device_id: targetType === "device" ? deviceId.trim() : null,
          target_filter: targetType === "group" ? filter : null,
          title, body, severity,
        },
        { params },
      );
      toast.success(`Sent to ${data.stats.target_count} device(s)`);
      setTitle(""); setBody(""); setDeviceId("");
      setAppVersion(""); setModel("");
      onSent?.();
    } catch (err) { toast.error(formatApiError(err)); }
    finally { setBusy(false); }
  }

  return (
    <div className="hush-card" data-testid="broadcast-composer">
      <h3 style={{ margin: "0 0 14px", fontSize: 16, fontWeight: 800 }}>
        New broadcast
      </h3>
      <div className="hush-form-row">
        <div className="hush-field">
          <label className="hush-field-label">Target</label>
          <select
            className="hush-select"
            data-testid="bc-target-type"
            value={targetType}
            onChange={(e) => setTargetType(e.target.value)}
          >
            <option value="all">All devices in this reseller</option>
            <option value="group">A group (filtered)</option>
            <option value="device">A specific device</option>
          </select>
        </div>
        <div className="hush-field">
          <label className="hush-field-label">Severity</label>
          <select
            className="hush-select"
            data-testid="bc-severity"
            value={severity}
            onChange={(e) => setSeverity(e.target.value)}
          >
            <option value="info">Info</option>
            <option value="warning">Warning</option>
            <option value="critical">Critical</option>
          </select>
        </div>
      </div>

      {targetType === "device" && (
        <div className="hush-field" style={{ marginTop: 12 }}>
          <label className="hush-field-label">Device ID</label>
          <input
            className="hush-input"
            data-testid="bc-device-id"
            value={deviceId}
            onChange={(e) => setDeviceId(e.target.value)}
            placeholder="paste from Devices page"
          />
        </div>
      )}
      {targetType === "group" && (
        <div className="hush-form-row" style={{ marginTop: 12 }}>
          <div className="hush-field">
            <label className="hush-field-label">App version (exact)</label>
            <input
              className="hush-input"
              data-testid="bc-filter-version"
              value={appVersion}
              onChange={(e) => setAppVersion(e.target.value)}
              placeholder="e.g. 1.42.95"
            />
          </div>
          <div className="hush-field">
            <label className="hush-field-label">Device model (exact)</label>
            <input
              className="hush-input"
              data-testid="bc-filter-model"
              value={model}
              onChange={(e) => setModel(e.target.value)}
              placeholder="e.g. NVIDIA Shield"
            />
          </div>
        </div>
      )}

      <div className="hush-field" style={{ marginTop: 12 }}>
        <label className="hush-field-label">Title</label>
        <input
          className="hush-input"
          data-testid="bc-title"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          maxLength={120}
        />
      </div>
      <div className="hush-field" style={{ marginTop: 12 }}>
        <label className="hush-field-label">Body</label>
        <textarea
          className="hush-textarea"
          data-testid="bc-body"
          value={body}
          onChange={(e) => setBody(e.target.value)}
          maxLength={2000}
        />
      </div>
      <div style={{ marginTop: 14, display: "flex", justifyContent: "flex-end" }}>
        <button
          className="hush-btn hush-btn-primary"
          data-testid="bc-send"
          onClick={send}
          disabled={busy}
        >
          <Send size={14} /> {busy ? "Sending…" : "Send"}
        </button>
      </div>
    </div>
  );
}
