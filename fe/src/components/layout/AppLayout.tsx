import type { ReactNode } from 'react'
import { AppSidebar } from './AppSidebar'
import { AppHeader } from './AppHeader'

export function AppLayout({ children }: { children: ReactNode }) {
  return <><a className="skip-link" href="#main-content">본문으로 건너뛰기</a><div className="app-shell"><AppSidebar /><div className="main-area"><AppHeader title="대시보드" subtitle="교육 운영의 흐름을 한눈에 파악하세요" /><main id="main-content" tabIndex={-1}>{children}</main></div></div></>
}
