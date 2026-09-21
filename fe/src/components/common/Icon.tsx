import type { ReactNode } from 'react'

const paths = {
  dashboard: <><rect x="3" y="3" width="7" height="7" rx="1" /><rect x="14" y="3" width="7" height="7" rx="1" /><rect x="3" y="14" width="7" height="7" rx="1" /><rect x="14" y="14" width="7" height="7" rx="1" /></>,
  members: <><circle cx="9" cy="7" r="3" /><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 4a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 5v2" /></>,
  organization: <><rect x="8" y="3" width="8" height="5" rx="1" /><path d="M12 8v5M5 16v-3h14v3" /><rect x="2" y="16" width="6" height="5" rx="1" /><rect x="16" y="16" width="6" height="5" rx="1" /></>,
  briefcase: <><rect x="3" y="7" width="18" height="14" rx="2" /><path d="M8 7V3h8v4M8 7v14M16 7v14" /></>,
  book: <path d="M12 5C9 3 5 3 2 4v15c3-1 7-1 10 1 3-2 7-2 10-1V4c-3-1-7-1-10 1Zm0 0v15" />,
  clipboard: <><rect x="5" y="4" width="14" height="18" rx="2" /><rect x="9" y="2" width="6" height="4" rx="1" /><path d="M9 11h6m-6 5h6" /></>,
  chart: <path d="M3 3v18h18M7 16v-5m5 5V6m5 10v-8" />,
  exam: <path d="M14 2H5v20h10M14 2l5 5h-5V2m2 14 2 2 4-5" />,
  search: <><circle cx="10" cy="10" r="7" /><path d="m15 15 6 6" /></>,
  bell: <path d="M5 10a7 7 0 0 1 14 0c0 7 2 7 2 7H3s2 0 2-7m4 10a3 3 0 0 0 6 0" />,
  logout: <path d="M9 3H4v18h5m5-14 5 5-5 5m-5-5h13" />,
  arrow: <path d="M4 12h16m-6-6 6 6-6 6" />,
} satisfies Record<string, ReactNode>

export type IconName = keyof typeof paths

// 외부 이미지 없이 동일한 크기와 선 두께를 사용하는 outline 아이콘.
export function Icon({ name }: { name: IconName }) {
  return <svg className="icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}
