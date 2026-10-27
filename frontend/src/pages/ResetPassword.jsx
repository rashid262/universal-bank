import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { apiRequest } from "../api";
import Field from "../components/Field";
import Notice from "../components/Notice";
import Wordmark from "../components/Wordmark";

export default function ResetPassword() {
  const [params] = useSearchParams();
  const token = params.get("token") || "";
  const [form, setForm] = useState({ newPassword: "", confirmPassword: "" });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    if (form.newPassword.length < 8) {
      setError("Use a password of at least 8 characters.");
      return;
    }
    if (form.newPassword !== form.confirmPassword) {
      setError("The passwords do not match.");
      return;
    }

    setLoading(true);
    const res = await apiRequest("/auth/reset-password", "POST", { token, newPassword: form.newPassword });
    setLoading(false);

    if (res?.status === "SUCCESS") {
      navigate("/", { replace: true, state: { passwordReset: true } });
      return;
    }
    setError(res?.message || "This reset link is invalid or has expired. Request a new one.");
  };

  return (
    <div className="auth-split">
      <aside className="auth-panel">
        <Wordmark inverted />
        <p className="lede">Choose a new password. Every device signed in with the old one will be signed out.</p>
        <p className="panel-foot">Keep your password private. Universal Bank will never ask for it by email.</p>
      </aside>
      <div className="auth-form-wrap">
        <div className="auth-card">
          <h1>Choose a new password</h1>
          {!token ? (
            <div className="form-stack">
              <Notice tone="error">This reset link is incomplete. Open the link from your email again, or request a new one.</Notice>
              <Link className="btn btn-primary btn-block" to="/forgot-password" style={{ textDecoration: "none" }}>
                Request a new link
              </Link>
            </div>
          ) : (
            <form className="form-stack" onSubmit={handleSubmit}>
              {error ? <Notice tone="error">{error}</Notice> : null}
              <Field label="New password" hint="At least 8 characters" htmlFor="reset-password">
                <input
                  id="reset-password"
                  className="input"
                  type="password"
                  autoComplete="new-password"
                  value={form.newPassword}
                  onChange={(e) => setForm({ ...form, newPassword: e.target.value })}
                />
              </Field>
              <Field label="Confirm new password" htmlFor="reset-confirm">
                <input
                  id="reset-confirm"
                  className="input"
                  type="password"
                  autoComplete="new-password"
                  value={form.confirmPassword}
                  onChange={(e) => setForm({ ...form, confirmPassword: e.target.value })}
                />
              </Field>
              <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
                {loading ? "Saving…" : "Set new password"}
              </button>
            </form>
          )}
          <p className="auth-alt">
            <Link to="/forgot-password">Request a new link</Link> · <Link to="/">Back to sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
