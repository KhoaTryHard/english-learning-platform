import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { server } from '../../../tests/setup';
import { courses, demoId } from '../../mocks/data';
import { LearningProvider } from '../learning-setup/LearningProvider';
import { HistoryPage, ProgressPage } from './ProgressPages';

function show(page: React.ReactNode) {
  return render(
    <MemoryRouter>
      <LearningProvider>{page}</LearningProvider>
    </MemoryRouter>,
  );
}

it('keeps a null denominator distinct from zero and refreshes server progress', async () => {
  let calls = 0;
  server.use(
    http.get('*/api/v1/me/progress', () => {
      calls++;
      return HttpResponse.json({
        courseId: courses[0].courseId,
        completedLessons: 0,
        totalLessons: calls === 1 ? 0 : 4,
        progressPercent: calls === 1 ? null : 0,
        generatedAt: '2026-11-09T12:00:00Z',
        syncState: 'EVENTUAL',
      });
    }),
  );
  show(<ProgressPage />);
  expect(await screen.findByText('Chưa có bài học để tính tỷ lệ.')).toBeInTheDocument();
  expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  await userEvent.click(screen.getByRole('button', { name: 'Làm mới tiến độ' }));
  expect(await screen.findByText('0/4 bài học hoàn thành')).toBeInTheDocument();
  expect(screen.getByRole('progressbar')).toHaveAttribute('value', '0');
});

it('loads every history page, retains retakes and old course/level snapshots', async () => {
  const currentCourse = courses[0].courseId;
  const oldCourse = demoId(888),
    oldLevel = demoId(889);
  const requests: number[] = [];
  server.use(
    http.get('*/api/v1/me/history', ({ request }) => {
      const page = Number(new URL(request.url).searchParams.get('page') || 0);
      requests.push(page);
      return HttpResponse.json({
        items: [
          {
            historyId: demoId(800 + page),
            submissionId: demoId(810 + page),
            resultId: demoId(820 + page),
            assessmentId: demoId(830),
            assessmentType: 'EXERCISE',
            courseId: page ? oldCourse : currentCourse,
            levelIdAtStart: page ? oldLevel : demoId(11),
            lessonId: demoId(840),
            earnedPoints: 0,
            maxPoints: 1,
            scorePercent: 0,
            completedAt: '2026-11-09T12:00:00Z',
          },
        ],
        page,
        size: 1,
        totalElements: 2,
        totalPages: 2,
      });
    }),
  );
  show(<HistoryPage />);
  expect(await screen.findByText(oldLevel)).toBeInTheDocument();
  expect(requests).toEqual([0, 1]);
  expect(screen.getAllByRole('link', { name: '0/1 · 0% →' })).toHaveLength(2);
  await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Lọc khóa học' }), oldCourse);
  expect(screen.getAllByRole('link', { name: '0/1 · 0% →' })).toHaveLength(1);
  expect(screen.getByRole('link', { name: '0/1 · 0% →' })).toHaveAttribute(
    'href',
    `/results/${demoId(821)}`,
  );
});

it('does not turn a failed progress source into zero and retries explicitly', async () => {
  let failed = true;
  server.use(
    http.get('*/api/v1/me/progress', () =>
      failed
        ? HttpResponse.json(
            { code: 'DEPENDENCY_UNAVAILABLE', message: 'Nguồn tiến độ tạm ngừng' },
            { status: 503 },
          )
        : HttpResponse.json({
            courseId: courses[0].courseId,
            completedLessons: 1,
            totalLessons: 4,
            progressPercent: 25,
            generatedAt: '2026-11-09T12:00:00Z',
            syncState: 'EVENTUAL',
          }),
    ),
  );
  show(<ProgressPage />);
  expect(await screen.findByText('Nguồn tiến độ tạm ngừng')).toBeInTheDocument();
  expect(screen.queryByRole('progressbar')).not.toBeInTheDocument();
  failed = false;
  await userEvent.click(screen.getByRole('button', { name: 'Thử lại' }));
  expect(await screen.findByText('1/4 bài học hoàn thành')).toBeInTheDocument();
});
