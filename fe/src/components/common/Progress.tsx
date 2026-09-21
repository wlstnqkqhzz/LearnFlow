function percentage(value: number) {
  return Number.isFinite(value) ? Math.min(100, Math.max(0, value)) : 0
}

export function ProgressBar({ value, label }: { value: number; label: string }) {
  const progress = percentage(value)
  return <div className="progress-track" role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress}><span style={{ width: `${progress}%` }} /></div>
}

export function CircularProgress({ value, label }: { value: number; label: string }) {
  const progress = percentage(value)
  return <div className="circular-progress" role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress}>
    <svg viewBox="0 0 100 100" aria-hidden="true"><circle className="ring-track" cx="50" cy="50" r="42" /><circle className="ring-value" cx="50" cy="50" r="42" pathLength="100" strokeDasharray={`${progress} 100`} /></svg>
    <div aria-hidden="true"><strong>{progress}%</strong><span>수료율</span></div>
  </div>
}
