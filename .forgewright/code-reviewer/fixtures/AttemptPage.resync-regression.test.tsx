import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { demoStore, server } from '../../../tests/setup';
import { api } from '../../shared/api/platform';
import { demoId } from '../../mocks/data';
import { AttemptEditor } from './AttemptPage';

it('keeps a failed local answer visibly unsaved after a successful status GET', async () => {
  const user = userEvent.setup();
  const attempt = await api.startAttempt(demoId(501), crypto.randomUUID());
  server.use(http.put('*/api/v1/attempts/:id/answers', () =>
    HttpResponse.json({ code: 'UNAVAILABLE', message: 'Save failed', errors: [] }, { status: 503 }),
  ));
  render(<MemoryRouter><AttemptEditor initial={attempt} /></MemoryRouter>);
  const answer = screen.getByRole('radio', { name: /Could I have the menu/ });
  await user.click(answer);
  await screen.findByText('Save failed');
  expect(demoStore.attempts[attempt.attemptId].data.answers).toHaveLength(0);
  await user.click(screen.getByRole('button', { name: 'Kiểm tra trạng thái máy chủ' }));
  await waitFor(() => expect(screen.queryByText('Save failed')).not.toBeInTheDocument());
  expect(answer).toBeChecked();
  expect(demoStore.attempts[attempt.attemptId].data.answers).toHaveLength(0);
  expect(screen.queryByText('Đã tải/lưu đáp án từ máy chủ.')).not.toBeInTheDocument();
  expect(screen.getByText('Đáp án mới chưa lưu.')).toBeVisible();
});
