import { useCallback, useRef, useState } from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { usePreventRemove } from 'expo-router/react-navigation';
import { type LearningDetail, textValue } from '@/learning/api';
import { useLearning } from '@/learning/useLearning';
import { Button } from '@/learning/ui';
import { gradeTime } from '@/coursework/api';
import { employeeStyles as s } from '@/components/employee-styles';
import { canShowCertificate, certificateError } from './api';
import { certificateApi, certificateDownload } from './runtime';

export function CertificateSection({ detail }: { detail: LearningDetail }) {
  return canShowCertificate(detail.status) ? <CompletedCertificate detail={detail} /> : null;
}
function CompletedCertificate({ detail }: { detail: LearningDetail }) {
  const query = useLearning(useCallback(async (signal: AbortSignal) => ({ certificate: await certificateApi.get(detail.enrollmentId, signal) }), [detail]));
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const lock = useRef(false);
  usePreventRemove(busy, () => {});
  async function download() {
    if (lock.current || query.loading) return;
    lock.current = true; setBusy(true); setError('');
    try {
      await query.run(async signal => {
        try { return { certificate: await certificateDownload.run(detail.enrollmentId, signal) }; }
        catch (cause) {
          setError(certificateError(cause));
          // Issuance may have succeeded even if PDF rendering/sharing failed.
          return { certificate: await certificateApi.get(detail.enrollmentId, signal) };
        }
      });
    } finally { lock.current = false; setBusy(false); }
  }
  const certificate = query.data?.certificate;
  return <View style={s.card}><Text style={s.heading}>수료증</Text>
    {query.loading && <ActivityIndicator accessibilityLabel={busy ? '수료증 PDF 준비 중' : '수료증 조회 중'} />}
    {certificate ? <><Text selectable style={s.subtitle}>번호 {textValue(certificate.certificateNumber)}</Text><Text style={s.subtitle}>발급 {gradeTime(certificate.issuedAt)}{'\n'}수료 {gradeTime(certificate.completedAt)}</Text><Text style={s.subtitle}>{textValue(certificate.memberName)} · {textValue(certificate.courseTitle)}</Text></> : !query.loading && !query.error && <Text style={s.subtitle}>PDF를 요청하면 최초 수료증이 발급됩니다.</Text>}
    {!!(error || query.error) && <Text accessibilityRole="alert" style={s.error}>{error || query.error}</Text>}
    {query.error ? <Button title="수료증 다시 조회" disabled={query.loading || busy} onPress={() => { setError(''); void query.reload(); }} /> : <Button title={busy ? 'PDF 준비 중…' : certificate ? 'PDF 다시 받기 / 공유' : '수료증 발급 / PDF 공유'} disabled={query.loading || busy} onPress={() => void download()} />}
    <Text style={s.subtitle}>기기의 공유 창에서 PDF를 열 앱이나 저장 위치를 선택하세요.</Text>
  </View>;
}
