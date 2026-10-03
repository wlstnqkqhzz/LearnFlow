import { StyleSheet } from 'react-native';
export const employeeStyles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#f3f7fc' },
  content: { padding: 24, paddingBottom: 100, gap: 20, width: '100%', maxWidth: 560, alignSelf: 'center', flexGrow: 1 },
  brand: { fontSize: 20, fontWeight: '800', color: '#146baf', marginTop: 16 },
  title: { fontSize: 30, lineHeight: 40, fontWeight: '700', color: '#15334b' },
  subtitle: { fontSize: 14, lineHeight: 22, color: '#52677b' },
  card: { backgroundColor: 'white', padding: 22, borderRadius: 20, borderWidth: 1, borderColor: '#dfe8f0', gap: 14 },
  heading: { fontSize: 20, fontWeight: '700', color: '#15334b' },
  label: { fontSize: 14, fontWeight: '600', color: '#15334b' },
  input: { borderColor: '#cbd9e6', borderWidth: 1, borderRadius: 10, minHeight: 48, padding: 12, fontSize: 16, color: '#15334b', backgroundColor: '#fff' },
  button: { minHeight: 50, backgroundColor: '#146baf', borderRadius: 12, justifyContent: 'center', alignItems: 'center', padding: 14 },
  buttonText: { color: 'white', fontWeight: '700', fontSize: 16 },
  secondary: { padding: 14, alignItems: 'center' },
  link: { color: '#146baf', fontWeight: '600', minHeight: 32 },
  error: { color: '#aa2634', fontSize: 14, lineHeight: 22 },
  email: { fontSize: 16, color: '#15334b', flexShrink: 1 },
});
