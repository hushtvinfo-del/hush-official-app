/**
 * Devices page — sortable list of every device (filterable to
 * online-only), with block / unblock actions.
 */
import { useEffect, useState } from "react";
import { ShieldOff, Shield, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { api, formatApiError } from "@/admin/api";
import { useAuth } from "@/admin/AuthContext";

function relativeTime(iso) {
  if (!iso) return "—";
  const ms = Date.now() - new Date(iso).getTime();
  if (ms < 60_000) return Math.floor(ms / 1000) + "s ago";
  if (ms < 3_600_000) return Math.floor(ms / 60_000) + "m ago";
  if (ms < 86_400_000) return Math.floor(ms / 3_600_000) + "h ago";
  return Math.floor(ms / 86_400_000) + "d ago";
}

export default function DevicesPage() {
  const { resellerId } = useAuth();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [onlyOnline, setOnlyOnline] = useState(false);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    let alive = true;
    setLoading(true);
    const params = resellerId ? { reseller_id: resellerId } : {};
    api.get("/admin/devices", { params })
      .then(({ data }) => { if (alive) setItems(data.items); })
      .catch((e) => { if (alive) toast.error(formatApiError(e)); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [resellerId, refresh]);

  // Auto-refresh every 15s while the page is open.
  useEffect(() => {
    const t = setInterval(() => setRefresh((n) => n + 1), 15000);
    return () => clearInterval(t);
  }, []);

  async function toggleBlock(d) {
    const action = d.status === "blocked" ? "unblock" : "block";
    try {
      await api.post(`/admin/devices/${d.id}/${action}`);
      toast.success(`Device ${action}ed`);
      setRefresh((n) => n + 1);
    } catch (err) { toast.error(formatApiError(err)); }
  }

  const view = onlyOnline ? items.filter((d) => d.online) : items;

  return (
    <div data-testid="devices-page">
      <div className="hush-page-title">
        <div>
          <h1>Devices</h1>
          <p>
            {items.length} total ·{" "}
            <span style={{ color: "var(--hush-green)" }}>
              {items.filter((d) => d.online).length} online
            </span>
          </p>
        </div>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
          <label className="hush-toggle">
            <input
              type="checkbox"
              checked={onlyOnline}
              onChange={(e) => setOnlyOnline(e.target.checked)}
              data-testid="filter-online-only"
            />
            <span className="hush-toggle-track">
              <span className="hush-toggle-thumb" />
            </span>
            <span style={{ fontSize: 13 }}>Online only</span>
          </label>
          <button
            className="hush-btn hush-btn-secondary"
            data-testid="devices-refresh"
            onClick={() => setRefresh((n) => n + 1)}
          >
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
      </div>

      {loading && items.length === 0 ? (
        <div className="hush-card">
          <div className="hush-skeleton" style={{ marginBottom: 12 }} />
          <div className="hush-skeleton" style={{ marginBottom: 12 }} />
          <div className="hush-skeleton" />
        </div>
      ) : view.length === 0 ? (
        <div className="hush-empty" data-testid="devices-empty">
          No devices have phoned home yet.
        </div>
      ) : (
        <div className="hush-table-wrap">
          <table className="hush-table" data-testid="devices-table">
            <thead>
              <tr>
                <th>Status</th>
                <th>Device</th>
                <th>App</th>
                <th>Last screen</th>
                <th>Last seen</th>
                <th>First seen</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {view.map((d) => (
                <tr key={d.id} data-testid={`device-row-${d.id}`}>
                  <td data-label="Status">
                    {d.status === "blocked"
                      ? <span className="hush-pill is-blocked">Blocked</span>
                      : d.online
                        ? <span className="hush-pill is-online">Online</span>
                        : <span className="hush-pill is-offline">Offline</span>}
                  </td>
                  <td data-label="Device">
                    <div style={{ fontWeight: 600 }}>{d.model || "Unknown"}</div>
                    <div style={{ fontSize: 11, color: "var(--hush-text-dim)" }}>
                      {d.id.slice(0, 12)}… · {d.os_version || "—"}
                    </div>
                  </td>
                  <td data-label="App">
                    <span className="hush-pill is-info">v{d.app_version || "?"}</span>
                  </td>
                  <td data-label="Last screen">{d.last_screen || "—"}</td>
                  <td data-label="Last seen">{relativeTime(d.last_seen)}</td>
                  <td data-label="First seen">{relativeTime(d.first_seen)}</td>
                  <td data-label="">
                    <button
                      className={
                        "hush-btn " +
                        (d.status === "blocked"
                          ? "hush-btn-secondary"
                          : "hush-btn-danger")
                      }
                      data-testid={`device-block-${d.id}`}
                      onClick={() => toggleBlock(d)}
                    >
                      {d.status === "blocked"
                        ? <><Shield size={13} /> Unblock</>
                        : <><ShieldOff size={13} /> Block</>}
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
