import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import { createServer } from 'vite'

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const { AuthContext } = await server.ssrLoadModule('/src/auth/AuthContext.ts')
  const { AppSidebar } = await server.ssrLoadModule('/src/components/layout/AppSidebar.tsx')
  const { EmployeeSidebar } = await server.ssrLoadModule('/src/components/layout/EmployeeSidebar.tsx')
  const { AppHeader } = await server.ssrLoadModule('/src/components/layout/AppHeader.tsx')
  const { RoleRoute } = await server.ssrLoadModule('/src/routes/RoleRoute.tsx')
  const { ProtectedRoute } = await server.ssrLoadModule('/src/routes/ProtectedRoute.tsx')
  const user = { memberId: 1, email: 'qa@learnflow.local', roles: ['EMPLOYEE', 'ADMIN'] }
  const auth = { user, isAuthenticated: true, isInitializing: false, login() {}, async logout() {} }
  const render = (child, path, state = auth) => renderToStaticMarkup(h(MemoryRouter, { initialEntries: [path] }, h(AuthContext.Provider, { value: state }, child)))

  await test('시험 상세 직접 URL은 시험 관리 한 항목만 현재 메뉴로 표시한다', () => {
    const html = render(h(AppSidebar), '/admin/courses/72/exam')
    assert.equal((html.match(/aria-current="page"/g) ?? []).length, 1)
    assert.match(html, /<a[^>]*aria-current="page"[^>]*href="\/admin\/exams"/)
    assert.doesNotMatch(html, /<a[^>]*aria-current="page"[^>]*href="\/admin\/courses"/)
  })
  await test('과정 상세와 회원 상세의 상위 메뉴를 정확하게 표시한다', () => {
    for (const [path, menu] of [['/admin/courses/72', 'courses'], ['/admin/members/5', 'members']]) {
      const html = render(h(AppSidebar), path)
      assert.equal((html.match(/aria-current="page"/g) ?? []).length, 1)
      assert.match(html, new RegExp(`<a[^>]*aria-current="page"[^>]*href="/admin/${menu}"`))
    }
  })
  await test('직원 아이콘 메뉴에도 접근 가능한 이름이 있고 관리자 메뉴가 없다', () => {
    const html = render(h(EmployeeSidebar), '/employee/learning/7', { ...auth, user: { ...user, roles: ['EMPLOYEE'] } })
    assert.match(html, /aria-label="내 교육"/)
    assert.doesNotMatch(html, /\/admin\//)
  })
  await test('Header에 동작하지 않는 검색 입력창이나 준비 중 문구가 없다', () => {
    const html = render(h(AppHeader, { title: '대시보드' }), '/admin/dashboard')
    assert.doesNotMatch(html, /type="search"|준비 중/)
    assert.match(html, /알림, 읽지 않은 알림/)
  })
  await test('인증 복원 중에는 관리자 자식 화면을 렌더링하지 않는다', () => {
    const child = h(Routes, null, h(Route, { element: h(ProtectedRoute) }, h(Route, { path: '*', element: h('p', null, 'PRIVATE_ADMIN_DATA') })))
    const html = render(child, '/admin/dashboard', { ...auth, isInitializing: true })
    assert.doesNotMatch(html, /PRIVATE_ADMIN_DATA/)
    assert.match(html, /role="status"/)
  })
  await test('EMPLOYEE가 관리자 URL에 직접 접근해도 관리자 내용을 먼저 렌더링하지 않는다', () => {
    const child = h(Routes, null, h(Route, { element: h(RoleRoute, { role: 'ADMIN' }) }, h(Route, { path: '*', element: h('p', null, 'PRIVATE_ADMIN_DATA') })))
    const html = render(child, '/admin/dashboard', { ...auth, user: { ...user, roles: ['EMPLOYEE'] } })
    assert.doesNotMatch(html, /PRIVATE_ADMIN_DATA/)
  })
} finally { await server.close() }
