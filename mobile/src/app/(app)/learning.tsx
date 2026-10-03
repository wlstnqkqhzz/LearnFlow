import { Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { employeeStyles as s } from '@/components/employee-styles';
export default function LearningPlaceholder() { return <SafeAreaView style={s.screen}><View style={s.content}><Text style={s.brand}>LearnFlow</Text><Text style={s.title}>내 교육</Text><View style={s.card}><Text style={s.heading}>교육 목록을 준비하고 있습니다.</Text><Text style={s.subtitle}>배정된 교육 조회와 학습 기능은 다음 단계에서 제공됩니다.</Text></View></View></SafeAreaView>; }
