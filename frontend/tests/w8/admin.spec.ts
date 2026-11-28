import { expect, test } from '@playwright/test';

test('Assessment authoring and Statistics error/filter/keyboard states (MOCK contract)', async ({
  page,
}, info) => {
  const id = (n: number) => `10000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
  const profile = {
    userId: id(901),
    role: 'ADMIN',
    status: 'ACTIVE',
    email: 'admin.w8@example.com',
    fullName: 'Admin W8 MOCK',
  };
  let loggedIn = false,
    statsDown = false;
  const detail = {
    assessmentId: id(50),
    type: 'EXERCISE',
    courseId: id(1),
    lessonId: id(3),
    title: 'Exercise browser MOCK',
    timeLimitSeconds: null,
    ready: false,
    questionCount: 0,
    questions: [] as object[],
  };
  const queries: string[] = [];
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request(),
      url = new URL(req.url()),
      p = url.pathname.replace('/api/v1', '');
    let data: unknown,
      status = 200;
    const paged = (items: object[]) => ({
      items,
      page: 0,
      size: 100,
      totalElements: items.length,
      totalPages: items.length ? 1 : 0,
    });
    if (p === '/me/profile') {
      status = loggedIn ? 403 : 401;
      data = { code: 'AUTH_REQUIRED', message: 'Admin session fixture' };
    } else if (p === '/auth/csrf') data = { csrfToken: 'w8-mock-csrf' };
    else if (p === '/auth/login') {
      loggedIn = true;
      data = { user: profile };
    } else if (p.startsWith('/admin/users/')) data = profile;
    else if (p === '/admin/courses')
      data = paged([{ courseId: id(1), name: 'Khóa W8 MOCK', published: true }]);
    else if (p === '/admin/lessons')
      data = paged([
        {
          courseId: id(1),
          moduleId: id(4),
          lessonId: id(3),
          title: 'Bài W8 MOCK',
          content: 'Body',
          orderNo: 1,
          published: false,
        },
      ]);
    else if (p === '/admin/assessments') data = paged([detail]);
    else if (p.endsWith('/questions') && req.method() === 'POST') {
      expect(req.headers()['x-csrf-token']).toBe('w8-mock-csrf');
      const body = req.postDataJSON();
      expect(body.correctAnswer).toBe(false);
      const q = { ...body, questionId: id(51) };
      detail.questions = [q];
      detail.questionCount = 1;
      data = q;
      status = 201;
    } else if (p === `/admin/assessments/${id(50)}`) data = detail;
    else if (p === '/admin/statistics') {
      queries.push(url.search);
      if (statsDown) {
        status = 503;
        data = { code: 'DEPENDENCY_UNAVAILABLE', message: 'Nguồn MOCK tạm ngừng' };
      } else
        data = {
          totalLearners: 7,
          generatedAt: '2026-11-09T12:00:00Z',
          syncState: 'EVENTUAL',
          courses: [
            {
              courseId: id(1),
              levelId: url.searchParams.get('levelId'),
              currentLearnerCount: 2,
              averageProgressPercent: null,
              completedLessonCount: 1,
              attemptCounts: { EXERCISE: 2, QUIZ: 1, TEST: 0 },
              averageQuizTestScorePercent: 0,
            },
          ],
        };
    } else {
      status = 404;
      data = { code: 'FIXTURE_MISSING', message: p };
    }
    await route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) });
  });
  await page.goto('/login');
  await page.getByLabel('Email', { exact: true }).fill(profile.email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill('BrowserFixture2026!');
  await page.getByRole('button', { name: /Đăng nhập/ }).click();
  await expect(page.getByRole('heading', { name: 'Chăm chút từng bài học.' })).toBeVisible();
  await page.goto('/admin/assessments');
  await page.getByRole('button', { name: `Xem / sửa ${detail.title}` }).click();
  await page.getByRole('button', { name: 'Thêm câu hỏi', exact: true }).click();
  await page.getByLabel('Loại câu hỏi').selectOption('TRUE_FALSE');
  await page.getByLabel('Nội dung câu hỏi').fill('False stays false');
  await page.getByRole('combobox', { name: 'Đáp án đúng', exact: true }).selectOption('false');
  await page.getByRole('button', { name: 'Lưu câu hỏi' }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByText('Câu hỏi (1)')).toBeVisible();
  await page.screenshot({ path: info.outputPath('assessment.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.goto('/admin/statistics');
  await expect(page.getByText('Tiến độ trung bình: Chưa đủ dữ liệu')).toBeVisible();
  await page.getByRole('combobox', { name: 'Khóa học', exact: true }).selectOption(id(1));
  await page.getByRole('textbox', { name: 'Mã trình độ (tùy chọn)' }).fill(id(2));
  await page.getByRole('button', { name: 'Áp dụng bộ lọc' }).click();
  await expect.poll(() => queries.some((q) => q.includes(`levelId=${id(2)}`))).toBe(true);
  await expect(page.getByText('Điểm Quiz/Test trung bình: 0%')).toBeVisible();
  await page.screenshot({ path: info.outputPath('statistics.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  statsDown = true;
  await page.getByRole('button', { name: 'Làm mới thống kê' }).click();
  await expect(page.getByText('Nguồn MOCK tạm ngừng')).toBeVisible();
  await expect(page.getByText('Điểm Quiz/Test trung bình: 0%')).toHaveCount(0);
  statsDown = false;
  await page.getByRole('button', { name: 'Thử lại' }).click();
  await expect(page.getByText('Điểm Quiz/Test trung bình: 0%')).toBeVisible();
});
