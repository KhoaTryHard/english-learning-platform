import { test, expect, type APIRequestContext, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

// Isolated TEST only; all business fixtures go through owner APIs via Gateway.
test.skip(process.env.RUN_KHOA_W8_LIVE !== '1', 'Requires the isolated Khoa TEST stack.');
const origin = 'http://127.0.0.1:5173';
const courseId = '10000000-0000-4000-8000-000000000001';
const levelId = '10000000-0000-4000-8000-000000000002';
const otherCourseId = '20000000-0000-4000-8000-000000000001';
const otherLevelId = '20000000-0000-4000-8000-000000000002';
const checks: { method: string; path: string; expected: number; actual: number }[] = [];
test.beforeEach(({ page }) => { checks.length = 0; test.setTimeout(120000); page.setDefaultTimeout(15000); });
test.afterEach(async ({}, info) => {
  await info.attach('sanitized-http', { body: JSON.stringify(checks), contentType: 'application/json' });
});
async function call(api: APIRequestContext, method: string, path: string, data?: unknown,
                    expected = 200, extra: Record<string, string> = {}) {
  const csrf = !['GET', 'HEAD'].includes(method)
    ? (await (await api.get('/api/v1/auth/csrf')).json()).csrfToken : '';
  const response = await api.fetch('/api/v1' + path, {
    method, data, headers: { Origin: origin, ...(csrf ? { 'X-CSRF-Token': csrf } : {}), ...extra },
  });
  checks.push({ method, path: path.split('?')[0], expected, actual: response.status() });
  expect(response.status(), `${method} ${path}`).toBe(expected);
  return response.status() === 204 ? null : response.json();
}
async function admin(page: Page) {
  const password = readFileSync(resolve(process.env.KHOA_STACK_ROOT!, '.secrets/admin_password'), 'utf8').trim();
  // The UI also records the non-secret Admin navigation hint used on reload.
  await page.goto('/login');
  await page.getByLabel('Email', { exact: true }).fill('admin.hung.w7@example.com');
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: /Đăng nhập/ }).click();
  await expect(page.getByRole('heading', { name: 'Chăm chút từng bài học.' })).toBeVisible();
}
async function lesson(page: Page) {
  await call(page.request, 'PATCH', `/admin/courses/${courseId}`, { published: true });
  const module = await call(page.request, 'POST', '/admin/modules', {
    courseId, title: `Khoa W8 ${crypto.randomUUID()}`, orderNo: Date.now() % 1000000000,
  }, 201);
  await call(page.request, 'PATCH', `/admin/modules/${module.moduleId}`, { published: true });
  return call(page.request, 'POST', '/admin/lessons', {
    moduleId: module.moduleId, title: `Khoa W8 lesson ${crypto.randomUUID()}`,
    content: 'Owner API fixture', orderNo: 1,
  }, 201);
}
async function exercise(page: Page, lessonId: string, points = 1) {
  const a = await call(page.request, 'POST', '/admin/assessments', {
    type: 'EXERCISE', courseId, lessonId, title: `Khoa W8 exercise ${crypto.randomUUID()}`,
    timeLimitSeconds: null,
  }, 201);
  const q = await call(page.request, 'POST', `/admin/assessments/${a.assessmentId}/questions`, {
    questionType: 'TRUE_FALSE', content: 'Khoa fractional question', points, correctAnswer: false,
  }, 201);
  return { a, q };
}

test('LIVE W8 Admin Assessment creates draft, rejects early readiness, keeps false and publishes', async ({ page }, info) => {
  await admin(page);
  const l = await lesson(page);
  const title = `Khoa W8 UI ${crypto.randomUUID()}`;
  await page.goto('/admin/assessments');
  await page.getByRole('button', { name: 'Tạo bài mới' }).click();
  await page.getByLabel('Tên bài').fill(title);
  await page.getByLabel('Khóa học của bài').selectOption(courseId);
  await page.getByLabel('Bài học liên kết').selectOption(l.lessonId);
  const created = page.waitForResponse(r => r.url().endsWith('/api/v1/admin/assessments') && r.request().method() === 'POST');
  await page.getByRole('button', { name: 'Lưu cấu hình' }).click();
  const response = await created;
  expect(response.status()).toBe(201);
  const a = await response.json();
  await expect(page.getByText('Câu hỏi (0)')).toBeVisible();
  await page.getByLabel('Sẵn sàng nhận lượt làm mới').check();
  const rejected = page.waitForResponse(r => r.url().endsWith(`/admin/assessments/${a.assessmentId}`) && r.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'Lưu cấu hình' }).click();
  expect((await rejected).status()).toBe(409);
  expect((await call(page.request, 'GET', `/admin/assessments/${a.assessmentId}`)).ready).toBe(false);
  await page.getByRole('button', { name: 'Thêm câu hỏi', exact: true }).click();
  await page.getByLabel('Loại câu hỏi').selectOption('TRUE_FALSE');
  await page.getByLabel('Nội dung câu hỏi').fill('False remains false through Gateway');
  await page.getByRole('combobox', { name: 'Đáp án đúng', exact: true }).selectOption('false');
  await page.getByRole('button', { name: 'Lưu câu hỏi' }).click();
  await expect(page.getByText('Câu hỏi (1)')).toBeVisible();
  await page.getByLabel('Sẵn sàng nhận lượt làm mới').check();
  const accepted = page.waitForResponse(r => r.url().endsWith(`/admin/assessments/${a.assessmentId}`) && r.request().method() === 'PATCH');
  await page.getByRole('button', { name: 'Lưu cấu hình' }).click();
  expect((await accepted).status()).toBe(200);
  const detail = await call(page.request, 'GET', `/admin/assessments/${a.assessmentId}`);
  expect(detail).toMatchObject({ ready: true, questionCount: 1, timeLimitSeconds: null });
  expect(detail.questions[0]).toMatchObject({ correctAnswer: false, points: 1 });
  await call(page.request, 'PATCH', `/admin/lessons/${l.lessonId}`, { published: true });
  // Absence probe only: no Statistics success is claimed from 404.
  await call(page.request, 'GET', '/admin/statistics', undefined, 404);
  await info.attach('fixture', { body: JSON.stringify({ lessonId: l.lessonId, assessmentId: a.assessmentId }), contentType: 'application/json' });
});

test('LIVE reproduction: owner accepts 0.001 but Admin Question form blocks its unchanged score', async ({ page }, info) => {
  await admin(page);
  const l = await lesson(page);
  const { a, q } = await exercise(page, l.lessonId, 0.001);
  expect(q.points).toBe(0.001);
  const original = await call(page.request, 'GET', `/admin/assessments/${a.assessmentId}`);
  expect(original.questions[0].points).toBe(0.001);
  await page.goto('/admin/assessments');
  await page.getByRole('button', { name: `Xem / sửa ${a.title}`, exact: true }).click();
  await page.getByRole('button', { name: `Sửa câu hỏi ${q.content}`, exact: true }).click();
  const points = page.getByLabel('Điểm');
  await expect(points).toHaveValue('0.001');
  expect(await points.evaluate((el: HTMLInputElement) => el.validity.rangeUnderflow)).toBe(true);
  let puts = 0;
  page.on('request', r => { if (r.method() === 'PUT' && r.url().includes(`/questions/${q.questionId}`)) puts++; });
  await page.getByLabel('Nội dung câu hỏi').fill('Updated text without changing score');
  await page.getByRole('button', { name: 'Lưu câu hỏi' }).click();
  await expect(points).toBeFocused();
  expect(puts).toBe(0);
  const unchanged = await call(page.request, 'GET', `/admin/assessments/${a.assessmentId}`);
  expect(unchanged.questions[0].content).toBe(q.content);
  await info.attach('confirmed-defect', {
    body: JSON.stringify({ status: 'CONFIRMED_DEFECT', owner: 'LeTHung', points: 0.001,
      expected: 'Question can be edited without changing contract-valid points',
      actual: 'rangeUnderflow, no PUT, server retains original content',
      assessmentId: a.assessmentId, questionId: q.questionId }), contentType: 'application/json',
  });
});

test('LIVE W8 History keeps retakes and Level snapshot after setup change; Progress refresh uses current course', async ({ page }, info) => {
  await admin(page);
  const l = await lesson(page);
  const { a } = await exercise(page, l.lessonId);
  await call(page.request, 'PATCH', `/admin/assessments/${a.assessmentId}`, { ready: true });
  await call(page.request, 'PATCH', `/admin/lessons/${l.lessonId}`, { published: true });
  await call(page.request, 'PATCH', `/admin/courses/${otherCourseId}`, { published: true });
  await call(page.request, 'POST', '/auth/logout', undefined, 204);
  const credentials = { email: `khoa-w8-${crypto.randomUUID()}@example.com`, password: crypto.randomUUID() + '-Aa1' };
  await call(page.request, 'POST', '/auth/register', credentials, 201);
  await call(page.request, 'POST', '/auth/login', credentials);
  await call(page.request, 'PUT', '/me/learning-setup', { courseId, levelId });
  await call(page.request, 'GET', '/admin/assessments', undefined, 403);
  await call(page.request, 'GET', '/admin/statistics', undefined, 403);
  const attempts: string[] = [];
  for (let i = 0; i < 2; i++) {
    const attempt = await call(page.request, 'POST', `/assessments/${a.assessmentId}/attempts`, undefined, 201,
      { 'Idempotency-Key': crypto.randomUUID() });
    attempts.push(attempt.attemptId);
    await call(page.request, 'POST', `/attempts/${attempt.attemptId}/submit`);
  }
  expect(attempts[0]).not.toBe(attempts[1]);
  await expect.poll(async () => (await call(page.request, 'GET', '/me/history?size=100')).totalElements,
    { timeout: 30000 }).toBe(2);
  expect((await call(page.request, 'GET', `/me/progress?courseId=${courseId}`)).completedLessons).toBe(1);
  await call(page.request, 'PUT', '/me/learning-setup', { courseId: otherCourseId, levelId: otherLevelId });
  await page.goto('/history');
  await expect(page.getByText(levelId, { exact: true })).toHaveCount(2);
  await expect(page.getByRole('link', { name: '0/1 · 0% →' })).toHaveCount(2);
  await page.getByRole('button', { name: 'Làm mới lịch sử' }).click();
  await expect(page.getByText(levelId, { exact: true })).toHaveCount(2);
  await page.goto('/progress');
  await expect(page.getByRole('button', { name: 'Làm mới tiến độ' })).toBeVisible();
  await page.getByRole('button', { name: 'Làm mới tiến độ' }).click();
  const progress = await call(page.request, 'GET', `/me/progress?courseId=${otherCourseId}`);
  await expect(page.getByText(`0/${progress.totalLessons} bài học hoàn thành`)).toBeVisible();
  await info.attach('fixture', { body: JSON.stringify({ assessmentId: a.assessmentId, attempts,
    oldCourse: courseId, oldLevel: levelId, newCourse: otherCourseId, newLevel: otherLevelId }), contentType: 'application/json' });
});
