/**
 * Admin Diagnostics page — list of Network & Stream Health reports
 * uploaded by user devices. Super-admins see everything in the
 * selected reseller; reseller-admins see only their own.
 *
 * Each row = one report. Clicking a row expands the raw JSON
 * payload so support can eyeball the numbers without leaving the
 * page.
 */
import { useEffect, useState } from "react";
import { RefreshCw, ChevronDown, ChevronRight } from "lucide-react";
import { toast } from "sonner";
import { api, formatApiError } from "@/admin/api";
import { useAuth } from "@/admin/AuthContext";

function verdictClass(v) {
  switch ((v || "").toLowerCase()) {
    case "excellent": case "good":    return "is-online";
    case "fair": case "warning":      return "is-warning";
    case "poor": case "failed": case "bad": case "critical":
                                      return "is-critical";
    default:                          return "is-info";
  }
}

function relativeTime(iso) {
  if (!iso) return "—";
  const ms = Date.now() - new Date(iso).getTime();
  if (ms < 60_000) return Math.floor(ms / 1000) + "s ago";
  if (ms < 3_600_000) return Math.floor(ms / 60_000) + "m ago";
  if (ms < 86_400_000) return Math.floor(ms / 3_600_000) + "h ago";
  return Math.floor(ms / 86_400_000) + "d ago";
}

export default function DiagnosticsPage() {
  const { resellerId } = useAuth();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refresh, setRefresh] = useState(0);
  const [expanded, setExpanded] = useState(null);
  const [deviceFilter, setDeviceFilter] = useState("");

  useEffect(() => {
    let alive = true;
    setLoading(true);
    const params = { reseller_id: resellerId };
    if (deviceFilter.trim()) params.device_id = deviceFilter.trim();
    api.get("/admin/diagnostics", { params })
      .then(({ data }) => { if (alive) setItems(data.items || []); })
      .catch((e) => { if (alive) toast.error(formatApiError(e)); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [resellerId, refresh, deviceFilter]);

  return (
    <div data-testid="diagnostics-page">
      <div className="hush-page-title">
        <div>
          <h1>Diagnostics</h1>
          <p>
            Health Check reports uploaded from user devices.
            Use this to triage "is it the user's connection or the provider?"
          </p>
        </div>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
          <input
            className="hush-input"
            data-testid="diag-device-filter"
            placeholder="Filter by device_id"
            style={{ width: 260 }}
            value={deviceFilter}
            onChange={(e) => setDeviceFilter(e.target.value)}
          />
          <button
            className="hush-btn hush-btn-secondary"
            data-testid="diag-refresh"
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
      ) : items.length === 0 ? (
        <div className="hush-empty" data-testid="diagnostics-empty">
          No health reports yet. Ask a user to run
          {" Settings → Network & Stream Health"} and submit.
        </div>
      ) : (
        <div className="hush-table-wrap">
          <table className="hush-table" data-testid="diagnostics-table">
            <thead>
              <tr>
                <th></th>
                <th>When</th>
                <th>Device</th>
                <th>Verdict</th>
                <th>Internet</th>
                <th>Our clip</th>
                <th>Their stream</th>
              </tr>
            </thead>
            <tbody>
              {items.map((r) => {
                const open = expanded === r.id;
                const net = r.network || {};
                const ref = r.reference || {};
                const prov = r.provider || {};
                return (
                  <>
                    <tr
                      key={r.id}
                      data-testid={`diag-row-${r.id}`}
                      style={{ cursor: "pointer" }}
                      onClick={() => setExpanded(open ? null : r.id)}
                    >
                      <td data-label="">
                        {open
                          ? <ChevronDown size={14} />
                          : <ChevronRight size={14} />}
                      </td>
                      <td data-label="When">{relativeTime(r.created_at)}</td>
                      <td data-label="Device">
                        <div style={{ fontWeight: 600 }}>{r.model || "Unknown"}</div>
                        <div style={{ fontSize: 11, color: "var(--hush-text-dim)" }}>
                          {r.device_id.slice(0, 10)}… · v{r.app_version || "?"}
                        </div>
                      </td>
                      <td data-label="Verdict">
                        <span className={`hush-pill ${verdictClass(r.verdict)}`}>
                          {r.verdict || "unknown"}
                        </span>
                      </td>
                      <td data-label="Internet">
                        {net.download_mbps != null
                          ? `${net.download_mbps.toFixed?.(0) ?? net.download_mbps} Mbps · ${net.ping_ms ?? "?"} ms`
                          : "—"}
                      </td>
                      <td data-label="Our clip">
                        {ref.rebuffers != null
                          ? `${ref.rebuffers} stalls · ${ref.ttff_ms ?? "?"} ms TTFF`
                          : "—"}
                      </td>
                      <td data-label="Their stream">
                        {prov.rebuffers != null
                          ? `${prov.rebuffers} stalls · ${prov.ttff_ms ?? "?"} ms TTFF`
                          : "—"}
                      </td>
                    </tr>
                    {open && (
                      <tr data-testid={`diag-detail-${r.id}`}>
                        <td colSpan={7} style={{ padding: 0 }}>
                          <pre style={{
                            margin: 0,
                            padding: 16,
                            background: "var(--hush-bg-0)",
                            color: "var(--hush-text)",
                            fontSize: 11,
                            lineHeight: 1.5,
                            overflowX: "auto",
                            fontFamily: "ui-monospace, SFMono-Regular, monospace",
                            borderTop: "1px solid var(--hush-stroke)",
                          }}>
                            {JSON.stringify(r, null, 2)}
                          </pre>
                        </td>
                      </tr>
                    )}
                  </>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
