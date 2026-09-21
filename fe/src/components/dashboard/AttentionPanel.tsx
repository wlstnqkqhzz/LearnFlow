import { attentionItems } from '../../data/dashboardMockData'

export function AttentionPanel() {
  return (
    <section aria-labelledby="attention-title">
      <div className="section-heading"><div><h2 id="attention-title">확인이 필요한 수강</h2><p className="section-description">조치가 필요한 항목</p></div></div>
      <ul className="attention-list">
        {attentionItems.map((item) => (
          <li key={item.label} className="attention-row surface">
            <span className={`attention-marker tone-${item.tone}`} aria-hidden="true"><i /></span>
            <div><h3>{item.label}</h3><p>{item.description}</p></div>
            <strong className={`attention-count tone-${item.tone}`}>{item.count}<span className="sr-only">건</span></strong>
          </li>
        ))}
      </ul>
    </section>
  )
}
