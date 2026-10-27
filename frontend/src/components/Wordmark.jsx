export default function Wordmark({ inverted = false }) {
  return (
    <div className="mark" aria-label="Universal Bank">
      <span className="mark-mono" style={inverted ? { color: "#c4a574", borderColor: "#c4a574" } : undefined}>
        UB
      </span>
      <span className="mark-name">
        Universal Bank
        <small>Est. online banking</small>
      </span>
    </div>
  );
}
