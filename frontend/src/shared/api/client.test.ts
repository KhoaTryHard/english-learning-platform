import { http, HttpResponse } from 'msw';
import { server, demoStore } from '../../../tests/setup';
import { DEMO_EMAIL, DEMO_PASSWORD } from '../../mocks/data';
import { api } from './platform';
import { allPages, ApiFailure, getCsrf, request } from './client';

it('rotates CSRF after login and logout and never exposes an access token', async () => {
  demoStore.currentUserId = null;
  const pre = await getCsrf();
  const login = await api.login({ email: DEMO_EMAIL, password: DEMO_PASSWORD });
  expect(login.user.email).toBe(DEMO_EMAIL);
  expect(login).not.toHaveProperty('token');
  expect(await getCsrf()).not.toBe(pre);
  await api.updateProfile('Lê Tiến Hưng');
  expect((await api.profile()).fullName).toBe('Lê Tiến Hưng');
  await api.logout();
  expect(demoStore.currentUserId).toBeNull();
  await expect(api.profile()).rejects.toMatchObject({ status: 401 });
  expect(localStorage.length).toBe(0);
});

it('surfaces 503 with trace and never retries a mutation automatically', async () => {
  let count = 0;
  server.use(
    http.patch('*/api/v1/me/profile', () => {
      count++;
      return HttpResponse.json(
        {
          code: 'DEPENDENCY_UNAVAILABLE',
          message: 'Service unavailable',
          traceId: 'trace-test',
          errors: [],
        },
        { status: 503 },
      );
    }),
  );
  await expect(api.updateProfile(null)).rejects.toMatchObject({
    status: 503,
    traceId: 'trace-test',
  });
  expect(count).toBe(1);
});

it('reads every page, including more than 100 entries', async () => {
  server.use(
    http.get('*/api/v1/paged', ({ request }) => {
      const p = Number(new URL(request.url).searchParams.get('page'));
      return HttpResponse.json({
        items: Array.from({ length: p === 0 ? 100 : 2 }, (_, i) => p * 100 + i),
        page: p,
        size: 100,
        totalElements: 102,
        totalPages: 2,
      });
    }),
  );
  const items = await allPages<number>('/paged');
  expect(items).toHaveLength(102);
  expect(items.at(-1)).toBe(101);
});

it('preserves HTTP 403 separately from unauthenticated 401', async () => {
  server.use(
    http.get('*/api/v1/forbidden', () =>
      HttpResponse.json({ code: 'FORBIDDEN', message: 'No access', errors: [] }, { status: 403 }),
    ),
  );
  let failure: unknown;
  try {
    await request('/forbidden');
  } catch (e) {
    failure = e;
  }
  expect(failure).toBeInstanceOf(ApiFailure);
  expect(failure).toMatchObject({ status: 403, code: 'FORBIDDEN' });
});
