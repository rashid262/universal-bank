import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { apiRequest } from "../api";
import Field from "../components/Field";
import Notice from "../components/Notice";
import Wordmark from "../components/Wordmark";

const ACCOUNT_PATTERN = /^UB-\d{4}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export default function Register() {
  const [form, setForm] = useState({
    username: "",
    email: "",
    password: "",
    accountId: "",
    currency: "INR",
  });
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();

  const handleRegister = async (event) => {
    event.preventDefault();
    setError("");

    if (!form.username.trim() || !form.email.trim() || !form.password || !form.accountId.trim()) {
      setError("Complete every field before submitting.");
      return;
    }
    if (form.password.length < 8) {
      setError("Use a password of at least 8 characters.");
      return;
    }
    if (!EMAIL_PATTERN.test(form.email)) {
      setError("Enter a valid email address. A verification link will be sent there.");
      return;
    }
    if (!ACCOUNT_PATTERN.test(form.accountId)) {
      setError("Account numbers use the format UB-0000, for example UB-4721.");
      return;
    }

    setLoading(true);
    const res = await apiRequest("/auth/register", "POST", form);
    setLoading(false);

    if (res?.status === "SUCCESS") {
      navigate("/", { state: { registered: true, emailSent: res.data !== false } });
      return;
    }
    setError(res?.message || "Registration could not be completed.");
  };

  return (
    <div className="auth-split">
      <aside className="auth-panel">
        <Wordmark inverted />
        <p className="lede">Open a personal account, then confirm it from your email before the first transfer.</p>
        <p className="panel-foot">Choose the currency your account will hold. Cross-currency payments use a quoted reference rate.</p>
      </aside>
      <div className="auth-form-wrap">
        <div className="auth-card">
          <h1>Open an account</h1>
          <p className="subtitle">You will receive a verification link before online banking is fully available.</p>
          <form className="form-stack" onSubmit={handleRegister}>
            {error ? <Notice tone="error">{error}</Notice> : null}
            <Field label="Username" htmlFor="reg-username">
              <input
                id="reg-username"
                className="input"
                autoComplete="username"
                value={form.username}
                onChange={(e) => setForm({ ...form, username: e.target.value })}
              />
            </Field>
            <Field label="Email" htmlFor="reg-email">
              <input
                id="reg-email"
                className="input"
                type="email"
                autoComplete="email"
                value={form.email}
                onChange={(e) => setForm({ ...form, email: e.target.value })}
              />
            </Field>
            <Field label="Password" hint="At least 8 characters" htmlFor="reg-password">
              <input
                id="reg-password"
                className="input"
                type="password"
                autoComplete="new-password"
                value={form.password}
                onChange={(e) => setForm({ ...form, password: e.target.value })}
              />
            </Field>
            <Field label="Account number" hint="Format UB-XXXX" htmlFor="reg-account">
              <input
                id="reg-account"
                className="input"
                value={form.accountId}
                onChange={(e) => setForm({ ...form, accountId: e.target.value.toUpperCase() })}
              />
            </Field>
            <Field label="Account currency" htmlFor="reg-currency">
              <select
                id="reg-currency"
                className="select"
                value={form.currency}
                onChange={(e) => setForm({ ...form, currency: e.target.value })}
              >
                <option value="INR">INR — Indian rupee</option>
                <option value="USD">USD — US dollar</option>
                <option value="CNY">CNY — Chinese yuan</option>
                <option value="JPY">JPY — Japanese yen</option>
              </select>
            </Field>
            <button className="btn btn-primary btn-block" type="submit" disabled={loading}>
              {loading ? "Submitting…" : "Create account"}
            </button>
          </form>
          <p className="auth-alt">
            Already a customer? <Link to="/">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
