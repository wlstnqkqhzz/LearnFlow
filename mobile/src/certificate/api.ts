import { type AxiosInstance, isAxiosError } from 'axios';
export type Certificate = { enrollmentId: number; certificateNumber: string; memberName: string; courseTitle: string; completedAt: string; issuedAt: string };
export const canShowCertificate = (status: string) => status === 'COMPLETED';
export class CertificateError extends Error {}
export function certificateError(error: unknown) {
  if (error instanceof CertificateError) return error.message;
  if (isAxiosError(error)) {
    if (error.response?.status === 403) return '이 수료증에 접근할 권한이 없습니다.';
    if (error.response?.status === 409) return '수료 상태를 확인해 주세요. 수료한 교육에서만 발급할 수 있습니다.';
    if (!error.response) return '서버에 연결하지 못했습니다. 네트워크를 확인하고 다시 시도해 주세요.';
  }
  return '수료증을 준비하지 못했습니다. 잠시 후 다시 시도해 주세요.';
}
export function validatePdf(data: unknown, contentType: unknown): Uint8Array {
  if (typeof contentType !== 'string' || contentType.split(';')[0].trim().toLowerCase() !== 'application/pdf') throw new CertificateError('PDF가 아닌 응답을 받았습니다. 다시 시도해 주세요.');
  const bytes = data instanceof ArrayBuffer ? new Uint8Array(data) : data instanceof Uint8Array ? data : null;
  if (!bytes || bytes.length < 12 || bytes.length > 20 * 1024 * 1024 || String.fromCharCode(...bytes.subarray(0, 5)) !== '%PDF-' || !/%%EOF\s*$/.test(String.fromCharCode(...bytes.subarray(Math.max(0, bytes.length - 1024))))) throw new CertificateError('정상적인 PDF 파일을 받지 못했습니다. 다시 시도해 주세요.');
  return bytes;
}
export function createCertificateApi(client: AxiosInstance) {
  return {
    async get(id: number, signal?: AbortSignal): Promise<Certificate | null> {
      try { return (await client.get<Certificate>(`/enrollments/${id}/certificate`, { signal })).data; }
      catch (error) { if (isAxiosError<{ code?: string }>(error) && error.response?.status === 404 && error.response.data?.code === 'CERTIFICATE_NOT_FOUND') return null; throw error; }
    },
    async issue(id: number, signal?: AbortSignal) { return (await client.put<Certificate>(`/enrollments/${id}/certificate`, undefined, { signal })).data; },
    async pdf(id: number, signal?: AbortSignal) {
      const response = await client.get<ArrayBuffer>(`/enrollments/${id}/certificate/pdf`, { responseType: 'arraybuffer', headers: { Accept: 'application/pdf' }, signal });
      return validatePdf(response.data, response.headers['content-type']);
    },
  };
}
export type PdfFile = { uri: string; remove(): Promise<void> };
export type PdfPorts = { available(): Promise<boolean>; clean(): Promise<void>; save(id: number, bytes: Uint8Array): Promise<PdfFile>; share(uri: string): Promise<void> };
export function createCertificateDownload(api: ReturnType<typeof createCertificateApi>, ports: PdfPorts) {
  let active: Promise<Certificate> | null = null;
  let activeId: number | null = null;
  return {
    run(id: number, signal?: AbortSignal): Promise<Certificate> {
      if (active) return activeId === id ? active : Promise.reject(new CertificateError('다른 수료증을 준비 중입니다. 완료 후 다시 시도해 주세요.'));
      const check = () => { if (signal?.aborted) throw new CertificateError('수료증 요청이 취소되었습니다.'); };
      activeId = id;
      active = (async () => {
        let file: PdfFile | null = null;
        try {
          check();
          if (!await ports.available()) throw new CertificateError('이 기기에서는 PDF 공유를 사용할 수 없습니다. PDF를 지원하는 앱과 기기 설정을 확인해 주세요.');
          check(); await ports.clean();
          const certificate = await api.issue(id, signal);
          const bytes = await api.pdf(id, signal); check();
          file = await ports.save(id, bytes); check();
          await ports.share(file.uri);
          // Keep the file after chooser dismissal: the receiving app may still be reading it.
          return certificate;
        } catch (error) {
          if (file) await file.remove().catch(() => {});
          throw error;
        }
      })();
      const current = active;
      void current.then(() => { active = null; activeId = null; }, () => { active = null; activeId = null; });
      return current;
    },
  };
}
