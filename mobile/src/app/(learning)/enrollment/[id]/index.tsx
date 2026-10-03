import { useCallback } from 'react';
import { Link, useLocalSearchParams } from 'expo-router';
import { Pressable, Text } from 'react-native';
import { employeeStyles as s } from '@/components/employee-styles';
import { contentLabels, editable, routeId, textValue } from '@/learning/api';
import { learningApi, useLearning } from '@/learning/useLearning';
import { Progress, Screen, Status } from '@/learning/ui';
import { ExamSection } from '@/exam/ExamSection';
export default function Detail() {
  const params = useLocalSearchParams<{ id: string }>(); const id = routeId(params.id);
  const load = useCallback((signal: AbortSignal) => id ? learningApi.detail(id, signal) : Promise.reject(new Error('Invalid ID')), [id]);
  const query = useLearning(load), detail = query.data;
  return <Screen {...query}>
    <Link href="/learning" accessibilityRole="link" style={s.link}>내 교육 목록으로</Link>
    {detail && <>
      <Text style={s.title}>{textValue(detail.courseTitle)}</Text><Status status={detail.status} />
      <Text style={s.subtitle}>{detail.courseType === 'MANDATORY' ? '필수 교육' : '선택 교육'} · 마감 {textValue(detail.dueDate)}{'\n'}운영 {textValue(detail.courseStartDate)} ~ {textValue(detail.courseEndDate)}</Text>
      <Progress value={detail.progressRate} /><Text style={s.subtitle}>{textValue(detail.courseDescription)}</Text>
      {!!detail.instructorName && <Text style={s.subtitle}>강사 {detail.instructorName}</Text>}
      {!editable(detail.status) && <Text style={s.subtitle}>종료된 교육입니다. 콘텐츠 진도를 수정할 수 없습니다.</Text>}
      <ExamSection detail={detail} />
      <Text style={s.heading}>학습 콘텐츠</Text>
      {!detail.contents.length && <Text style={s.subtitle}>등록된 콘텐츠가 없습니다.</Text>}
      {detail.contents.map(content => <Link key={content.contentId} href={{ pathname: '/enrollment/[id]/content/[contentId]', params: { id: detail.enrollmentId, contentId: content.contentId } }} asChild><Pressable accessibilityRole="button" style={s.card}>
        <Text style={s.heading}>{textValue(content.title)}</Text><Text style={s.subtitle}>{contentLabels[content.contentType] ?? '콘텐츠'} · {content.required ? '필수' : '선택'} · {content.completedAt ? '학습 완료' : '미완료'}</Text><Progress value={content.progressRate} />
      </Pressable></Link>)}
    </>}
  </Screen>;
}
