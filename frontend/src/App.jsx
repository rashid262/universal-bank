import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom";
import Login from "./pages/Login";
import Register from "./pages/Register";
import AdminDashboard from "./pages/AdminDashboard";
import CustomerDashboard from "./pages/CustomerDashboard";
import VerifySuccess from "./pages/VerifySuccess";
import ForgotPassword from "./pages/ForgotPassword";
import ResetPassword from "./pages/ResetPassword";
import ResendVerification from "./pages/ResendVerification";
import { GuestOnly, RequireAuth } from "./components/RequireAuth";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route
          path="/"
          element={
            <GuestOnly>
              <Login />
            </GuestOnly>
          }
        />
        <Route
          path="/register"
          element={
            <GuestOnly>
              <Register />
            </GuestOnly>
          }
        />
        <Route
          path="/forgot-password"
          element={
            <GuestOnly>
              <ForgotPassword />
            </GuestOnly>
          }
        />
        <Route
          path="/resend-verification"
          element={
            <GuestOnly>
              <ResendVerification />
            </GuestOnly>
          }
        />
        {/* Not guest-only: the emailed link must work even if a session exists in this browser */}
        <Route path="/reset-password" element={<ResetPassword />} />
        <Route path="/verify-success" element={<VerifySuccess />} />
        <Route
          path="/admin"
          element={
            <RequireAuth role="ADMIN">
              <AdminDashboard />
            </RequireAuth>
          }
        />
        <Route
          path="/customer"
          element={
            <RequireAuth role="CUSTOMER">
              <CustomerDashboard />
            </RequireAuth>
          }
        />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
