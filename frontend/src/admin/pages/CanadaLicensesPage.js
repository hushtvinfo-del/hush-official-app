/**
 * HushTV Canada — license management page.
 *
 * Lets a super-admin:
 *   1. Look up a user's license by xtream username
 *   2. Manually grant a 12-month (or N-month) license to a username
 *      — used when an Interac e-Transfer fails to auto-match (typo
 *      in Message field, transfer from a corporate account that
 *      strips the message, etc.)
 *   3. Revoke a license
 *   4. Browse recent orders + active licenses
 *
 * All calls go through the main backend's proxy at
 * /api/admin/canada/* — no admin token ever lives in the browser.
 */
import { useEffect, useState } from "react";
import { Search, UserPlus, UserMinus, RefreshCw, CheckCircle2, AlertTriangle, Mail } from "lucide-react";
import { toast } from "sonner";
import { api, formatApiError } from "@/admin/api";

export default function CanadaLicensesPage() {
  const [lookup, setLookup] = useState("");
  const [lookupState, setLookupState] = useState(null); // {username, license, expiresOn}
  const [busy, setBusy] = useState(false);
  const [orders, setOrders] = useState([]);
  const [licenses, setLicenses] = useState([]);
  const [activeTab, setActiveTab] = useState("approve");
  const [grantMonths, setGrantMonths] = useState(12);

  async function refreshLists() {
    try {
      const [ords, lics] = await Promise.all([
        api.get("/admin/canada/orders", { params: { limit: 30 } }),
        api.get("/admin/canada/licenses", { params: { limit: 100 } }),
      ]);
      setOrders(ords.data.orders || []);
      setLicenses(lics.data.licenses || []);
    } catch (e) {
      toast.error(formatApiError(e));
    }
  }

  useEffect(() => { refreshLists(); }, []);

  function fmtDate(ms) {
    if (!ms) return "—";
    return new Date(ms).toLocaleString();
  }

  async function doLookup(username = lookup) {
    const u = (username || "").trim().toLowerCase();
    if (!u) return;
    setBusy(true);
    try {
      const { data } = await api.get(`/admin/canada/license/${encodeURIComponent(u)}`);
      setLookupState({ username: u, license: data.license || { paid: false } });
    } catch (e) {
      toast.error(formatApiError(e));
      setLookupState(null);
    } finally {
      setBusy(false);
    }
  }

  async function doGrant() {
    const u = (lookup || "").trim().toLowerCase();
    if (!u) {
      toast.error("Enter a username first");
      return;
    }
    setBusy(true);
    try {
      const { data } = await api.post("/admin/canada/grant", {
        xtream_username: u,
        months: Number(grantMonths) || 12,
      });
      toast.success(
        `Granted ${grantMonths} month${grantMonths == 1 ? "" : "s"} to ${u}` +
        (data?.license?.days_remaining ? ` — now ${data.license.days_remaining} days remaining` : ""),
      );
      await doLookup(u);
      await refreshLists();
    } catch (e) {
      toast.error(formatApiError(e));
    } finally {
      setBusy(false);
    }
  }

  async function doRevoke(username) {
    const u = (username || lookup || "").trim().toLowerCase();
    if (!u) return;
    if (!window.confirm(`Revoke the HushTV Canada license for "${u}"? They will be locked out of the app immediately on next launch.`)) return;
    setBusy(true);
    try {
      await api.post("/admin/canada/revoke", { xtream_username: u });
      toast.success(`Revoked ${u}`);
      await doLookup(u);
      await refreshLists();
    } catch (e) {
      toast.error(formatApiError(e));
    } finally {
      setBusy(false);
    }
  }

  async function doForcePoll() {
    setBusy(true);
    try {
      const { data } = await api.post("/admin/canada/poll");
      const s = data.scan || {};
      toast.success(`Scanned ${s.checked || 0} emails, matched ${s.matched || 0}`);
      await refreshLists();
    } catch (e) {
      toast.error(formatApiError(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="p-6 max-w-6xl mx-auto space-y-6" data-testid="canada-licenses-page">
      <header className="flex items-center justify-between gap-4 flex-wrap">
        <div>
          <h1 className="text-2xl font-black tracking-tight">HushTV Canada Licenses</h1>
          <p className="text-sm text-slate-400">
            Manually approve, revoke, and audit $40/year CDN-proxy-fee licenses.
          </p>
        </div>
        <button
          data-testid="canada-force-poll-btn"
          onClick={doForcePoll}
          disabled={busy}
          className="inline-flex items-center gap-2 rounded-lg border border-cyan-500/40 bg-cyan-500/10 px-3 py-2 text-sm font-semibold text-cyan-300 hover:bg-cyan-500/20 disabled:opacity-50"
        >
          <Mail size={16} /> Re-scan Interac inbox
        </button>
      </header>

      <div className="border-b border-slate-700 flex gap-2">
        {[
          ["approve", "Approve / Lookup"],
          ["licenses", `Active licenses (${licenses.length})`],
          ["orders", `Recent orders (${orders.length})`],
        ].map(([k, label]) => (
          <button
            key={k}
            data-testid={`canada-tab-${k}`}
            onClick={() => setActiveTab(k)}
            className={`px-4 py-2 text-sm font-semibold border-b-2 -mb-px transition ${
              activeTab === k
                ? "border-cyan-400 text-cyan-300"
                : "border-transparent text-slate-400 hover:text-slate-200"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {activeTab === "approve" && (
        <div className="space-y-6">
          {/* Lookup + approve panel */}
          <section className="rounded-xl border border-slate-700 bg-slate-900/50 p-6 space-y-4">
            <h2 className="font-bold text-lg">Manually approve a user</h2>
            <p className="text-sm text-slate-400">
              Just enter the Xtream username — no order number, no email, no amount.
              The user immediately gets a paid license tied to that username, valid
              for the number of months you choose (default 12).
            </p>

            <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
              <div className="flex-1">
                <label className="block text-xs uppercase tracking-wider text-slate-400 mb-1">
                  Xtream username
                </label>
                <input
                  data-testid="canada-username-input"
                  type="text"
                  value={lookup}
                  onChange={(e) => setLookup(e.target.value)}
                  onKeyDown={(e) => { if (e.key === "Enter") doLookup(); }}
                  placeholder="bfam23"
                  autoCapitalize="off"
                  spellCheck={false}
                  className="w-full rounded-lg bg-slate-800 border border-slate-600 px-3 py-2 text-base focus:border-cyan-400 focus:outline-none font-mono"
                />
              </div>
              <div className="w-full sm:w-36">
                <label className="block text-xs uppercase tracking-wider text-slate-400 mb-1">
                  Months
                </label>
                <input
                  data-testid="canada-months-input"
                  type="number"
                  min="1"
                  max="120"
                  value={grantMonths}
                  onChange={(e) => setGrantMonths(e.target.value)}
                  className="w-full rounded-lg bg-slate-800 border border-slate-600 px-3 py-2 text-base focus:border-cyan-400 focus:outline-none"
                />
              </div>
            </div>

            <div className="flex gap-2 flex-wrap">
              <button
                data-testid="canada-lookup-btn"
                onClick={() => doLookup()}
                disabled={busy || !lookup.trim()}
                className="inline-flex items-center gap-2 rounded-lg border border-slate-600 bg-slate-800 px-4 py-2 text-sm font-semibold hover:bg-slate-700 disabled:opacity-50"
              >
                <Search size={16} /> Look up
              </button>
              <button
                data-testid="canada-grant-btn"
                onClick={doGrant}
                disabled={busy || !lookup.trim()}
                className="inline-flex items-center gap-2 rounded-lg bg-cyan-500 px-4 py-2 text-sm font-black text-slate-950 hover:bg-cyan-400 disabled:opacity-50"
              >
                <UserPlus size={16} /> Approve & Grant {grantMonths || 12} months
              </button>
            </div>

            {/* Result card */}
            {lookupState && (
              <LookupResult
                state={lookupState}
                onRevoke={() => doRevoke(lookupState.username)}
                busy={busy}
                fmtDate={fmtDate}
              />
            )}
          </section>
        </div>
      )}

      {activeTab === "licenses" && (
        <section className="rounded-xl border border-slate-700 bg-slate-900/50 overflow-hidden">
          <table className="w-full text-sm" data-testid="canada-licenses-table">
            <thead className="bg-slate-800/70 text-xs uppercase tracking-wider text-slate-400">
              <tr>
                <th className="text-left px-4 py-3">Xtream Username</th>
                <th className="text-left px-4 py-3">Paid On</th>
                <th className="text-left px-4 py-3">Expires</th>
                <th className="text-left px-4 py-3">Last Order</th>
                <th className="text-right px-4 py-3">Actions</th>
              </tr>
            </thead>
            <tbody>
              {licenses.length === 0 ? (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-slate-500">No active licenses yet.</td></tr>
              ) : licenses.map(l => (
                <tr key={l.xtream_username} className="border-t border-slate-800 hover:bg-slate-800/30">
                  <td className="px-4 py-3 font-mono">{l.xtream_username}</td>
                  <td className="px-4 py-3 text-slate-300">{fmtDate(l.paid_at)}</td>
                  <td className="px-4 py-3 text-slate-300">
                    {fmtDate(l.expires_at)}
                  </td>
                  <td className="px-4 py-3 font-mono text-slate-400">{l.last_order_id}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => doRevoke(l.xtream_username)}
                      className="text-rose-400 hover:text-rose-300 text-xs font-semibold"
                    >Revoke</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {activeTab === "orders" && (
        <section className="rounded-xl border border-slate-700 bg-slate-900/50 overflow-hidden">
          <table className="w-full text-sm" data-testid="canada-orders-table">
            <thead className="bg-slate-800/70 text-xs uppercase tracking-wider text-slate-400">
              <tr>
                <th className="text-left px-4 py-3">Order ID</th>
                <th className="text-left px-4 py-3">Xtream Username</th>
                <th className="text-left px-4 py-3">Status</th>
                <th className="text-left px-4 py-3">Created</th>
                <th className="text-left px-4 py-3">Paid</th>
              </tr>
            </thead>
            <tbody>
              {orders.length === 0 ? (
                <tr><td colSpan={5} className="px-4 py-8 text-center text-slate-500">No orders yet.</td></tr>
              ) : orders.map(o => (
                <tr key={o.order_id} className="border-t border-slate-800 hover:bg-slate-800/30">
                  <td className="px-4 py-3 font-mono">{o.order_id}</td>
                  <td className="px-4 py-3 font-mono">{o.xtream_username}</td>
                  <td className="px-4 py-3">
                    <span className={
                      o.status === "paid" ? "text-emerald-400 font-bold" :
                      o.status === "expired" ? "text-slate-500" :
                      "text-amber-400"
                    }>{o.status}</span>
                  </td>
                  <td className="px-4 py-3 text-slate-400 text-xs">{fmtDate(o.created_at)}</td>
                  <td className="px-4 py-3 text-slate-400 text-xs">{o.paid_at ? fmtDate(o.paid_at) : "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}

function LookupResult({ state, onRevoke, busy, fmtDate }) {
  const lic = state.license || {};
  const paid = !!lic.paid;
  return (
    <div
      data-testid="canada-lookup-result"
      className={`rounded-xl border-2 p-4 ${
        paid
          ? "border-emerald-500/50 bg-emerald-500/5"
          : "border-rose-500/40 bg-rose-500/5"
      }`}
    >
      <div className="flex items-center gap-2 mb-3">
        {paid ? (
          <CheckCircle2 className="text-emerald-400" size={22} />
        ) : (
          <AlertTriangle className="text-rose-400" size={22} />
        )}
        <span className={`font-black text-sm tracking-wider uppercase ${paid ? "text-emerald-400" : "text-rose-400"}`}>
          {paid ? "Active license" : (lic.expired ? "Expired" : "No license")}
        </span>
      </div>
      <div className="grid grid-cols-2 gap-x-6 gap-y-2 text-sm">
        <div className="text-slate-400">Username</div>
        <div className="font-mono">{state.username}</div>
        {paid && (
          <>
            <div className="text-slate-400">Paid on</div>
            <div>{fmtDate(lic.paid_at)}</div>
            <div className="text-slate-400">Expires</div>
            <div>{fmtDate(lic.expires_at)}</div>
            <div className="text-slate-400">Days remaining</div>
            <div className="font-bold">{lic.days_remaining}</div>
            <div className="text-slate-400">Last Order ID</div>
            <div className="font-mono text-slate-300">{lic.last_order_id}</div>
          </>
        )}
        {lic.expired && (
          <>
            <div className="text-slate-400">Expired on</div>
            <div>{fmtDate(lic.expired_at)}</div>
          </>
        )}
      </div>
      {paid && (
        <button
          data-testid="canada-revoke-btn"
          onClick={onRevoke}
          disabled={busy}
          className="mt-4 inline-flex items-center gap-2 rounded-lg border border-rose-500/40 bg-rose-500/10 px-3 py-1.5 text-xs font-semibold text-rose-300 hover:bg-rose-500/20 disabled:opacity-50"
        >
          <UserMinus size={14} /> Revoke this license
        </button>
      )}
    </div>
  );
}
