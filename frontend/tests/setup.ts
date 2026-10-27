import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { webcrypto } from 'node:crypto';
import { TextDecoder, TextEncoder } from 'node:util';
import { setupServer } from 'msw/node';
import { createDemoStore, demoId } from '../src/mocks/data';
import type { DemoStore } from '../src/mocks/data';
import { createHandlers } from '../src/mocks/handlers';
import { resetCsrf } from '../src/shared/api/client';

Object.defineProperty(globalThis, 'crypto', { value: webcrypto });
Object.assign(globalThis, { TextEncoder, TextDecoder });
window.scrollTo = () => undefined;
export const server = setupServer();
export let demoStore: DemoStore;
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
beforeEach(async () => {
  resetCsrf();
  sessionStorage.clear();
  demoStore = await createDemoStore();
  demoStore.currentUserId = demoId(900);
  server.use(...createHandlers(demoStore));
});
afterEach(() => {
  cleanup();
  server.resetHandlers();
});
afterAll(() => server.close());
