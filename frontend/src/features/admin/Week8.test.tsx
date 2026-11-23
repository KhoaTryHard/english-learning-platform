import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { demoStore, server } from '../../../tests/setup';
import { demoId } from '../../mocks/data';
import { StatisticsPage } from './StatisticsPage';

beforeEach(() => {
  demoStore.currentUserId = demoId(901);
});

it('shows missing statistics separately from zero and retries a failed source', async () => {
  let calls = 0;
  server.use(
    http.get('*/api/v1/admin/statistics', () => {
      calls++;
      if (calls === 1)
        return HttpResponse.json(
          { code: 'DEPENDENCY_UNAVAILABLE', message: 'Nguồn tạm ngừng' },
          { status: 503 },
        );
      return HttpResponse.json({
        totalLearners: 0,
        generatedAt: '2026-11-09T12:00:00Z',
        syncState: 'EVENTUAL',
        courses: [
          {
            courseId: 'course',
            levelId: null,
            currentLearnerCount: 0,
            averageProgressPercent: null,
            completedLessonCount: 0,
            attemptCounts: { EXERCISE: 0, QUIZ: 0, TEST: 0 },
            averageQuizTestScorePercent: 0,
          },
        ],
      });
    }),
  );
  render(<StatisticsPage />);
  expect(await screen.findByText('Nguồn tạm ngừng')).toBeInTheDocument();
  await userEvent.click(
    within(screen.getByText('Nguồn tạm ngừng').closest('[role="alert"]') as HTMLElement).getByRole(
      'button',
      { name: 'Thử lại' },
    ),
  );
  expect(await screen.findByText('Tiến độ trung bình: Chưa đủ dữ liệu')).toBeInTheDocument();
  expect(screen.getByText('Điểm Quiz/Test trung bình: 0%')).toBeInTheDocument();
});
