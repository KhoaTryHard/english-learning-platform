import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { demoStore, server } from '../../../tests/setup';
import { courses, demoId } from '../../mocks/data';
import { StatisticsPage } from './StatisticsPage';

it('filters at the owner API, never requests Learner-only Level data, clears Level on Course change', async () => {
  demoStore.currentUserId = demoId(901);
  const queries: URLSearchParams[] = [];
  let forbiddenCalls = 0;
  server.use(
    http.get('*/api/v1/courses/:id/levels', () => {
      forbiddenCalls++;
      return HttpResponse.json({}, { status: 403 });
    }),
    http.get('*/api/v1/admin/statistics', ({ request }) => {
      queries.push(new URL(request.url).searchParams);
      return HttpResponse.json({
        totalLearners: 7,
        courses: [],
        generatedAt: '2026-11-09T12:00:00Z',
        syncState: 'EVENTUAL',
      });
    }),
  );
  const user = userEvent.setup();
  render(<StatisticsPage />);
  await screen.findByText('Chưa có thống kê phù hợp.');
  const level = screen.getByRole('textbox', { name: 'Mã trình độ (tùy chọn)' });
  expect(level).toBeDisabled();
  await user.selectOptions(screen.getByRole('combobox', { name: 'Khóa học' }), courses[0].courseId);
  await user.type(level, demoId(11));
  await user.click(screen.getByRole('button', { name: 'Áp dụng bộ lọc' }));
  await waitFor(() => expect(queries.at(-1)?.get('levelId')).toBe(demoId(11)));
  expect(queries.at(-1)?.get('courseId')).toBe(courses[0].courseId);
  await user.selectOptions(screen.getByRole('combobox', { name: 'Khóa học' }), '');
  expect(level).toHaveValue('');
  await user.click(screen.getByRole('button', { name: 'Áp dụng bộ lọc' }));
  await waitFor(() => expect(queries.at(-1)?.has('courseId')).toBe(false));
  expect(queries.at(-1)?.has('levelId')).toBe(false);
  expect(forbiddenCalls).toBe(0);
  expect(screen.getByText('7')).toBeInTheDocument();
});
