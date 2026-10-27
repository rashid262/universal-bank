const KEYS = ["bank_token", "user", "account_id", "bank_role"];

export function getSession() {
  return {
    token: localStorage.getItem("bank_token"),
    username: localStorage.getItem("user"),
    accountId: localStorage.getItem("account_id"),
    role: localStorage.getItem("bank_role"),
  };
}

export function saveSession({ token, username, accountId, role }) {
  localStorage.setItem("bank_token", token);
  localStorage.setItem("user", username);
  localStorage.setItem("account_id", accountId || "");
  localStorage.setItem("bank_role", role || "");
}

export function updateToken(token) {
  localStorage.setItem("bank_token", token);
}

export function clearSession() {
  KEYS.forEach((key) => localStorage.removeItem(key));
}

export function homeForRole(role) {
  return role === "ADMIN" ? "/admin" : "/customer";
}
