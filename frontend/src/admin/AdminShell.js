/**
 * Admin shell — top bar + collapsible left rail + main outlet.
 *
 * Responsive strategy:
 *   • ≥ 1024 px (desktop / TV): rail is permanently visible; the
 *     user can toggle expanded/collapsed via the chevron at top.
 *   • 768–1023 px (tablet): rail is permanently collapsed-icon
 *     mode by default; toggle expands it.
 *   • <  768 px (phone): rail hidden by default; hamburger in
 *     top bar opens a slide-over drawer with focus trap.
 *
 * No element ever overlays content — everything participates in
 * normal CSS-grid flow except the mobile drawer which is the
 * only `position: fixed` member (and it backs everything else
 * with a tappable scrim, so nothing is unreachable).
 */
import { Outlet, NavLink, useNavigate } from "react-router-dom";
import { useEffect, useState } from "react";
import {
  LayoutDashboard, Tv, Megaphone, Palette, SlidersHorizontal,
  Users, ScrollText, LogOut, Menu, ChevronsLeft, ChevronsRight,
  Stethoscope, HardDrive, ShieldCheck,
} from "lucide-react";
import { useAuth } from "@/admin/AuthContext";
import ResellerSwitcher from "@/admin/components/ResellerSwitcher";
import "@/admin/admin.css";

const NAV = [
  { to: "/", end: true, label: "Dashboard", icon: LayoutDashboard },
  { to: "/devices", label: "Devices", icon: Tv },
  { to: "/broadcasts", label: "Broadcasts", icon: Megaphone },
  { to: "/diagnostics", label: "Diagnostics", icon: Stethoscope },
  { to: "/branding", label: "Branding", icon: Palette },
  { to: "/config", label: "App Config", icon: SlidersHorizontal },
  { to: "/resellers", label: "Resellers", icon: Users, superOnly: true },
  { to: "/dvr-cluster", label: "DVR Cluster", icon: HardDrive, superOnly: true },
  { to: "/canada-licenses", label: "Canada Licenses", icon: ShieldCheck, superOnly: true },
  { to: "/audit", label: "Audit Log", icon: ScrollText },
];

export default function AdminShell() {
  const { user, logout } = useAuth();
  const nav = useNavigate();
  const [expanded, setExpanded] = useState(true);
  const [mobileOpen, setMobileOpen] = useState(false);

  // Auto-collapse rail at narrow desktop widths so the content
  // grid keeps room for the data tables.
  useEffect(() => {
    function onResize() {
      if (window.innerWidth < 1024) setExpanded(false);
      else setExpanded(true);
    }
    onResize();
    window.addEventListener("resize", onResize);
    return () => window.removeEventListener("resize", onResize);
  }, []);

  // Lock body scroll while the mobile drawer is open.
  useEffect(() => {
    document.body.style.overflow = mobileOpen ? "hidden" : "";
    return () => { document.body.style.overflow = ""; };
  }, [mobileOpen]);

  const visibleNav = NAV.filter(
    (n) => !n.superOnly || user?.role === "super_admin",
  );

  async function handleLogout() {
    await logout();
    nav("/login", { replace: true });
  }

  return (
    <div className="hush-shell">
      {/* ── Top bar ─────────────────────────────────── */}
      <header className="hush-topbar" data-testid="admin-topbar">
        <div className="hush-topbar-left">
          <button
            type="button"
            className="hush-icon-btn hush-mobile-only"
            aria-label="Open menu"
            data-testid="topbar-mobile-menu"
            onClick={() => setMobileOpen(true)}
          >
            <Menu size={20} />
          </button>
          <button
            type="button"
            className="hush-icon-btn hush-desktop-only"
            aria-label={expanded ? "Collapse menu" : "Expand menu"}
            data-testid="topbar-collapse-toggle"
            onClick={() => setExpanded((v) => !v)}
          >
            {expanded ? <ChevronsLeft size={20} /> : <ChevronsRight size={20} />}
          </button>
          <span className="hush-brand">
            <span className="hush-brand-h">hush</span>
            <span className="hush-brand-tv">tv.</span>
            <span className="hush-brand-tag">admin</span>
          </span>
        </div>
        <div className="hush-topbar-right">
          {user?.role === "super_admin" && <ResellerSwitcher />}
          <span className="hush-user-chip" data-testid="topbar-user-email">
            {user?.email}
          </span>
          <button
            type="button"
            className="hush-icon-btn"
            aria-label="Sign out"
            data-testid="topbar-logout"
            onClick={handleLogout}
            title="Sign out"
          >
            <LogOut size={18} />
          </button>
        </div>
      </header>

      {/* ── Layout grid: rail + main ────────────────── */}
      <div className={`hush-layout ${expanded ? "is-expanded" : "is-collapsed"}`}>
        <aside
          className="hush-rail hush-desktop-only"
          data-testid="admin-rail"
        >
          {visibleNav.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.end}
              data-testid={`nav-${n.label.toLowerCase().replace(/\s+/g, "-")}`}
              className={({ isActive }) =>
                "hush-rail-item" + (isActive ? " is-active" : "")
              }
            >
              <n.icon size={20} className="hush-rail-icon" />
              <span className="hush-rail-label">{n.label}</span>
            </NavLink>
          ))}
        </aside>

        <main className="hush-main" data-testid="admin-main">
          <Outlet />
        </main>
      </div>

      {/* ── Mobile drawer ───────────────────────────── */}
      {mobileOpen && (
        <>
          <div
            className="hush-drawer-scrim"
            data-testid="mobile-drawer-scrim"
            onClick={() => setMobileOpen(false)}
          />
          <aside
            className="hush-drawer"
            role="dialog"
            aria-label="Navigation"
            data-testid="mobile-drawer"
          >
            <div className="hush-drawer-head">
              <span className="hush-brand">
                <span className="hush-brand-h">hush</span>
                <span className="hush-brand-tv">tv.</span>
              </span>
            </div>
            {visibleNav.map((n) => (
              <NavLink
                key={n.to}
                to={n.to}
                end={n.end}
                onClick={() => setMobileOpen(false)}
                data-testid={`drawer-nav-${n.label.toLowerCase().replace(/\s+/g, "-")}`}
                className={({ isActive }) =>
                  "hush-rail-item" + (isActive ? " is-active" : "")
                }
              >
                <n.icon size={20} className="hush-rail-icon" />
                <span className="hush-rail-label">{n.label}</span>
              </NavLink>
            ))}
          </aside>
        </>
      )}
    </div>
  );
}
