import { Directory, File, Paths } from 'expo-file-system';
import * as Sharing from 'expo-sharing';
import { Platform } from 'react-native';
import { apiClient } from '@/auth/runtime';
import { CertificateError, createCertificateApi, createCertificateDownload, validatePdf } from './api';

export const certificateApi = createCertificateApi(apiClient);
const folder = () => new Directory(Paths.cache, 'learnflow-certificates');
const retentionMs = 24 * 60 * 60 * 1000;
export const certificateDownload = createCertificateDownload(certificateApi, {
  available: async () => Platform.OS !== 'web' && await Sharing.isAvailableAsync(),
  async clean() {
    try {
      const directory = folder();
      directory.create({ intermediates: true, idempotent: true });
      for (const entry of directory.list()) {
        if (!(entry instanceof File)) continue;
        const match = /^certificate-\d+-(\d+)\.pdf$/.exec(entry.name);
        if (match && Date.now() - Number(match[1]) > retentionMs) entry.delete();
      }
    } catch { throw new CertificateError('앱 파일 저장소를 준비하지 못했습니다. 저장 공간을 확인해 주세요.'); }
  },
  async save(id, bytes) {
    const file = new File(folder(), `certificate-${id}-${Date.now()}.pdf`);
    try {
      file.create(); file.write(bytes);
      if (file.size !== bytes.length) throw new Error('Incomplete file');
      validatePdf(await file.bytes(), 'application/pdf');
      return { uri: file.uri, remove: async () => { if (file.exists) file.delete(); } };
    } catch {
      try { if (file.exists) file.delete(); } catch { /* The next cache cleanup retries removal. */ }
      throw new CertificateError('PDF 파일을 저장하지 못했습니다. 저장 공간을 확인하고 다시 시도해 주세요.');
    }
  },
  async share(uri) {
    try { await Sharing.shareAsync(uri, { mimeType: 'application/pdf', UTI: 'com.adobe.pdf', dialogTitle: 'LearnFlow 수료증 열기 / 공유' }); }
    catch { throw new CertificateError('PDF를 열거나 공유하지 못했습니다. PDF를 지원하는 앱을 확인하고 다시 시도해 주세요.'); }
  },
});
