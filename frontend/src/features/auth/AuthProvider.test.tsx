import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { demoStore, server } from '../../../tests/setup';
import { demoId } from '../../mocks/data';
import { AuthProvider, useAuth } from './AuthProvider';
import { rememberSession } from './session';

function Identity() {
  const { user } = useAuth();
  return <p>{user?.role || 'anonymous'}</p>;
}
it('can retry an Admin session check after 503 without losing its non-secret hint', async () => {
  const admin = demoStore.users[demoId(901)].profile;
  rememberSession(admin);
  let requests = 0;
  server.use(
    http.get('*/api/v1/me/profile', () =>
      HttpResponse.json(
        { code: 'FORBIDDEN', message: 'Learner only', errors: [] },
        { status: 403 },
      ),
    ),
    http.get('*/api/v1/admin/users/:id', () =>
      ++requests === 1
        ? HttpResponse.json(
            { code: 'UNAVAILABLE', message: 'Session service down', errors: [] },
            { status: 503 },
          )
        : HttpResponse.json(admin),
    ),
  );
  const user = userEvent.setup();
  render(
    <AuthProvider>
      <Identity />
    </AuthProvider>,
  );
  await screen.findByText('Dịch vụ đang tạm gián đoạn.');
  await user.click(screen.getByRole('button', { name: 'Thử lại' }));
  await screen.findByText('ADMIN');
  expect(requests).toBe(2);
});
