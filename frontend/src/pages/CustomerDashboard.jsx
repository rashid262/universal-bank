import { useCallback, useEffect, useState } from "react";
import { apiRequest } from "../api";
import AppShell from "../components/AppShell";
import ChangePasswordCard from "../components/ChangePasswordCard";
import ProfileCard from "../components/ProfileCard";
import Field from "../components/Field";
import Notice from "../components/Notice";
import { amountStep, formatDateTime, formatMoney } from "../lib/money";
import { getSession } from "../lib/session";

const NAV = [
  { id: "overview", label: "Overview" },
  { id: "payments", label: "Payments" },
  { id: "security", label: "Profile" },
];

export default function CustomerDashboard() {
  const { accountId, username } = getSession();
  const [section, setSection] = useState("overview");
  const [accountData, setAccountData] = useState({
    balance: 0,
    currency: "INR",
    description: "",
    accountId,
  });
  const [transactions, setTransactions] = useState([]);
  const [loadError, setLoadError] = useState("");
  const [transfer, setTransfer] = useState({ recipientId: "", amount: "", note: "" });
  const [transferQuote, setTransferQuote] = useState(null);
  const [quoteLoading, setQuoteLoading] = useState(false);
  const [quoteError, setQuoteError] = useState("");
  const [transferNotice, setTransferNotice] = useState(null);

  const fetchDashboardData = useCallback(async () => {
    if (!accountId) return;
    const balanceRes = await apiRequest(`/accounts/${accountId}/balance`, "GET");
    const historyRes = await apiRequest(`/accounts/${accountId}/transactions`, "GET");

    if (balanceRes?.status === "SUCCESS") {
      setAccountData(balanceRes.data);
      setLoadError("");
    } else {
      setLoadError(balanceRes?.message || "Unable to load your account.");
    }

    if (historyRes?.status === "SUCCESS") {
      setTransactions(historyRes.data || []);
    }
  }, [accountId]);

  useEffect(() => {
    const initialFetch = setTimeout(fetchDashboardData, 0);
    return () => clearTimeout(initialFetch);
  }, [fetchDashboardData]);

  useEffect(() => {
    const amount = Number(transfer.amount);
    let cancelled = false;
    let debounceTimer;
    const validRequest =
      /^UB-\d{4}$/.test(transfer.recipientId) &&
      Number.isFinite(amount) &&
      amount > 0;

    const resetTimer = setTimeout(() => {
      if (cancelled) return;
      setTransferQuote(null);
      setQuoteError("");
      if (!validRequest) {
        setQuoteLoading(false);
        return;
      }

      setQuoteLoading(true);
      debounceTimer = setTimeout(async () => {
        const params = new URLSearchParams({
          destinationAccountId: transfer.recipientId,
          amount: transfer.amount,
        });
        const response = await apiRequest(`/transactions/quote?${params.toString()}`, "GET");
        if (cancelled) return;
        if (response?.status === "SUCCESS") {
          setTransferQuote(response.data);
        } else {
          setQuoteError(response?.message || "Unable to retrieve an exchange-rate quote.");
        }
        setQuoteLoading(false);
      }, 350);
    }, 0);

    return () => {
      cancelled = true;
      clearTimeout(resetTimer);
      clearTimeout(debounceTimer);
    };
  }, [transfer.amount, transfer.recipientId]);

  const handleTransfer = async (event) => {
    event.preventDefault();
    setTransferNotice(null);

    if (transfer.recipientId === accountId) {
      setTransferNotice({ tone: "error", text: "You cannot transfer to your own account." });
      return;
    }
    if (!transferQuote || quoteLoading) {
      setTransferNotice({
        tone: "error",
        text: quoteError || "Wait for a valid quote before confirming the payment.",
      });
      return;
    }

    const res = await apiRequest("/transactions/transfer", "POST", {
      sourceAccountId: accountId,
      destinationAccountId: transfer.recipientId,
      amount: parseFloat(transfer.amount),
      note: transfer.note,
      idempotencyKey: crypto.randomUUID(),
      quotedExchangeRate: transferQuote.exchangeRate,
      quotedExchangeRateDate: transferQuote.rateDate,
    });

    if (res?.status === "SUCCESS") {
      setTransfer({ recipientId: "", amount: "", note: "" });
      setTransferQuote(null);
      setTransferNotice({ tone: "ok", text: "Payment sent." });
      fetchDashboardData();
      setSection("overview");
    } else {
      setTransferNotice({ tone: "error", text: res?.message || "The transfer could not be completed." });
      if (res?.message?.includes("quote changed")) {
        setTransferQuote(null);
      }
    }
  };

  const currency = accountData.currency || "INR";

  return (
    <AppShell
      title={
        section === "payments"
          ? "Send a payment"
          : section === "security"
            ? "Profile and security"
            : `Welcome back, ${username || "Customer"}`
      }
      subtitle={
        section === "overview"
          ? "Here's your account at a glance."
          : section === "payments"
            ? "Payments leave your own account. Cross-currency amounts are quoted before you confirm."
            : "Your account details and the password used to sign in to online banking."
      }
      nav={NAV}
      active={section}
      onNavigate={setSection}
    >
      {loadError ? <Notice tone="error">{loadError}</Notice> : null}

      {section === "overview" && (
        <div className="stack">
          <section className="account-hero">
            <div>
              <h2>Available balance</h2>
              <div className="balance">{formatMoney(accountData.balance, currency)}</div>
              <p className="muted">{accountData.description || "Personal current account"}</p>
            </div>
            <div className="hero-meta" aria-label="Account information">
              <div className="hero-meta-item">
                <span>Account number</span>
                <strong className="hero-meta-value mono">{accountData.accountId || accountId}</strong>
              </div>
              <div className="hero-meta-item">
                <span>Currency</span>
                <strong className="hero-meta-value currency-value">{currency}</strong>
              </div>
            </div>
          </section>

          <section className="card">
            <h2>Recent activity</h2>
            <p className="lede-sm">Credits and payments posted to this account.</p>
            <TransactionTable transactions={transactions} accountId={accountId} fallbackCurrency={currency} />
          </section>
        </div>
      )}

      {section === "payments" && (
        <div className="grid-2">
          <section className="card">
            <h2>New payment</h2>
            <p className="lede-sm">Enter a Universal Bank account number. The amount is taken from {accountId}.</p>
            <form className="form-stack" onSubmit={handleTransfer}>
              {transferNotice ? <Notice tone={transferNotice.tone}>{transferNotice.text}</Notice> : null}
              <Field label="Recipient account" htmlFor="recipient">
                <input
                  id="recipient"
                  className="input"
                  placeholder="UB-0000"
                  value={transfer.recipientId}
                  onChange={(e) => setTransfer({ ...transfer, recipientId: e.target.value.toUpperCase() })}
                  required
                />
              </Field>
              <Field label={`Amount (${currency})`} htmlFor="amount">
                <input
                  id="amount"
                  className="input"
                  type="number"
                  min="0"
                  step={amountStep(currency)}
                  value={transfer.amount}
                  onChange={(e) => setTransfer({ ...transfer, amount: e.target.value })}
                  required
                />
              </Field>

              {transfer.amount && transfer.recipientId ? (
                <div className="quote-box" aria-live="polite">
                  {quoteLoading && <p>Requesting a reference rate…</p>}
                  {!quoteLoading && quoteError && <p className="field-error">{quoteError}</p>}
                  {!quoteLoading && transferQuote && (
                    <>
                      <strong>
                        {formatMoney(transferQuote.sourceAmount, transferQuote.sourceCurrency)}
                        {" → "}
                        {formatMoney(transferQuote.destinationAmount, transferQuote.destinationCurrency)}
                      </strong>
                      <p>
                        1 {transferQuote.sourceCurrency} = {transferQuote.exchangeRate} {transferQuote.destinationCurrency}
                      </p>
                      <p className="muted">
                        {transferQuote.rateProvider}, dated {transferQuote.rateDate}. This rate is confirmed again when you send.
                      </p>
                    </>
                  )}
                </div>
              ) : null}

              <Field label="Reference (optional)" htmlFor="note">
                <input
                  id="note"
                  className="input"
                  value={transfer.note}
                  onChange={(e) => setTransfer({ ...transfer, note: e.target.value })}
                  disabled={quoteLoading || !transferQuote}
                />
              </Field>
              <button
                className="btn btn-primary"
                type="submit"
                disabled={quoteLoading || !transferQuote}
              >
                Confirm payment
              </button>
            </form>
          </section>
          <section className="card">
            <h2>Before you send</h2>
            <p className="lede-sm">
              Recipients must be verified customers. Same-currency payments use a rate of 1. Different currencies use a daily
              reference rate from Frankfurter, not a live tradable quote.
            </p>
            <p className="muted">
              Available to send: <strong className="mono">{formatMoney(accountData.balance, currency)}</strong>
            </p>
          </section>
        </div>
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

function TransactionTable({ transactions, accountId, fallbackCurrency }) {
  if (!transactions.length) {
    return <p className="empty">No payments have posted yet.</p>;
  }

  return (
    <div className="table-wrap">
      <table className="data">
        <thead>
          <tr>
            <th>Date</th>
            <th>Details</th>
            <th>Amount</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((tx) => {
            const isCredit = tx.destinationAccountId === accountId;
            const displayAmount = isCredit ? tx.amount : tx.sourceAmount || tx.amount;
            const displayCurrency = isCredit
              ? tx.destinationCurrency || fallbackCurrency
              : tx.sourceCurrency || fallbackCurrency;
            const title =
              tx.type === "TRANSFER"
                ? isCredit
                  ? `From ${tx.sourceAccountId}`
                  : `To ${tx.destinationAccountId}`
                : tx.destinationDescription || tx.sourceDescription || tx.type;
            return (
              <tr key={tx.id || `${tx.createdAt}-${tx.idempotencyKey}`}>
                <td>{formatDateTime(tx.createdAt)}</td>
                <td>
                  <div>{title}</div>
                  {(tx.destinationDescription || tx.sourceDescription) && (
                    <div className="muted">{isCredit ? tx.destinationDescription : tx.sourceDescription}</div>
                  )}
                  {tx.exchangeRateProvider && tx.sourceCurrency !== tx.destinationCurrency && (
                    <div className="muted">
                      {formatMoney(tx.sourceAmount, tx.sourceCurrency)} → {formatMoney(tx.amount, tx.destinationCurrency)}
                      {" · "}
                      {tx.exchangeRateProvider} ({tx.exchangeRateDate})
                    </div>
                  )}
                </td>
                <td className={isCredit ? "credit" : "debit"}>
                  {isCredit ? "+" : "−"}
                  {formatMoney(displayAmount, displayCurrency)}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
