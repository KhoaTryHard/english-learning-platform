import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { demoStore, server } from '../../../tests/setup';
import { api } from '../../shared/api/platform';
import { demoId } from '../../mocks/data';
import { AttemptPage } from './AttemptPage';

function renderAttempt(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/attempts/${id}`]}>
      <Routes>
        <Route path="/attempts/:attemptId" element={<AttemptPage />} />
        <Route path="/results/:resultId" element={<h1>Result from server</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

it('saves a FALSE answer and waits for ACK before submitting', async () => {
  const user = userEvent.setup();
  const attempt = await api.startAttempt(demoId(501), crypto.randomUUID());
  renderAttempt(attempt.attemptId);
  await screen.findByText(/Which sentence/);
  await user.click(screen.getByLabelText('Could I have the menu, please?', { exact: false }));
  await user.click(screen.getByRole('button', { name: /Câu tiếp theo/ }));
  await user.click(screen.getByRole('radio', { name: 'Sai' }));
  await user.click(screen.getByRole('button', { name: 'Nộp bài' }));
  await screen.findByRole('heading', { name: 'Result from server' });
  const result = Object.values(demoStore.results)[0].data;
  expect(result.earnedPoints).toBe(2);
  expect(demoStore.attempts[attempt.attemptId].data.answers[1]).toHaveProperty('value', false);
  expect(Object.values(demoStore.results)).toHaveLength(1);
});

it('does not POST submit when the latest answer write fails', async () => {
  const user = userEvent.setup();
  const attempt = await api.startAttempt(demoId(501), crypto.randomUUID());
  server.use(
    http.put('*/api/v1/attempts/:id/answers', () =>
      HttpResponse.json(
        { code: 'UNAVAILABLE', message: 'Save failed', errors: [] },
        { status: 503 },
      ),
    ),
  );
  renderAttempt(attempt.attemptId);
  await screen.findByText(/Which sentence/);
  await user.click(screen.getByRole('button', { name: 'Nộp bài' }));
  await screen.findByText('Save failed');
  expect(Object.keys(demoStore.results)).toHaveLength(0);
});

it('resumes the same attempt with saved answers and server deadline', async () => {
  const user = userEvent.setup();
  const attempt = await api.startAttempt(demoId(510), crypto.randomUUID());
  await api.answers(attempt.attemptId, [
    { questionId: demoId(601), questionType: 'SINGLE_CHOICE', optionId: demoId(702) },
  ]);
  renderAttempt(attempt.attemptId);
  const radio = await screen.findByRole('radio', { name: /Could I have the menu/ });
  expect(radio).toBeChecked();
  expect(screen.getByRole('timer')).toBeVisible();
  await user.click(screen.getByRole('button', { name: /Câu tiếp theo/ }));
  expect(Object.keys(demoStore.attempts)).toHaveLength(1);
  expect(demoStore.attempts[attempt.attemptId].data.deadlineAt).toBe(attempt.deadlineAt);
});

it('handles a terminal attempt returned by answer-save (200 TIMEOUT)', async () => {
  const user = userEvent.setup();
  const attempt = await api.startAttempt(demoId(510), crypto.randomUUID());
  renderAttempt(attempt.attemptId);
  await screen.findByText(/Which sentence/);
  demoStore.attempts[attempt.attemptId].data.deadlineAt = new Date(Date.now() - 1000).toISOString();
  await user.click(screen.getByRole('radio', { name: /Could I have the menu/ }));
  await screen.findByRole('heading', { name: 'Result from server' });
  await waitFor(() => expect(Object.values(demoStore.results)[0].data.status).toBe('TIMEOUT'));
  expect(Object.values(demoStore.results)[0].data.earnedPoints).toBe(0);
});
