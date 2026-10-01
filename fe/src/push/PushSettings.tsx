import { useEffect, useSyncExternalStore } from 'react'
import { pushService, type PushState } from './pushService.ts'
import { PUSH_STORAGE_KEY } from './pushBrowser.ts'

const labels: Record<PushState['status'], string> = {
  loading: '알림 설정을 확인하는 중…', unsupported: '미지원 환경', default: '알림 권한 미결정',
  off: '권한 허용 · Push 꺼짐', on: 'Push 켜짐', denied: '브라우저에서 차단됨',
  disabled: '서버 Push가 꺼져 있습니다', error: '알림 설정 미완료', 'other-account': '다른 계정에 연결된 브라우저',
}
export function PushSettings() {
  const state = useSyncExternalStore(pushService.subscribe, pushService.getSnapshot, pushService.getSnapshot)
  useEffect(() => {
    void pushService.prepare()
    const refresh = () => { void pushService.prepare() }
    const changed = (event: StorageEvent) => { if (event.key === PUSH_STORAGE_KEY) refresh() }
    window.addEventListener('focus', refresh)
    window.addEventListener('storage', changed)
    return () => { window.removeEventListener('focus', refresh); window.removeEventListener('storage', changed) }
  }, [])
  return <PushSettingsView state={state} enable={() => void pushService.enable()} disable={() => void pushService.disable()} retry={() => void pushService.prepare()} />
}
export function PushSettingsView({ state, enable, disable, retry }: {
  state: PushState; enable: () => void; disable: () => void; retry: () => void
}) {
  return <section className="push-settings" aria-label="이 브라우저 Push 알림" aria-busy={state.busy}>
    <strong>이 브라우저 Push 알림</strong>
    <p role="status">{state.busy ? '알림 설정 처리 중…' : labels[state.status]}</p>
    {state.error && <p className="push-error" role="alert">{state.error}</p>}
    {state.status === 'denied' && <p>브라우저의 사이트 설정에서 알림을 허용한 후 다시 확인해 주세요.</p>}
    {state.status === 'other-account' && <p>이 브라우저는 한 계정의 Push만 받을 수 있습니다. 연결된 계정에서 알림을 끄고 다시 로그인해 주세요.</p>}
    <div className="push-actions">
      {state.canEnable && <button type="button" className="admin-button" disabled={state.busy} onClick={enable}>알림 켜기</button>}
      {state.canDisable && <button type="button" className="admin-button" disabled={state.busy} onClick={disable}>알림 끄기</button>}
      {state.status !== 'unsupported' && <button type="button" className="admin-button" disabled={state.busy} onClick={retry}>상태 다시 확인</button>}
    </div>
    <small>iPhone·iPad는 iOS/iPadOS 16.4 이상에서 홈 화면에 추가한 웹 앱으로 열어 주세요. 브라우저·OS 알림 설정에 따라 수신이 제한될 수 있습니다.</small>
  </section>
}
