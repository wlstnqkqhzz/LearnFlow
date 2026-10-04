export type Subscription = { subscriptionId: number; platform: 'ANDROID' | 'IOS'; enabled: boolean; version: number; updatedAt: string };
export type Installation = { installationId: string; installationSecret: string; bindingGeneration: number; bindingMemberId: number | null; binding: Subscription | null; memberId: number | null; optedInMember: number | null };
export type InstallationStorage = { read(): Promise<string | null>; write(value: string): Promise<void> };

export function createInstallationStore(storage: InstallationStorage, random: () => { id: string; secret: string }) {
  let cached: Installation | null = null;
  let queue: Promise<unknown> = Promise.resolve();
  function serialize<T>(operation: () => Promise<T>): Promise<T> {
    const next = queue.then(operation); queue = next.catch(() => {}); return next;
  }
  async function read() {
    if (cached) return cached;
    const raw = await storage.read();
    if (raw) {
      const value: Installation = JSON.parse(raw);
      if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(value.installationId) || !/^[A-Za-z0-9_-]{43,128}$/.test(value.installationSecret)) throw new Error('설치 보안 정보를 읽을 수 없습니다.');
      if (value.bindingGeneration === undefined) { value.bindingGeneration = 0; value.bindingMemberId = null; }
      if (!Number.isSafeInteger(value.bindingGeneration) || value.bindingGeneration < 0 || (value.bindingMemberId !== null && (!Number.isSafeInteger(value.bindingMemberId) || value.bindingMemberId <= 0))) throw new Error('설치 Binding 정보를 읽을 수 없습니다.');
      cached = value; return value;
    }
    const generated = random();
    const value: Installation = { installationId: generated.id, installationSecret: generated.secret, bindingGeneration: 0, bindingMemberId: null, binding: null, memberId: null, optedInMember: null };
    await storage.write(JSON.stringify(value)); cached = value; return value;
  }
  return {
    get: () => serialize(read),
    beginBinding: (memberId: number, valid: () => boolean) => serialize(async () => {
      const current = await read();
      if (!valid()) throw new Error('cancelled');
      if (current.bindingMemberId === memberId) return current;
      const bindingGeneration = current.bindingGeneration + 1;
      if (!Number.isSafeInteger(bindingGeneration)) throw new Error('Binding generation exhausted');
      const value = { ...current, bindingGeneration, bindingMemberId: memberId, optedInMember: null };
      // Persist intent before any network request, including retries after process restart.
      await storage.write(JSON.stringify(value)); cached = value; return value;
    }),
    update: (patch: Partial<Pick<Installation, 'binding' | 'memberId' | 'optedInMember' | 'bindingMemberId'>>) => serialize(async () => {
      const value = { ...await read(), ...patch }; await storage.write(JSON.stringify(value)); cached = value; return value;
    }),
  };
}
