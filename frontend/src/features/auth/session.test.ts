import { http, HttpResponse } from 'msw';
import { server, demoStore } from '../../../tests/setup';
import { demoId } from '../../mocks/data';
import { rememberSession, restoreSession } from './session';

const forbidden = () =>
  HttpResponse.json({ code: 'FORBIDDEN', message: 'No access', errors: [] }, { status: 403 });
it('restores Admin only after an Admin-only API validates the real cookie', async () => {
  const user = demoStore.users[demoId(901)].profile;
  rememberSession(user);
  server.use(http.get('*/api/v1/me/profile', forbidden));
  demoStore.currentUserId = user.userId;
  await expect(restoreSession(new AbortController().signal)).resolves.toMatchObject({
    userId: user.userId,
    role: 'ADMIN',
  });
});
it('does not grant an Admin session from a modified local hint with a Learner cookie', async () => {
  rememberSession(demoStore.users[demoId(901)].profile);
  server.use(http.get('*/api/v1/me/profile', forbidden));
  await expect(restoreSession(new AbortController().signal)).rejects.toMatchObject({ status: 403 });
});
it('clears the Admin hint on logout and never falls back after a session outage', async () => {
  rememberSession(demoStore.users[demoId(901)].profile);
  let calls = 0;
  server.use(
    http.get('*/api/v1/admin/users/:id', () => {
      calls++;
      return forbidden();
    }),
    http.get('*/api/v1/me/profile', () =>
      HttpResponse.json({ code: 'UNAVAILABLE', message: 'Down', errors: [] }, { status: 503 }),
    ),
  );
  await expect(restoreSession(new AbortController().signal)).rejects.toMatchObject({ status: 503 });
  expect(calls).toBe(0);
  rememberSession(null);
  expect(sessionStorage.getItem('linguatrack:admin-hint:v1')).toBeNull();
});
