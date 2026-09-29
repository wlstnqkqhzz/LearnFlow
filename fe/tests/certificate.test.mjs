import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'
import { AxiosError } from 'axios'
import { apiClient } from '../src/api/client.ts'
import { certificateApi, certificateFilename } from '../src/api/certificateApi.ts'
import { adminErrorMessage } from '../src/api/adminError.ts'
import { downloadCertificate } from '../src/components/certificate/downloadCertificate.ts'

const certificate = { enrollmentId: 3, certificateNumber: 'LF-CERT-0123456789abcdef0123456789abcdef', memberName: '직원', courseTitle: '교육', completedAt: '2026-09-20T16:30:00', issuedAt: '2026-09-29T16:30:00' }
const pdf = new Blob(['%PDF-1.7\ncertificate\n%%EOF'], { type: 'application/pdf' })
function response(config, data, status = 200, headers = {}) { return { config, data, status, statusText: '', headers } }
async function withAdapter(adapter, action) {
  const previous = apiClient.defaults.adapter
  apiClient.defaults.adapter = adapter
  try { await action() } finally { apiClient.defaults.adapter = previous }
}
function browser() {
  const originals = { document: globalThis.document, create: URL.createObjectURL, revoke: URL.revokeObjectURL, timeout: globalThis.setTimeout }
  const saved = [], revoked = [], callbacks = []
  let removed = 0
  globalThis.document = {
    body: { appendChild() {} },
    createElement(tag) {
      assert.equal(tag, 'a')
      return { href: '', download: '', click() { saved.push({ href: this.href, filename: this.download }) }, remove() { removed++ } }
    },
  }
  URL.createObjectURL = blob => { assert.equal(blob, pdf); return 'blob:certificate-test' }
  URL.revokeObjectURL = url => revoked.push(url)
  globalThis.setTimeout = callback => { callbacks.push(callback); return 1 }
  return {
    saved, revoked, callbacks, removed: () => removed,
    restore() {
      if (originals.document === undefined) delete globalThis.document
      else globalThis.document = originals.document
      URL.createObjectURL = originals.create; URL.revokeObjectURL = originals.revoke; globalThis.setTimeout = originals.timeout
    },
  }
}

await test('Controller/DTO에 맞는 PUT, GET 및 blob PDF API', async () => {
  const calls = [], signal = new AbortController().signal
  await withAdapter(async config => {
    calls.push(config)
    return response(config, config.url.endsWith('/pdf') ? pdf : certificate, 200, { 'content-type': 'application/pdf', 'content-disposition': 'attachment; filename="certificate-fixed.pdf"' })
  }, async () => {
    assert.deepEqual(await certificateApi.issue(3), certificate)
    assert.deepEqual(await certificateApi.get(3, signal), certificate)
    assert.deepEqual(await certificateApi.pdf(3), { blob: pdf, filename: 'certificate-fixed.pdf' })
    assert.deepEqual(calls.map(c => [c.method, c.url]), [['put', '/enrollments/3/certificate'], ['get', '/enrollments/3/certificate'], ['get', '/enrollments/3/certificate/pdf']])
    assert.equal(calls[1].signal, signal)
    assert.equal(calls[2].responseType, 'blob')
    assert.equal(calls[0].data, undefined)
  })
})

await test('최초 발급/기존 발급 재다운로드 모두 PUT 다음 GET, Blob 저장 및 URL 해제', async () => {
  const dom = browser(), calls = []
  try {
    await withAdapter(async config => {
      calls.push(config.method)
      return response(config, config.method === 'put' ? certificate : pdf, config.method === 'put' && calls.length === 1 ? 201 : 200,
        { 'content-type': 'application/pdf', 'content-disposition': `attachment; filename="certificate-${certificate.certificateNumber}.pdf"` })
    }, async () => { await downloadCertificate(3); await downloadCertificate(3) })
    assert.deepEqual(calls, ['put', 'get', 'put', 'get'])
    assert.equal(dom.saved.length, 2)
    assert.equal(dom.saved[0].filename, `certificate-${certificate.certificateNumber}.pdf`)
    assert.equal(dom.saved[0].href, 'blob:certificate-test')
    assert.equal(dom.removed(), 2)
    assert.equal(dom.revoked.length, 0)
    dom.callbacks.forEach(callback => callback())
    assert.deepEqual(dom.revoked, ['blob:certificate-test', 'blob:certificate-test'])
  } finally { dom.restore() }
})

await test('진행 중 중복 클릭은 요청/저장 한 번만 수행', async () => {
  const dom = browser(), calls = []
  let release
  const gate = new Promise(resolve => { release = resolve })
  try {
    await withAdapter(async config => {
      calls.push(config.method)
      if (config.method === 'put') await gate
      return response(config, config.method === 'put' ? certificate : pdf, 200, { 'content-type': 'application/pdf' })
    }, async () => {
      const first = downloadCertificate(3), second = downloadCertificate(3)
      assert.equal(first, second)
      release()
      await Promise.all([first, second])
      assert.deepEqual(calls, ['put', 'get'])
      assert.equal(dom.saved.length, 1)
    })
  } finally { dom.restore() }
})

await test('발급 ownership 거부 시 PDF 요청/파일 저장 없이 기존 오류 UI 메시지', async () => {
  const dom = browser(), calls = []
  try {
    await withAdapter(async config => {
      calls.push(config.method)
      throw new AxiosError('private path', undefined, config, undefined, response(config, { code: 'CERTIFICATE_ACCESS_DENIED', message: 'private path' }, 403))
    }, async () => {
      await assert.rejects(downloadCertificate(3), error => {
        assert.match(adminErrorMessage(error), /접근할 권한이 없습니다/)
        assert.doesNotMatch(adminErrorMessage(error), /private path/)
        return true
      })
      assert.deepEqual(calls, ['put']); assert.equal(dom.saved.length, 0)
    })
  } finally { dom.restore() }
})

await test('JSON 오류 Blob은 디코딩해 안전한 메시지로 전달하고 파일 저장 금지, 실패 후 재시도', async () => {
  const dom = browser()
  let fail = true
  try {
    await withAdapter(async config => {
      if (config.method === 'put') return response(config, certificate)
      if (fail) throw new AxiosError('private stack', undefined, config, undefined, response(config,
        new Blob([JSON.stringify({ code: 'CERTIFICATE_PDF_GENERATION_FAILED', message: 'private stack' })], { type: 'application/json' }), 500))
      return response(config, pdf, 200, { 'content-type': 'application/pdf' })
    }, async () => {
      await assert.rejects(downloadCertificate(3), error => {
        assert.match(adminErrorMessage(error), /PDF를 생성하지 못했습니다/)
        assert.doesNotMatch(adminErrorMessage(error), /private stack/)
        return true
      })
      assert.equal(dom.saved.length, 0)
      fail = false
      await downloadCertificate(3)
      assert.equal(dom.saved.length, 1)
      assert.equal(dom.saved[0].filename, 'certificate-download.pdf')
    })
  } finally { dom.restore() }
})

await test('PDF GET ownership JSON Blob도 403과 코드 보존', async () => {
  await withAdapter(async config => { throw new AxiosError('', undefined, config, undefined, response(config,
    new Blob(['{"code":"CERTIFICATE_ACCESS_DENIED"}'], { type: 'application/json' }), 403)) }, async () => {
    await assert.rejects(certificateApi.pdf(3), error => error.response.status === 403 && /권한/.test(adminErrorMessage(error)))
  })
})

for (const [name, blob, mime] of [
  ['200 JSON', new Blob(['{"code":"CERTIFICATE_NOT_FOUND"}'], { type: 'application/json' }), 'application/json'],
  ['위장 PDF JSON', new Blob(['{"code":"CERTIFICATE_NOT_FOUND"}'], { type: 'application/pdf' }), 'application/pdf'],
  ['빈 PDF', new Blob([], { type: 'application/pdf' }), 'application/pdf'],
  ['HTML', new Blob(['<html>error</html>']), 'text/html'],
  ['PDF 잘못된 MIME', pdf, 'text/html'],
]) await test(`${name} 응답은 파일 저장 전에 차단`, async () => {
  await withAdapter(async config => response(config, blob, 200, { 'content-type': mime }), async () => {
    await assert.rejects(certificateApi.pdf(3), error => {
      assert.ok(adminErrorMessage(error)); assert.doesNotMatch(adminErrorMessage(error), /<html>/); return true
    })
  })
})

await test('Content-Disposition 일반/UTF-8 파일명과 안전한 fallback', () => {
  assert.equal(certificateFilename('attachment; filename="certificate-123.pdf"'), 'certificate-123.pdf')
  assert.equal(certificateFilename("attachment; filename*=UTF-8''%EC%88%98%EB%A3%8C%EC%A6%9D.pdf"), '수료증.pdf')
  for (const header of [undefined, '', 'attachment; filename="../../x.pdf"', 'attachment; filename="evil.exe"', 'attachment; filename="x.exe.pdf"', "attachment; filename*=UTF-8''%ZZ.pdf"])
    assert.equal(certificateFilename(header), 'certificate-download.pdf')
})

const server = await createServer({ server: { middlewareMode: true }, appType: 'custom' })
try {
  const { LearningSummary } = await server.ssrLoadModule('/src/pages/employee/LearningDetailPage.tsx')
  const { EnrollmentTable } = await server.ssrLoadModule('/src/pages/admin/EnrollmentsPage.tsx')
  const { CertificateDownloadView } = await server.ssrLoadModule('/src/components/certificate/CertificateDownload.tsx')
  const render = value => renderToStaticMarkup(value)
  const enrollment = { ...certificate, memberId: 2, courseType: 'MANDATORY', courseStartDate: '2026-09-01', courseEndDate: '2026-09-30', dueDate: '2026-09-30', assignedAt: '2026-09-01T00:00:00', assignmentSource: 'MANUAL' }
  await test('직원 상세/ADMIN 수강 현황은 COMPLETED일 때만 공통 다운로드 버튼 노출', () => {
    for (const status of ['COMPLETED', 'ASSIGNED', 'IN_PROGRESS', 'FAILED', 'EXPIRED']) {
      const value = { ...enrollment, status }
      assert.equal(render(h(LearningSummary, { detail: value })).includes('수료증 다운로드'), status === 'COMPLETED')
      assert.equal(render(h(EnrollmentTable, { enrollments: [value], onDetail() {} })).includes('수료증 다운로드'), status === 'COMPLETED')
    }
  })
  await test('준비 중 disabled, 오류 alert와 재시도 버튼, 성공 안내', () => {
    const props = { pending: false, error: '', success: '', onDownload() {} }
    const pending = render(h(CertificateDownloadView, { ...props, pending: true }))
    assert.match(pending, /disabled=""/); assert.match(pending, /aria-busy="true"/); assert.match(pending, /수료증 준비 중/)
    const failed = render(h(CertificateDownloadView, { ...props, error: '다운로드 실패' }))
    assert.match(failed, /role="alert"/); assert.match(failed, /다운로드 재시도/); assert.doesNotMatch(failed, /disabled=/)
    assert.match(render(h(CertificateDownloadView, { ...props, success: '다운로드를 요청했습니다.' })), /role="status"/)
  })
} finally { await server.close() }
