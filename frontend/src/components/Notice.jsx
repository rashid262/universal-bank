export default function Notice({ tone = "info", children }) {
  if (!children) return null;
  return (
    <div className={`notice notice--${tone}`} role="status">
      {children}
    </div>
  );
}
