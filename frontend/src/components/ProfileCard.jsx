import { useEffect, useState } from "react";
import { apiRequest } from "../api";
import Notice from "./Notice";

// Shows the signed-in user's own details, loaded fresh from the backend (/auth/me).
export default function ProfileCard() {
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let active = true;
    apiRequest("/auth/me", "GET").then((res) => {
      if (!active) return;
      if (res?.status === "SUCCESS") setProfile(res.data);
      else setError(res?.message || "Your account details could not be loaded.");
    });
    return () => {
      active = false;
    };
  }, []);

  const isAdmin = profile?.role === "ADMIN";
  const rows = profile
    ? [
        ["Username", profile.username],
        ["Email", profile.email],
        [isAdmin ? "Vault account" : "Account number", profile.accountId],
        ["Role", isAdmin ? "Administrator" : "Customer"],
        ["Status", profile.verified ? "Verified" : "Awaiting email verification"],
      ]
    : [];

  return (
    <section className="card">
      <h2>Account details</h2>
      <p className="lede-sm">
        {isAdmin
          ? "The administrator email is not used for password recovery."
          : "Password reset links are sent to this email address."}
      </p>
      {error ? <Notice tone="error">{error}</Notice> : null}
      {!profile && !error ? <p className="lede-sm">Loading…</p> : null}
      {profile ? (
        <dl className="detail-list">
          {rows.map(([label, value]) => (
            <div key={label}>
              <dt>{label}</dt>
              <dd className={label === "Account number" || label === "Vault account" ? "mono" : undefined}>{value}</dd>
            </div>
          ))}
        </dl>
      ) : null}
    </section>
  );
}
