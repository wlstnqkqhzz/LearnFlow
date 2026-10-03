import { useCallback, useState } from 'react';
import { Link } from 'expo-router';
import { Pressable, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { employeeStyles as s } from '@/components/employee-styles';
import { type EnrollmentStatus, statusLabels, textValue } from '@/learning/api';
import { learningApi, useLearning } from '@/learning/useLearning';
import { Button, Progress, Screen, Status } from '@/learning/ui';
export default function Learning() {
  const [status, setStatus] = useState<EnrollmentStatus | undefined>();
  const [page, setPage] = useState(0);
  const load = useCallback((signal: AbortSignal) => learningApi.list(status, page, signal), [status, page]);
  const query = useLearning(load);
  return <SafeAreaView style={{ flex: 1 }} edges={['top']}><Screen {...query}>
    <Text style={s.title}>내 교육</Text>
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
      {([undefined, ...Object.keys(statusLabels)] as (EnrollmentStatus | undefined)[]).map(value => <Pressable key={value ?? 'ALL'} accessibilityRole="button" accessibilityState={{ selected: status === value }} onPress={() => { setStatus(value); setPage(0); }} style={{ padding: 12, minHeight: 48, borderRadius: 10, backgroundColor: status === value ? '#146baf' : '#e6f1fa' }}><Text style={{ color: status === value ? 'white' : '#15334b' }}>{value ? statusLabels[value] : '전체'}</Text></Pressable>)}
    </View>
    {!query.loading && query.data?.content.length === 0 && <Text style={s.subtitle}>해당 조건의 교육이 없습니다.</Text>}
    {query.data?.content.map(row => <Link key={row.enrollmentId} href={{ pathname: '/enrollment/[id]', params: { id: row.enrollmentId } }} asChild><Pressable accessibilityRole="button" style={s.card}>
      <Text style={s.heading}>{textValue(row.courseTitle)}</Text><Text style={s.subtitle}>{row.courseType === 'MANDATORY' ? '필수 교육' : '선택 교육'}</Text><Status status={row.status} />
      <Progress value={row.progressRate} />{row.progressUnavailable && <Text style={s.subtitle}>진도를 불러오지 못했습니다. 당겨서 새로고침하거나 상세에서 다시 확인해 주세요.</Text>}
      <Text style={s.subtitle}>운영 {textValue(row.courseStartDate)} ~ {textValue(row.courseEndDate)}{'\n'}학습 마감 {textValue(row.dueDate)}</Text>
    </Pressable></Link>)}
    {!!query.data && <View style={{ gap: 10 }}><Text style={s.subtitle}>{query.data.totalPages ? `${query.data.page + 1} / ${query.data.totalPages} 페이지` : '0건'}</Text><Button title="이전 페이지" disabled={query.loading || page === 0} onPress={() => setPage(page - 1)} /><Button title="다음 페이지" disabled={query.loading || page + 1 >= query.data.totalPages} onPress={() => setPage(page + 1)} /></View>}
  </Screen></SafeAreaView>;
}
