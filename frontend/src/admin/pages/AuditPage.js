/**
 * Audit log page — append-only history of admin actions.
 */
import { useEffect, useState } from "react";
import { RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { api, formatApiError } from "@/admin/api";
import { useAuth } from "@/admin/AuthContext";

export default function AuditPage() {
  const { resellerId } = useAuth();
  const [items, setItems] = useState([]);
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    const params = resellerId ? { reseller_id: resellerId } : {};
    api.get("/admin/audit-log", { params })
      .then(({ data }) => setItems(data.items || []))
      .catch((e) => toast.error(formatApiError(e)));
  }, [resellerId, refresh]);

  return (
    <div data-testid="audit-page">
      <div className="hush-page-title">
        <div>
          <h1>Audit log</h1>
          <p>Every admin action, append-only.</p>
        </div>
        <button
          className="hush-btn hush-btn-secondary"
          data-testid="audit-refresh"
          onClick={() => setRefresh((n) => n + 1)}
        >
          <RefreshCw size={14} /> Refresh
        </button>
      </div>

      {items.length === 0 ? (
        <div className="hush-empty">No actions yet.</div>
      ) : (
        <div className="hush-table-wrap">
          <table className="hush-table" data-testid="audit-table">
            <thead>
              <tr>
                <th>When</th>
                <th>Actor</th>
                <th>Action</th>
                <th>Target</th>
              </tr>
            </thead>
            <tbody>
              {items.map((row) => (
                <tr key={row.id} data-testid={`audit-row-${row.id}`}>
                  <td data-label="When">
                    {new Date(row.created_at).toLocaleString()}
                  </td>
                  <td data-label="Actor">{row.actor_email}</td>
                  <td data-label="Action">
                    <code style={{ fontSize: 12 }}>{row.action}</code>
                  </td>
                  <td data-label="Target" style={{ maxWidth: 300, overflow: "hidden", textOverflow: "ellipsis" }}>
                    <code style={{ fontSize: 11, color: "var(--hush-text-dim)" }}>
                      {row.target}
                    </code>
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
