import { useState } from "react";
import { apiRequest } from "../api";
import { updateToken } from "../lib/session";
import Field from "./Field";
import Notice from "./Notice";

// Shared by the customer and admin dashboards. The backend identifies the user from the JWT.
export default function ChangePasswordCard() {
  const [form, setForm] = useState({ oldPassword: "", newPassword: "", confirmPassword: "" });
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setNotice(null);
    if (!form.oldPassword || !form.newPassword) {
      setNotice({ tone: "error", text: "Enter your current password and a new password." });
      return;
    }
    if (form.newPassword.length < 8) {
      setNotice({ tone: "error", text: "Use a new password of at least 8 characters." });
      return;
    }
    if (form.newPassword !== form.confirmPassword) {
      setNotice({ tone: "error", text: "The new passwords do not match." });
      return;
    }

    setBusy(true);
    const res = await apiRequest("/auth/change-password", "POST", {
      oldPassword: form.oldPassword,
      newPassword: form.newPassword,
    });
    setBusy(false);

    if (res?.status === "SUCCESS") {
      // Older tokens are now revoked; keep this browser signed in with the fresh one
      if (res.data) updateToken(res.data);
      setForm({ oldPassword: "", newPassword: "", confirmPassword: "" });
      setNotice({ tone: "ok", text: res.message || "Password updated." });
    } else {
      setNotice({
        tone: "error",
        text: res?.message || "The password could not be changed. Check your current password.",
      });
    }
  };

  return (
    <section className="card" style={{ maxWidth: 520 }}>
      <h2>Password</h2>
      <p className="lede-sm">Choose a password you do not use on other sites. Changing it signs out your other devices.</p>
      <form className="form-stack" onSubmit={handleSubmit}>
        {notice ? <Notice tone={notice.tone}>{notice.text}</Notice> : null}
        <Field label="Current password" htmlFor="old-password">
          <input
            id="old-password"
            className="input"
            type="password"
            autoComplete="current-password"
            value={form.oldPassword}
            onChange={(e) => setForm({ ...form, oldPassword: e.target.value })}
            required
          />
        </Field>
        <Field label="New password" hint="At least 8 characters" htmlFor="new-password">
          <input
            id="new-password"
            className="input"
            type="password"
            autoComplete="new-password"
            value={form.newPassword}
            onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
            required
          />
        </Field>
        <Field label="Confirm new password" htmlFor="confirm-password">
          <input
            id="confirm-password"
            className="input"
            type="password"
            autoComplete="new-password"
            value={form.confirmPassword}
            onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
            required
          />
        </Field>
        <button className="btn btn-primary" type="submit" disabled={busy}>
          {busy ? "Updating…" : "Update password"}
        </button>
      </form>
    </section>
  );
}
