import { useState } from "react";
import { Link } from "react-router-dom";
import { apiRequest } from "../api";
import Field from "../components/Field";
import Notice from "../components/Notice";
import Wordmark from "../components/Wordmark";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [error, setError] = useState("");
  const [sent, setSent] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (event) => {
    event.preventDefault();
    setError("");
    if (!EMAIL_PATTERN.test(email.trim())) {
      setError("Enter the email address on your account.");
      return;
    }

    setLoading(true);
    const res = await apiRequest("/auth/forgot-password", "POST", { email: email.trim() });
    setLoading(false);

    if (res?.status === "SUCCESS") {
      setSent(res.message);
      return;
    }
    setError(res?.message || "The request could not be sent. Try again shortly.");
  };

  return (
    <div className="auth-split">
      <aside className="auth-panel">
        <Wordmark inverted />
        <p className="lede">Locked out? We'll email you a single-use link to choose a new password.</p>
        <p className="panel-foot">The link expires after 30 minutes. Administrators recover access through the bank's operations team.</p>
      </aside>
      <div className="auth-form-wrap">
        <div className="auth-card">
          <h1>Reset your password</h1>
          <p className="subtitle">Enter the email address you used to open your account.</p>
          {sent ? (
            <div className="form-stack">
              <Notice tone="ok">{sent}</Notice>
              <p className="subtitle">Check your inbox and spam folder. You can close this page.</p>
            </div>
          ) : (
            <form className="form-stack" onSubmit={handleSubmit}>
              {error ? <Notice tone="error">{error}</Notice> : null}
              <Field label="Email" htmlFor="forgot-email">
                <input
                  id="forgot-email"
                  className="input"
                  type="email"
                  autoComplete="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </Field>
              <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
                {loading ? "Sending…" : "Send reset link"}
              </button>
            </form>
          )}
          <p className="auth-alt">
            Remembered it? <Link to="/">Back to sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
