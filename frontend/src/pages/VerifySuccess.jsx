import { Link } from "react-router-dom";
import Wordmark from "../components/Wordmark";

export default function VerifySuccess() {
  return (
    <div className="auth-split">
      <aside className="auth-panel">
        <Wordmark inverted />
        <p className="lede">Your account is confirmed. Online banking is ready when you are.</p>
        <p className="panel-foot">Keep your password private. Universal Bank will never ask for it by email.</p>
      </aside>
      <div className="auth-form-wrap">
        <div className="auth-card">
          <h1>Verified</h1>
          <p className="subtitle">
            You can now sign in and send transfers from your own account. Recipients must also be verified customers.
          </p>
          <Link className="btn btn-primary btn-block" to="/" style={{ textDecoration: "none" }}>
            Go to sign in
          </Link>
        </div>
      </div>
    </div>
  );
}
