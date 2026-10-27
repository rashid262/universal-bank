import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { apiRequest } from "../api";
import Field from "../components/Field";
import Notice from "../components/Notice";
import Wordmark from "../components/Wordmark";
import { homeForRole, saveSession } from "../lib/session";

export default function Login() {
  const [form, setForm] = useState({ username: "", password: "" });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const navigate = useNavigate();
  const location = useLocation();
  const registered = Boolean(location.state?.registered);
  const passwordReset = Boolean(location.state?.passwordReset);
  const sessionExpired = new URLSearchParams(location.search).get("session") === "expired";

  const handleLogin = async (event) => {
    event.preventDefault();
    setError("");
    if (!form.username.trim() || !form.password) {
      setError("Enter your username and password.");
      return;
    }

    setLoading(true);
    const response = await apiRequest("/auth/login", "POST", form);
    setLoading(false);

    if (response?.status === "SUCCESS") {
      const { token, username, accountId, role } = response.data;
      saveSession({ token, username, accountId, role });
      navigate(homeForRole(role));
      return;
    }

    setError(response?.message || "Sign-in failed. Check your details and try again.");
  };

  return (
    <div className="auth-split">
      <aside className="auth-panel">
        <Wordmark inverted />
        <p className="lede">Quiet banking for people who prefer their money kept in order.</p>
        <p className="panel-foot">Personal accounts · INR, USD, CNY, JPY · Transfers with a confirmed rate</p>
      </aside>
      <div className="auth-form-wrap">
        <div className="auth-card">
          <h1>Sign in</h1>
          <p className="subtitle">Use the credentials issued with your Universal Bank account.</p>
          <form className="form-stack" onSubmit={handleLogin}>
            {registered && !error ? (
              <Notice tone="ok">Account created. Confirm the email we sent, then sign in.</Notice>
            ) : null}
            {passwordReset && !error ? (
              <Notice tone="ok">Password reset. Sign in with your new password.</Notice>
            ) : null}
            {sessionExpired && !error && !passwordReset ? (
              <Notice tone="info">Your session ended. Please sign in again.</Notice>
            ) : null}
            {error ? <Notice tone="error">{error}</Notice> : null}
            <Field label="Username" htmlFor="username">
              <input
                id="username"
                className="input"
                autoComplete="username"
                value={form.username}
                onChange={(e) => setForm({ ...form, username: e.target.value })}
              />
            </Field>
            <Field label="Password" htmlFor="password">
              <input
                id="password"
                className="input"
                type="password"
                autoComplete="current-password"
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
              />
            </Field>
            <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
              {loading ? "Signing in…" : "Continue"}
            </button>
          </form>
          <p className="auth-alt">
            <Link to="/forgot-password">Forgot password?</Link>
          </p>
          <p className="auth-alt">
            New customer? <Link to="/register">Open an account</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
