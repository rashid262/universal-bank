const LOCALES = { INR: "en-IN", USD: "en-US", CNY: "zh-CN", JPY: "ja-JP" };
const DIGITS = { INR: 2, USD: 2, CNY: 2, JPY: 0 };

export function formatMoney(amount, currency = "INR") {
  const code = currency || "INR";
  return new Intl.NumberFormat(LOCALES[code] || "en-US", {
    style: "currency",
    currency: code,
    minimumFractionDigits: DIGITS[code] ?? 2,
    maximumFractionDigits: DIGITS[code] ?? 2,
  }).format(Number(amount || 0));
}

export function formatDateTime(value) {
  if (!value) return "—";
  return new Intl.DateTimeFormat("en-GB", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}

export function amountStep(currency) {
  return currency === "JPY" ? "1" : "0.01";
}
