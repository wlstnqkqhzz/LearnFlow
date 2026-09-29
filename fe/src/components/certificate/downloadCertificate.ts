import { certificateApi } from '../../api/certificateApi.ts'

// 행/화면 간에도 동일 수강의 진행 중 요청을 공유한다. 완료/실패 후 재시도 가능.
const flights = new Map<number, Promise<void>>()

export function downloadCertificate(enrollmentId: number): Promise<void> {
  const existing = flights.get(enrollmentId)
  if (existing) return existing
  const promise = (async () => {
    await certificateApi.issue(enrollmentId)
    const { blob, filename } = await certificateApi.pdf(enrollmentId)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    try {
      anchor.href = url
      anchor.download = filename
      document.body.appendChild(anchor)
      anchor.click()
    } finally {
      anchor.remove()
      // 브라우저가 다운로드를 시작할 시간을 확보한 뒤 메모리를 해제한다.
      setTimeout(() => URL.revokeObjectURL(url), 1000)
    }
  })()
  flights.set(enrollmentId, promise)
  const clear = () => { flights.delete(enrollmentId) }
  void promise.then(clear, clear)
  return promise
}
