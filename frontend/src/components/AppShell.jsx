import { useNavigate } from "react-router-dom";
import Wordmark from "./Wordmark";
import { clearSession, getSession } from "../lib/session";

export default function AppShell({ title, subtitle, nav, active, onNavigate, children }) {
  const navigate = useNavigate();
  const { username, accountId, role } = getSession();

  const signOut = () => {
    clearSession();
    navigate("/");
  };

  return (
    <div className="shell">
      <header className="topbar">
        <Wordmark inverted />
        <div className="topbar-meta">
          <div className="topbar-user">
            <strong>{username}</strong>
            <span>{role === "ADMIN" ? "Administrator" : accountId}</span>
          </div>
          <button type="button" className="btn btn-ghost" onClick={signOut}>
            Sign out
          </button>
        </div>
      </header>
      <div className="workspace">
        <nav className="sidenav" aria-label="Account">
          <div className="sidenav-label">Menu</div>
          {nav.map((item) => (
            <button
              key={item.id}
              type="button"
              className={item.id === active ? "is-active" : undefined}
              onClick={() => onNavigate(item.id)}
            >
              {item.label}
            </button>
          ))}
        </nav>
        <main className="content">
          <div className="page-head">
            <div>
              <h1>{title}</h1>
              {subtitle ? <p>{subtitle}</p> : null}
            </div>
          </div>
          {children}
        </main>
      </div>
    </div>
  );
}
