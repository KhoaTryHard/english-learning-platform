import { setupWorker } from 'msw/browser';
import { createDemoStore } from './data';
import type { DemoStore } from './data';
import { createHandlers } from './handlers';

export const DEMO_STORAGE_KEY = 'lt:demo:v1';
export async function startDemo() {
  let store: DemoStore;
  try {
    const stored = sessionStorage.getItem(DEMO_STORAGE_KEY);
    store = stored ? JSON.parse(stored) : await createDemoStore();
  } catch {
    store = await createDemoStore();
  }
  const persist = () => sessionStorage.setItem(DEMO_STORAGE_KEY, JSON.stringify(store));
  persist();
  const worker = setupWorker(...createHandlers(store, persist));
  await worker.start({
    quiet: true,
    onUnhandledRequest(request, print) {
      if (new URL(request.url).pathname.startsWith('/api/')) print.error();
    },
  });
}
