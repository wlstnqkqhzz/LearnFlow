import axios, { AxiosError } from 'axios'
import { apiClient } from './client.ts'
import type { Certificate } from './certificateTypes.ts'

async function errorBody(blob: Blob): Promise<{ code: string }> {
  // 오류 본문만 제한적으로 읽고 서버 message/내부 정보는 노출하지 않는다.
  if (blob.size <= 65536) {
    try {
      const body: unknown = JSON.parse(await blob.text())
      if (typeof body === 'object' && body !== null && 'code' in body && typeof body.code === 'string') return { code: body.code }
    } catch { /* JSON이 아닌 응답도 안전한 오류 메시지로 처리 */ }
  }
  return { code: 'CERTIFICATE_INVALID_PDF' }
}

export function certificateFilename(disposition: unknown): string {
  const fallback = 'certificate-download.pdf'
  if (typeof disposition !== 'string') return fallback
  const extended = /(?:^|;)\s*filename\*\s*=\s*UTF-8''([^;]+)/i.exec(disposition)
  const plain = /(?:^|;)\s*filename\s*=\s*(?:"([^"]*)"|([^;]+))/i.exec(disposition)
  let name = plain?.[1] ?? plain?.[2] ?? ''
  if (extended) {
    try { name = decodeURIComponent(extended[1].trim()) } catch { return fallback }
  }
  name = name.trim()
  // 경로/제어문자/이중 확장자 및 실행 파일명은 사용하지 않는다.
  return /^[\p{L}\p{N}_ -]{1,120}\.pdf$/iu.test(name) ? name : fallback
}

export const certificateApi = {
  async issue(id: number) { return (await apiClient.put<Certificate>(`/enrollments/${id}/certificate`)).data },
  async get(id: number, signal?: AbortSignal) { return (await apiClient.get<Certificate>(`/enrollments/${id}/certificate`, { signal })).data },
  async pdf(id: number) {
    try {
      const response = await apiClient.get<Blob>(`/enrollments/${id}/certificate/pdf`, { responseType: 'blob' })
      const blob = response.data
      const mime = String(response.headers['content-type'] ?? (blob instanceof Blob ? blob.type : '')).split(';')[0].trim().toLowerCase()
      if (!(blob instanceof Blob) || mime !== 'application/pdf' || await blob.slice(0, 5).text() !== '%PDF-') {
        const data = blob instanceof Blob ? await errorBody(blob) : { code: 'CERTIFICATE_INVALID_PDF' }
        throw new AxiosError('Invalid certificate PDF', undefined, response.config, undefined, { ...response, data })
      }
      return { blob, filename: certificateFilename(response.headers['content-disposition']) }
    } catch (error) {
      if (axios.isAxiosError<unknown>(error) && error.response?.data instanceof Blob) {
        error.response.data = await errorBody(error.response.data)
      }
      throw error
    }
  },
}
