import { useCallback, useEffect, useState } from "react";
import { apiRequest } from "../api";
import AppShell from "../components/AppShell";
import ChangePasswordCard from "../components/ChangePasswordCard";
import ProfileCard from "../components/ProfileCard";
import Field from "../components/Field";
import Notice from "../components/Notice";
import { formatDateTime, formatMoney } from "../lib/money";

const NAV = [
  { id: "vault", label: "Vault" },
  { id: "accounts", label: "Accounts" },
  { id: "ledger", label: "Ledger" },
  { id: "security", label: "Profile" },
];
const ACCOUNT_PATTERN = /^UB-\d{4}$/;

export default function AdminDashboard() {
  const [section, setSection] = useState("vault");
  const [accForm, setAccForm] = useState({ id: "", username: "", email: "", currency: "INR" });
  const [creditForm, setCreditForm] = useState({ id: "", amount: "", key: "" });
  const [creditQuote, setCreditQuote] = useState(null);
  const [creditQuoteLoading, setCreditQuoteLoading] = useState(false);
  const [creditQuoteError, setCreditQuoteError] = useState("");
  const [creditQuoteRefresh, setCreditQuoteRefresh] = useState(0);
  const [vaultFunding, setVaultFunding] = useState({ amount: "", key: "" });
  const [globalLogs, setGlobalLogs] = useState([]);
  const [vaultBalance, setVaultBalance] = useState(null);
  const [vaultBalanceError, setVaultBalanceError] = useState("");
  const [vaultBalanceLoading, setVaultBalanceLoading] = useState(true);
  const [notice, setNotice] = useState(null);
  const [busy, setBusy] = useState(false);

  const fetchGlobalLogs = useCallback(async () => {
    const res = await apiRequest("/admin/transactions/all", "GET");
    if (res?.status === "SUCCESS") {
      setGlobalLogs(res.data || []);
    }
  }, []);

  const fetchVaultBalance = useCallback(async () => {
    const res = await apiRequest("/accounts/UB-0000/balance", "GET");
    if (res?.status === "SUCCESS") {
      setVaultBalance(res.data);
      setVaultBalanceError("");
    } else {
      setVaultBalanceError(res?.message || "Unable to load the system vault balance.");
    }
    setVaultBalanceLoading(false);
  }, []);

  useEffect(() => {
    const initialFetch = setTimeout(() => {
      fetchGlobalLogs();
      fetchVaultBalance();
    }, 0);
    const interval = setInterval(() => {
      fetchGlobalLogs();
      fetchVaultBalance();
    }, 30000);
    return () => {
      clearTimeout(initialFetch);
      clearInterval(interval);
    };
  }, [fetchGlobalLogs, fetchVaultBalance]);

  useEffect(() => {
    let cancelled = false;
    let quoteTimer;
    const amount = Number(creditForm.amount);
    const validRequest = ACCOUNT_PATTERN.test(creditForm.id)
      && Number.isFinite(amount)
      && amount > 0;

    const resetTimer = setTimeout(() => {
      if (cancelled) return;
      setCreditQuote(null);
      setCreditQuoteError("");
      if (!validRequest) {
        setCreditQuoteLoading(false);
        return;
      }

      setCreditQuoteLoading(true);
      quoteTimer = setTimeout(async () => {
        const params = new URLSearchParams({ amount: creditForm.amount });
        const response = await apiRequest(
          `/accounts/${creditForm.id}/credit-quote?${params.toString()}`,
          "GET",
        );
        if (cancelled) return;
        if (response?.status === "SUCCESS") {
          setCreditQuote(response.data);
        } else {
          setCreditQuoteError(response?.message || "Unable to retrieve a vault credit quote.");
        }
        setCreditQuoteLoading(false);
      }, 350);
    }, 0);

    return () => {
      cancelled = true;
      clearTimeout(resetTimer);
      clearTimeout(quoteTimer);
    };
  }, [creditForm.amount, creditForm.id, creditQuoteRefresh]);

  const handleCreate = async (event) => {
    event.preventDefault();
    setNotice(null);
    if (!accForm.id || !accForm.username || !accForm.email) {
      setNotice({ tone: "error", text: "Enter a username, email, and account number." });
      return;
    }
    if (!ACCOUNT_PATTERN.test(accForm.id)) {
      setNotice({ tone: "error", text: "Account numbers use the format UB-0000." });
      return;
    }

    setBusy(true);
    const res = await apiRequest("/accounts", "POST", {
      accountId: accForm.id,
      username: accForm.username,
      email: accForm.email,
      currency: accForm.currency,
    });
    setBusy(false);

    if (res?.status === "SUCCESS") {
      setNotice({
        tone: "ok",
        text: `Account ${accForm.id} opened. Temporary credentials were sent to ${accForm.email}.`,
      });
      setAccForm({ id: "", username: "", email: "", currency: "INR" });
    } else {
      setNotice({ tone: "error", text: res?.message || "The account could not be opened." });
    }
  };

  const handleCredit = async (event) => {
    event.preventDefault();
    setNotice(null);
    if (!creditForm.id || !creditForm.amount) {
      setNotice({ tone: "error", text: "Enter the destination account and amount." });
      return;
    }
    if (!creditQuote || creditQuoteLoading) {
      setNotice({ tone: "error", text: creditQuoteError || "Wait for a current exchange-rate quote before posting the credit." });
      return;
    }

    setBusy(true);
    const res = await apiRequest(`/accounts/${creditForm.id}/credit`, "POST", {
      amount: parseFloat(creditForm.amount),
      currency: creditQuote.sourceCurrency,
      idempotencyKey: creditForm.key || crypto.randomUUID(),
      quotedExchangeRate: creditQuote.exchangeRate,
      quotedExchangeRateDate: creditQuote.rateDate,
    });
    setBusy(false);

    if (res?.status === "SUCCESS") {
      setNotice({ tone: "ok", text: `Credit posted to ${creditForm.id}.` });
      setCreditForm({ id: "", amount: "", key: "" });
      fetchGlobalLogs();
      fetchVaultBalance();
      setCreditQuote(null);
      setSection("ledger");
    } else {
      setNotice({ tone: "error", text: res?.message || "The credit could not be posted." });
      if (res?.message?.includes("quote changed")) {
        setCreditQuote(null);
        setCreditQuoteRefresh((refresh) => refresh + 1);
      }
    }
  };

  const handleFundVault = async (event) => {
    event.preventDefault();
    setNotice(null);
    if (!vaultFunding.amount) {
      setNotice({ tone: "error", text: "Enter an opening balance amount." });
      return;
    }

    setBusy(true);
    const res = await apiRequest("/admin/vault/fund", "POST", {
      amount: parseFloat(vaultFunding.amount),
      idempotencyKey: vaultFunding.key || crypto.randomUUID(),
    });
    setBusy(false);

    if (res?.status === "SUCCESS") {
      setNotice({ tone: "ok", text: "Opening balance recorded in the system vault." });
      setVaultFunding({ amount: "", key: "" });
      fetchGlobalLogs();
      fetchVaultBalance();
    } else {
      setNotice({ tone: "error", text: res?.message || "Vault funding failed." });
    }
  };

  const titles = {
    vault: ["System vault", "Record an authorised opening balance before crediting customers."],
    accounts: ["Customer accounts", "Provision a verified customer and send temporary sign-in details."],
    ledger: ["Institution ledger", "Every vault movement and customer transfer recorded by the bank."],
    security: ["Profile and security", "Administrator details and password. A forgotten admin password is recovered on the server, not by email."],
  };

  return (
    <AppShell
      title={titles[section][0]}
      subtitle={titles[section][1]}
      nav={NAV}
      active={section}
      onNavigate={(id) => {
        setNotice(null);
        setSection(id);
      }}
    >
      {notice ? <div style={{ marginBottom: 16 }}><Notice tone={notice.tone}>{notice.text}</Notice></div> : null}

      {section === "vault" && (
        <div className="grid-2">
          <section className="account-hero" style={{ marginBottom: 0 }}>
            <div>
              <h2>Vault UB-0000</h2>
              <div className="balance">
                {vaultBalanceLoading
                  ? "…"
                  : vaultBalanceError
                    ? "—"
                    : formatMoney(vaultBalance?.balance, vaultBalance?.currency || "INR")}
              </div>
              {vaultBalanceError ? <p>{vaultBalanceError}</p> : <p>Indian rupee holdings used to fund matching-currency customer credits.</p>}
            </div>
          </section>
          <section className="card">
            <h2>Opening balance</h2>
            <p className="lede-sm">This is an auditable funding entry, not a claim of real cash reserves.</p>
            <form className="form-stack" onSubmit={handleFundVault}>
              <Field label="Amount (INR)" htmlFor="vault-amount">
                <input
                  id="vault-amount"
                  className="input"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={vaultFunding.amount}
                  onChange={(e) => setVaultFunding({ ...vaultFunding, amount: e.target.value })}
                />
              </Field>
              <Field label="Idempotency key" hint="Leave blank to generate one" htmlFor="vault-key">
                <input
                  id="vault-key"
                  className="input"
                  value={vaultFunding.key}
                  onChange={(e) => setVaultFunding({ ...vaultFunding, key: e.target.value })}
                />
              </Field>
              <button className="btn btn-primary" type="submit" disabled={busy}>
                Record opening balance
              </button>
            </form>
          </section>
          <section className="card">
            <h2>Credit a customer</h2>
            <p className="lede-sm">Enter the amount in the vault currency. The customer receives the converted amount in their account currency.</p>
            <form className="form-stack" onSubmit={handleCredit}>
              <Field label="Customer account" htmlFor="credit-id">
                <input
                  id="credit-id"
                  className="input"
                  placeholder="UB-0000"
                  value={creditForm.id}
                  onChange={(e) => setCreditForm({ ...creditForm, id: e.target.value.toUpperCase() })}
                />
              </Field>
              <Field label="Amount (INR)" htmlFor="credit-amount">
                <input
                  id="credit-amount"
                  className="input"
                  type="number"
                  min="0.01"
                  step="0.01"
                  value={creditForm.amount}
                  onChange={(e) => setCreditForm({ ...creditForm, amount: e.target.value })}
                />
              </Field>
              {ACCOUNT_PATTERN.test(creditForm.id) && creditForm.amount ? (
                <div className="quote-box" aria-live="polite">
                  {creditQuoteLoading ? <span>Loading exchange-rate quote…</span> : null}
                  {!creditQuoteLoading && creditQuoteError ? <span className="error-text">{creditQuoteError}</span> : null}
                  {!creditQuoteLoading && creditQuote ? (
                    <>
                      <strong>
                        {formatMoney(creditQuote.sourceAmount, creditQuote.sourceCurrency)}
                        {" → "}
                        {formatMoney(creditQuote.destinationAmount, creditQuote.destinationCurrency)}
                      </strong>
                      <span>
                        1 {creditQuote.sourceCurrency} = {creditQuote.exchangeRate} {creditQuote.destinationCurrency}
                        {" · "}{creditQuote.rateProvider}, dated {creditQuote.rateDate}
                      </span>
                      <span>Reference rate only; the rate is checked again when posted.</span>
                    </>
                  ) : null}
                </div>
              ) : null}
              <Field label="Idempotency key" hint="Leave blank to generate one" htmlFor="credit-key">
                <input
                  id="credit-key"
                  className="input"
                  value={creditForm.key}
                  onChange={(e) => setCreditForm({ ...creditForm, key: e.target.value })}
                />
              </Field>
              <button className="btn btn-primary" type="submit" disabled={busy || creditQuoteLoading || !creditQuote}>
                Post credit
              </button>
            </form>
          </section>
        </div>
      )}

      {section === "accounts" && (
        <section className="card" style={{ maxWidth: 520 }}>
          <h2>Open a customer account</h2>
          <p className="lede-sm">A temporary password is emailed with the verification link.</p>
          <form className="form-stack" onSubmit={handleCreate}>
            <Field label="Username" htmlFor="acc-username">
              <input
                id="acc-username"
                className="input"
                value={accForm.username}
                onChange={(e) => setAccForm({ ...accForm, username: e.target.value })}
              />
            </Field>
            <Field label="Email" htmlFor="acc-email">
              <input
                id="acc-email"
                className="input"
                type="email"
                value={accForm.email}
                onChange={(e) => setAccForm({ ...accForm, email: e.target.value })}
              />
            </Field>
            <Field label="Account number" htmlFor="acc-id">
              <input
                id="acc-id"
                className="input"
                placeholder="UB-0000"
                value={accForm.id}
                onChange={(e) => setAccForm({ ...accForm, id: e.target.value.toUpperCase() })}
              />
            </Field>
            <Field label="Currency" htmlFor="acc-currency">
              <select
                id="acc-currency"
                className="select"
                value={accForm.currency}
                onChange={(e) => setAccForm({ ...accForm, currency: e.target.value })}
              >
                <option value="INR">INR — Indian rupee</option>
                <option value="USD">USD — US dollar</option>
                <option value="CNY">CNY — Chinese yuan</option>
                <option value="JPY">JPY — Japanese yen</option>
              </select>
            </Field>
            <button className="btn btn-primary" type="submit" disabled={busy}>
              Open account
            </button>
          </form>
        </section>
      )}

      {section === "ledger" && (
        <section className="card">
          <h2>All postings</h2>
          <p className="lede-sm">Refreshes every 30 seconds while this page is open.</p>
          {!globalLogs.length ? (
            <p className="empty">No transactions have been recorded yet.</p>
          ) : (
            <div className="table-wrap">
              <table className="data">
                <thead>
                  <tr>
                    <th>Date</th>
                    <th>From</th>
                    <th>To</th>
                    <th>Type</th>
                    <th>Amount</th>
                  </tr>
                </thead>
                <tbody>
                  {globalLogs.map((tx) => (
                    <tr key={tx.id || `${tx.createdAt}-${tx.idempotencyKey}`}>
                      <td>{formatDateTime(tx.createdAt)}</td>
                      <td className="mono">{tx.sourceAccountId}</td>
                      <td className="mono">{tx.destinationAccountId}</td>
                      <td>{tx.type}</td>
                      <td className="mono">
                        {formatMoney(tx.amount, tx.destinationCurrency || "INR")}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}

      {section === "security" && (
        <div className="grid-2">
          <ProfileCard />
          <ChangePasswordCard />
        </div>
      )}
    </AppShell>
  );
}
