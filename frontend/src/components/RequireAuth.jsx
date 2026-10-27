import { Navigate } from "react-router-dom";
import { getSession, homeForRole } from "../lib/session";

export function RequireAuth({ role, children }) {
  const session = getSession();
  if (!session.token) {
    return <Navigate to="/" replace />;
  }
  if (role && session.role !== role) {
    return <Navigate to={homeForRole(session.role)} replace />;
  }
  return children;
}

export function GuestOnly({ children }) {
  const session = getSession();
  if (session.token && session.role) {
    return <Navigate to={homeForRole(session.role)} replace />;
  }
  return children;
}
