import { test } from 'node:test';
import assert from 'node:assert/strict';
import { create, AxiosError } from 'axios';
import { canShowCertificate, certificateError, createCertificateApi, createCertificateDownload, validatePdf } from '../src/certificate/api.ts';
import { createAuthSession } from '../src/auth/session.ts';
const certificate = { enrollmentId: 7, certificateNumber: 'LF-2026-001', memberName: '직원 스냅샷', courseTitle: '교육 스냅샷', completedAt: '2026-10-03T00:00:00', issuedAt: '2026-10-03T01:00:00' };
const bytes = new TextEncoder().encode('%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n');
const response = (config, data, type = 'application/json') => ({ config, data, status: 200, statusText: 'OK', headers: { 'content-type': type } });
const fail = (c, status, code) => new AxiosError('request failed', '', c, null, { ...response(c, { code }), status });
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
function setup() {
  const client = create(); let issued = false; const calls = [];
  client.defaults.adapter = async c => {
    calls.push(c);
    if(c.method === 'put') { issued = true; return response(c, certificate); }
    if(c.url.endsWith('/pdf')) { assert.equal(c.responseType, 'arraybuffer'); assert.equal(c.headers.get('Accept'), 'application/pdf'); return response(c, bytes.buffer, 'application/pdf'); }
    if(!issued) throw fail(c, 404, 'CERTIFICATE_NOT_FOUND'); return response(c, certificate);
  };
  const events = [];
  const ports = { available: async () => true, clean: async () => { events.push('clean'); }, save: async (id, data) => { assert.equal(id, 7); assert.deepEqual(data, bytes); events.push('save'); return { uri: 'file:///cache/certificate.pdf', remove: async () => { events.push('remove'); } }; }, share: async uri => { assert.equal(uri, 'file:///cache/certificate.pdf'); events.push('share'); } };
  const api = createCertificateApi(client); return { client, api, calls, events, ports };
}
test('only server COMPLETED exposes certificate actions', () => {
  assert.equal(canShowCertificate('COMPLETED'), true);
  for(const value of ['ASSIGNED', 'IN_PROGRESS', 'FAILED', 'EXPIRED', '', undefined]) assert.equal(canShowCertificate(value), false);
});
test('first GET is unissued; PUT issues; subsequent GET preserves snapshot', async () => {
  const { api, calls } = setup(); assert.equal(await api.get(7), null); assert.deepEqual(await api.issue(7), certificate); assert.deepEqual(await api.get(7), certificate);
  assert.deepEqual(calls.map(c => [c.method, c.url]), [['get', '/enrollments/7/certificate'], ['put', '/enrollments/7/certificate'], ['get', '/enrollments/7/certificate']]);
});
test('redownload retains number, dates and snapshots', async () => {
  const { api, ports } = setup(); const download = createCertificateDownload(api, ports);
  const first = await download.run(7), second = await download.run(7); assert.deepEqual(first, second); assert.deepEqual(first, certificate);
});
test('PDF is validated before save/share; successful share keeps file for recipient', async () => {
  const { api, ports, events } = setup(); await createCertificateDownload(api, ports).run(7); assert.deepEqual(events, ['clean', 'save', 'share']);
});
test('invalid mime, JSON disguised as PDF and truncated PDF are rejected', () => {
  assert.throws(() => validatePdf(bytes, 'application/json'));
  assert.throws(() => validatePdf(new TextEncoder().encode('{"error":"denied"}'), 'application/pdf'));
  assert.throws(() => validatePdf(new TextEncoder().encode('%PDF-1.7\ntruncated'), 'application/pdf'));
  assert.throws(() => validatePdf({ error: 'denied' }, 'application/pdf'));
  assert.deepEqual(validatePdf(bytes.buffer, 'application/pdf; charset=binary'), bytes);
});
test('200 JSON error response never reaches file save', async () => {
  const { api, client, ports, events } = setup(); client.defaults.adapter = async c => response(c, c.method === 'put' ? certificate : { error: 'failed' });
  await assert.rejects(createCertificateDownload(api, ports).run(7)); assert.deepEqual(events, ['clean']);
});
test('duplicate downloads share a single issue/PDF/share operation', async () => {
  const { api, calls, ports } = setup(), gate = deferred(); ports.share = async () => gate.promise;
  const download = createCertificateDownload(api, ports), first = download.run(7), second = download.run(7); assert.equal(first, second);
  await assert.rejects(download.run(8)); gate.resolve(); await first; assert.equal(calls.filter(c => c.method === 'put').length, 1); assert.equal(calls.filter(c => c.url.endsWith('/pdf')).length, 1);
});
for(const status of [403, 409, 500]) test(`HTTP ${status} fails without writing file and allows retry`, async () => {
  const { api, client, ports, events } = setup(); const adapter = client.defaults.adapter;
  client.defaults.adapter = async c => { throw fail(c, status); }; const download = createCertificateDownload(api, ports);
  await assert.rejects(download.run(7)); assert.deepEqual(events, ['clean']); client.defaults.adapter = adapter; assert.deepEqual(await download.run(7), certificate);
});
test('unrelated 404 is not treated as unissued certificate', async () => {
  const { api, client } = setup(); client.defaults.adapter = async c => { throw fail(c, 404, 'ENROLLMENT_NOT_FOUND'); }; await assert.rejects(api.get(7));
});
test('network failure never writes file', async () => {
  const { api, client, ports, events } = setup(); client.defaults.adapter = async () => { throw new AxiosError('offline'); };
  await assert.rejects(createCertificateDownload(api, ports).run(7)); assert.deepEqual(events, ['clean']); assert.match(certificateError(new AxiosError('offline')), /네트워크/);
});
test('PDF generation failure retains issued certificate for retry', async () => {
  const { api, client, ports, events } = setup(), adapter = client.defaults.adapter;
  client.defaults.adapter = async c => { if(c.url.endsWith('/pdf')) throw fail(c, 500, 'CERTIFICATE_PDF_GENERATION_FAILED'); return adapter(c); };
  await assert.rejects(createCertificateDownload(api, ports).run(7)); assert.deepEqual(await api.get(7), certificate); assert.deepEqual(events, ['clean']);
});
test('file storage failure does not invoke sharing', async () => {
  const { api, ports, events } = setup(); ports.save = async () => { throw new Error('disk full'); };
  await assert.rejects(createCertificateDownload(api, ports).run(7)); assert.deepEqual(events, ['clean']);
});
test('unavailable sharing fails before issue and download', async () => {
  const { api, ports, calls } = setup(); ports.available = async () => false;
  await assert.rejects(createCertificateDownload(api, ports).run(7), /공유/); assert.equal(calls.length, 0);
});
test('sharing failure cleans temporary PDF', async () => {
  const { api, ports, events } = setup(); ports.share = async () => { throw new Error('no handler'); };
  await assert.rejects(createCertificateDownload(api, ports).run(7)); assert.deepEqual(events, ['clean', 'save', 'remove']);
});
test('leaving during file save prevents external share and cleans file', async () => {
  const { api, ports, events } = setup(); const controller = new AbortController(), save = ports.save;
  ports.save = async (...args) => { const file = await save(...args); controller.abort(); return file; };
  await assert.rejects(createCertificateDownload(api, ports).run(7, controller.signal)); assert.deepEqual(events, ['clean', 'save', 'remove']);
});
test('PDF uses authenticated Axios with rotating refresh and retry', async () => {
  let stored; const auth = createAuthSession({ read: async () => stored ?? null, write: async v => { stored = v; }, remove: async () => { stored = null; } }, 'https://example.com/api');
  const tokens = n => ({ accessToken: `a.${Buffer.from(JSON.stringify({ memberId: 1, email: 'a@example.com', roles: ['EMPLOYEE'], tokenType: 'ACCESS', exp: 9999999999, n })).toString('base64url')}.b`, refreshToken: `r${n}`, tokenType: 'Bearer', accessTokenExpiresInSeconds: 100, refreshTokenExpiresInSeconds: 100 });
  let refreshes = 0; auth.publicClient.defaults.adapter = async c => { if(c.url.endsWith('/refresh')) { refreshes++; assert.equal(JSON.parse(c.data).refreshToken, 'r0'); } return response(c, tokens(refreshes)); }; await auth.login('a@example.com', 'password');
  auth.client.defaults.adapter = async c => { assert.equal(c.url, '/enrollments/7/certificate/pdf'); assert.equal(c.params, undefined); assert.equal(c.responseType, 'arraybuffer'); if(!c.authRetried) { assert.equal(c.headers.get('Authorization'), `Bearer ${tokens(0).accessToken}`); throw fail(c, 401); } assert.equal(c.headers.get('Authorization'), `Bearer ${tokens(1).accessToken}`); return response(c, bytes.buffer, 'application/pdf'); };
  assert.deepEqual(await createCertificateApi(auth.client).pdf(7), bytes); assert.equal(refreshes, 1); assert.equal(JSON.parse(stored).refreshToken, 'r1');
});
