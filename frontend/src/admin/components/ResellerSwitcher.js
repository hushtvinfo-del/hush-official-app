/**
 * Reseller switcher chip — only rendered for super-admins.
 * Lists resellers and updates the auth context's `resellerId`
 * which every API caller picks up automatically.
 */
import { useEffect, useState } from "react";
import { ChevronDown, Building2 } from "lucide-react";
import { api } from "@/admin/api";
import { useAuth } from "@/admin/AuthContext";

export default function ResellerSwitcher() {
  const { resellerId, setResellerId } = useAuth();
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);

  useEffect(() => {
    let alive = true;
    api.get("/admin/resellers").then(({ data }) => {
      if (alive) setItems(data.items || []);
    }).catch(() => {});
    return () => { alive = false; };
  }, []);

  const active = items.find((r) => r.id === resellerId);

  return (
    <div className="hush-switcher" data-testid="reseller-switcher">
      <button
        type="button"
        className="hush-switcher-trigger"
        data-testid="reseller-switcher-trigger"
        onClick={() => setOpen((v) => !v)}
      >
        <Building2 size={14} />
        <span className="hush-switcher-name">
          {active?.display_name || "All resellers"}
        </span>
        <ChevronDown size={14} />
      </button>
      {open && (
        <div className="hush-switcher-menu" role="menu">
          {items.map((r) => (
            <button
              key={r.id}
              type="button"
              role="menuitem"
              className={
                "hush-switcher-option" +
                (r.id === resellerId ? " is-active" : "")
              }
              data-testid={`reseller-option-${r.slug}`}
              onClick={() => { setResellerId(r.id); setOpen(false); }}
            >
              <span>{r.display_name}</span>
              <span className="hush-switcher-meta">
                {r.device_count} dev · {r.activation_code}
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
