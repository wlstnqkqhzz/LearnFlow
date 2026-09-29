import type { EnrollmentStatus } from '../../api/assignmentTypes.ts'
import { useAction } from '../../hooks/useAction.ts'
import { Feedback } from '../admin/AdminUI.tsx'
import { downloadCertificate } from './downloadCertificate.ts'

export function CertificateDownloadView({ pending, error, success, onDownload }: {
  pending: boolean; error: string; success: string; onDownload: () => void
}) {
  return <div>
    <button type="button" className="admin-button" disabled={pending} aria-busy={pending} onClick={onDownload}>
      {pending ? '수료증 준비 중…' : error ? '수료증 다운로드 재시도' : '수료증 다운로드'}
    </button>
    <Feedback error={error} success={success} />
  </div>
}

export function CertificateDownload({ enrollmentId, status }: { enrollmentId: number; status: EnrollmentStatus }) {
  const action = useAction()
  if (status !== 'COMPLETED') return null
  return <CertificateDownloadView pending={action.pending} error={action.error} success={action.success}
    onDownload={() => { void action.run(() => downloadCertificate(enrollmentId), '수료증 다운로드를 요청했습니다.') }} />
}
