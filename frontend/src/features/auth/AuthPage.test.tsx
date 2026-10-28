import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { App } from '../../app/App';
import { demoStore } from '../../../tests/setup';
import { DEMO_EMAIL, DEMO_PASSWORD } from '../../mocks/data';

it('restores a protected URL after login without implicit registration login', async () => {
  demoStore.currentUserId = null;
  window.history.replaceState({}, '', '/profile');
  const user = userEvent.setup();
  render(<App />);
  await user.type(await screen.findByLabelText('Email'), DEMO_EMAIL);
  await user.type(screen.getByLabelText('Mật khẩu', { exact: true }), DEMO_PASSWORD);
  await user.click(screen.getByRole('button', { name: /Đăng nhập/ }));
  await screen.findByRole('heading', { name: 'Hồ sơ học viên' });
  expect(window.location.pathname).toBe('/profile');
});
