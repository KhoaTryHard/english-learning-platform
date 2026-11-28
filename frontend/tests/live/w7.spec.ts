import { test, expect } from '@playwright/test';
import type { APIRequestContext, Browser, Page, TestInfo } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

// All writes target a separately provisioned local TEST stack. Never use production/shared data.
test.skip(
  process.env.RUN_W7_LIVE !== '1',
  'Opt in with the isolated W7 stack and original OOXML fixtures.',
);
const origin = 'http://127.0.0.1:5173';
const courseId = '10000000-0000-4000-8000-000000000001';
const levelId = '10000000-0000-4000-8000-000000000002';
const otherCourseId = '20000000-0000-4000-8000-000000000001';
const password = 'HungW7Learning2026!';
const adminEmail = 'admin.hung.w7@example.com';
const checks: { method: string; path: string; status: number }[] = [];
test.beforeEach(({ page }) => {
  page.setDefaultTimeout(15000);
});
async function api(
  context: APIRequestContext,
  method: string,
  path: string,
  data?: unknown,
  expected = 200,
  headers: Record<string, string> = {},
) {
  const token = !['GET', 'HEAD'].includes(method)
    ? (await (await context.get('/api/v1/auth/csrf')).json()).csrfToken
    : '';
  const response = await context.fetch('/api/v1' + path, {
    method,
    data,
    headers: { Origin: origin, ...(token ? { 'X-CSRF-Token': token } : {}), ...headers },
  });
  checks.push({ method, path: path.split('?')[0], status: response.status() });
  expect(response.status(), `${method} ${path}`).toBe(expected);
  return response.status() === 204 ? null : response.json();
}
async function adminContext(browser: Browser) {
  const context = await browser.newContext({ baseURL: origin });
  const secret = readFileSync(
    resolve(process.env.W7_STACK_ROOT!, '.secrets/admin_password'),
    'utf8',
  ).trim();
  await api(context.request, 'POST', '/auth/login', { email: adminEmail, password: secret });
  return context;
}
async function login(page: Page, email: string, secret: string) {
  await page.goto('/login');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(secret);
  await page.getByRole('button', { name: /Đăng nhập/ }).click();
  await page.waitForURL((url) => url.pathname !== '/login');
}
async function record(page: Page, kind: string, title: string, id?: string) {
  await page.goto('/admin/' + kind);
  const scope = id ? page.locator('tbody tr').filter({ hasText: id }) : page;
  await scope.getByRole('button', { name: `Xem / sửa ${title}`, exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Chi tiết và chỉnh sửa' })).toBeVisible();
}
async function create(page: Page, kind: string) {
  await page.goto('/admin/' + kind);
  await page.getByRole('button', { name: 'Tạo mới' }).click();
  await expect(page.getByRole('heading', { name: 'Tạo bản nháp mới' })).toBeVisible();
}
async function saved(page: Page) {
  await page.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
  await expect(page.getByText('Đã lưu thay đổi trên máy chủ.')).toBeVisible();
}
async function exercise(admin: APIRequestContext, lessonId: string) {
  const a = await api(
    admin,
    'POST',
    '/admin/assessments',
    { type: 'EXERCISE', courseId, lessonId, title: 'W7 Exercise', timeLimitSeconds: null },
    201,
  );
  const q = await api(
    admin,
    'POST',
    `/admin/assessments/${a.assessmentId}/questions`,
    { questionType: 'TRUE_FALSE', content: 'W7 true or false?', points: 1, correctAnswer: true },
    201,
  );
  await api(admin, 'PATCH', `/admin/assessments/${a.assessmentId}`, { ready: true });
  return { id: a.assessmentId as string, questionId: q.questionId as string };
}
async function report(page: Page, info: TestInfo) {
  await expect(page.getByText('Đang tải nội dung…', { exact: true })).toHaveCount(0);
  await info.attach('HTTP statuses (no credentials)', {
    body: JSON.stringify(checks),
    contentType: 'application/json',
  });
  await page.screenshot({ path: info.outputPath('w7-screen.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
}

test('LIVE W7: Admin User, hierarchy, resources and atomic XLSX/DOCX import', async ({
  page,
  browser,
}, info) => {
  test.setTimeout(180000);
  checks.length = 0;
  const admin = await adminContext(browser);
  const suffix = crypto.randomUUID().slice(0, 8);
  const email = `hung-w7-admin-target-${suffix}@example.com`;
  const anonymous = await browser.newContext({ baseURL: origin });
  await api(anonymous.request, 'POST', '/auth/register', { email, password }, 201);
  const secret = readFileSync(
    resolve(process.env.W7_STACK_ROOT!, '.secrets/admin_password'),
    'utf8',
  ).trim();
  await login(page, adminEmail, secret);
  await expect(page.getByRole('heading', { name: 'Chăm chút từng bài học.' })).toBeVisible();
  await page.goto('/admin/users');
  await page.getByLabel('Tìm người dùng').fill(email);
  await page.getByRole('button', { name: 'Áp dụng' }).click();
  await page.getByRole('button', { name: `Xem / sửa ${email}` }).click();
  await page.getByLabel('Họ tên').fill('Hưng W7 kiểm tra');
  await page
    .getByRole('combobox', { name: 'Trạng thái tài khoản', exact: true })
    .selectOption('BLOCKED');
  await saved(page);
  await page.getByRole('button', { name: 'Xem / sửa Hưng W7 kiểm tra' }).click();
  await page
    .getByRole('combobox', { name: 'Trạng thái tài khoản', exact: true })
    .selectOption('ACTIVE');
  await saved(page);
  await create(page, 'courses');
  await page.getByLabel('Tên khóa học').fill(`W7 Course ${suffix}`);
  await saved(page);
  await record(page, 'courses', `W7 Course ${suffix}`);
  await page.getByLabel('Xuất bản').check();
  await saved(page);
  await api(admin.request, 'PATCH', `/admin/courses/${courseId}`, { published: true });
  await create(page, 'modules');
  await page.getByRole('combobox', { name: 'Khóa học', exact: true }).selectOption(courseId);
  await page.getByLabel('Tiêu đề').fill(`W7 Module ${suffix}`);
  await page.getByLabel('Thứ tự', { exact: true }).fill(String(Date.now() % 1000000000));
  await saved(page);
  let list = await api(admin.request, 'GET', `/admin/modules?courseId=${courseId}&size=100`);
  const module = list.items.find((r: { title: string }) => r.title === `W7 Module ${suffix}`);
  await record(page, 'modules', module.title);
  await page.getByLabel('Xuất bản').check();
  await saved(page);
  await create(page, 'lessons');
  await page.getByRole('combobox', { name: 'Module', exact: true }).selectOption(module.moduleId);
  await page.getByLabel('Tiêu đề').fill(`W7 Lesson ${suffix}`);
  await page.getByLabel('Nội dung bài học').fill('Nội dung kiểm thử W7');
  await saved(page);
  list = await api(admin.request, 'GET', `/admin/lessons?moduleId=${module.moduleId}&size=100`);
  const lesson = list.items[0];
  await record(page, 'lessons', lesson.title);
  await page.getByLabel('Xuất bản').check();
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  expect((await api(admin.request, 'GET', `/admin/lessons/${lesson.lessonId}`)).published).toBe(
    false,
  );
  await exercise(admin.request, lesson.lessonId);
  await saved(page);
  expect((await api(admin.request, 'GET', `/admin/lessons/${lesson.lessonId}`)).published).toBe(
    true,
  );
  const fixture = (name: string) => resolve(process.env.W7_STACK_ROOT!, 'docs/fixtures', name);
  await page.goto('/admin/import');
  await page
    .getByRole('combobox', { name: 'Bài học đích', exact: true })
    .selectOption(lesson.lessonId);
  const downloadPromise = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Tải mẫu .xlsx' }).click();
  const download = await downloadPromise;
  expect(download.suggestedFilename()).toBe('VocabularyTemplate.xlsx');
  await page.getByLabel('File .xlsx').setInputFiles(fixture('vocabulary-valid.xlsx'));
  await page.getByRole('button', { name: 'Import file', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Import thành công' })).toBeVisible();
  const words = await api(admin.request, 'GET', `/admin/vocabulary?lessonId=${lesson.lessonId}`);
  expect(words.totalElements).toBe(2);
  await page.getByRole('button', { name: 'Import file', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'File đã bị từ chối' })).toBeVisible();
  expect(
    (await api(admin.request, 'GET', `/admin/vocabulary?lessonId=${lesson.lessonId}`))
      .totalElements,
  ).toBe(2);
  await page.getByLabel('File .xlsx').setInputFiles(fixture('vocabulary-invalid.xlsx'));
  await page.getByRole('button', { name: 'Import file', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'File đã bị từ chối' })).toBeVisible();
  await page.getByRole('button', { name: 'Lesson Word' }).click();
  await page
    .getByRole('combobox', { name: 'Module đích', exact: true })
    .selectOption(module.moduleId);
  await page.getByLabel('Thứ tự bài học').fill('2');
  await page.getByLabel('File .docx').setInputFiles(fixture('lesson-missing-title.docx'));
  await page.getByRole('button', { name: 'Import file', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'File đã bị từ chối' })).toBeVisible();
  await page.getByLabel('File .docx').setInputFiles(fixture('lesson-leading-empty.docx'));
  await page.getByRole('button', { name: 'Import file', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Import thành công' })).toBeVisible();
  expect(
    (await api(admin.request, 'GET', `/admin/lessons?moduleId=${module.moduleId}`)).totalElements,
  ).toBe(2);
  await page.screenshot({ path: info.outputPath('import-outcome.png'), fullPage: true });
  await record(page, 'vocabulary', words.items[0].word, words.items[0].vocabularyId);
  await page.getByLabel('Nghĩa', { exact: true }).fill(`Nghĩa W7 ${suffix}`);
  await page.getByLabel('Xuất bản').check();
  await saved(page);
  for (const type of ['GRAMMAR', 'READING', 'LISTENING']) {
    await create(page, 'learning-contents');
    await page
      .getByRole('combobox', { name: 'Bài học', exact: true })
      .selectOption(lesson.lessonId);
    await page.getByRole('combobox', { name: 'Loại học liệu', exact: true }).selectOption(type);
    await page.getByLabel('Tiêu đề').fill(`${type} ${suffix}`);
    if (type === 'LISTENING')
      await page.getByLabel('URL nghe HTTPS').fill('https://example.com/w7-audio.mp3');
    else await page.getByLabel('Nội dung học liệu').fill('Học liệu W7');
    await saved(page);
    await record(page, 'learning-contents', `${type} ${suffix}`);
    await page.getByLabel('Xuất bản').check();
    await saved(page);
  }
  await report(page, info);
  await anonymous.close();
  await admin.close();
});

for (const assessmentType of ['QUIZ', 'TEST'] as const) {
  test(`LIVE W7: source-backed vocabulary, setup retention, Exercise and timed ${assessmentType} resume/retake`, async ({
    page,
    browser,
  }, info) => {
    test.setTimeout(180000);
    checks.length = 0;
    const admin = await adminContext(browser);
    const suffix = crypto.randomUUID().slice(0, 8);
    await api(admin.request, 'PATCH', `/admin/courses/${courseId}`, { published: true });
    await api(admin.request, 'PATCH', `/admin/courses/${otherCourseId}`, { published: true });
    const module = await api(
      admin.request,
      'POST',
      '/admin/modules',
      { courseId, title: `W7 learner ${suffix}`, orderNo: Date.now() % 1000000000 },
      201,
    );
    await api(admin.request, 'PATCH', `/admin/modules/${module.moduleId}`, { published: true });
    const lesson = await api(
      admin.request,
      'POST',
      '/admin/lessons',
      {
        moduleId: module.moduleId,
        title: `W7 học ${suffix}`,
        content: 'Học liệu từ backend thật',
        orderNo: 1,
      },
      201,
    );
    const ex = await exercise(admin.request, lesson.lessonId);
    await api(admin.request, 'PATCH', `/admin/lessons/${lesson.lessonId}`, { published: true });
    const csrf = (await api(admin.request, 'GET', '/auth/csrf')).csrfToken;
    const importedResponse = await admin.request.post('/api/v1/admin/imports/vocabulary', {
      headers: { Origin: origin, 'X-CSRF-Token': csrf },
      multipart: {
        lessonId: lesson.lessonId,
        file: {
          name: 'vocabulary-valid.xlsx',
          mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          buffer: readFileSync(
            resolve(process.env.W7_STACK_ROOT!, 'docs/fixtures/vocabulary-valid.xlsx'),
          ),
        },
      },
    });
    expect(importedResponse.status()).toBe(201);
    const imported = await importedResponse.json();
    const vid = imported.createdIds[0];
    await api(admin.request, 'PATCH', `/admin/vocabulary/${vid}`, { published: true });
    const quiz = await api(
      admin.request,
      'POST',
      '/admin/assessments',
      {
        type: assessmentType,
        courseId,
        lessonId: null,
        title: `W7 ${assessmentType} ${suffix}`,
        timeLimitSeconds: 20,
      },
      201,
    );
    await api(
      admin.request,
      'POST',
      `/admin/assessments/${quiz.assessmentId}/questions`,
      {
        questionType: 'TRUE_FALSE',
        content: 'W7 Quiz false answer',
        points: 1,
        correctAnswer: false,
      },
      201,
    );
    await api(admin.request, 'PATCH', `/admin/assessments/${quiz.assessmentId}`, { ready: true });
    const email = `hung-w7-learner-${suffix}@example.com`;
    await page.goto('/register');
    await page.getByLabel('Họ tên').fill('Hưng W7');
    await page.getByLabel('Email', { exact: true }).fill(email);
    await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
    await page.getByLabel('Xác nhận mật khẩu').fill(password);
    await page.getByRole('button', { name: /Tạo tài khoản/ }).click();
    await page.getByLabel('Email', { exact: true }).fill(email);
    await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
    await page.getByRole('button', { name: /Đăng nhập/ }).click();
    await page.waitForURL((url) => url.pathname !== '/login');
    await page.goto('/admin/users');
    await expect(page.getByRole('heading', { name: 'Bạn chưa có quyền quản trị.' })).toBeVisible();
    await api(page.request, 'GET', '/admin/users', undefined, 403);
    await page.goto('/setup');
    await page.getByRole('radio', { name: /Demo English/ }).check();
    await page.getByRole('radio', { name: 'A1', exact: true }).check();
    await page.getByRole('button', { name: /Bắt đầu học/ }).click();
    await page.goto(`/lessons/${lesson.lessonId}`);
    await page.getByRole('button', { name: 'Lưu từ', exact: true }).first().click();
    await expect(page.getByRole('button', { name: 'Bỏ lưu từ', exact: true })).toBeVisible();
    await Promise.all([
      api(page.request, 'PUT', `/me/vocabulary/${vid}`),
      api(page.request, 'PUT', `/me/vocabulary/${vid}`),
    ]);
    expect(
      (await api(page.request, 'GET', '/me/vocabulary')).items.filter(
        (v: { vocabularyId: string }) => v.vocabularyId === vid,
      ),
    ).toHaveLength(1);
    await api(admin.request, 'PATCH', `/admin/vocabulary/${vid}`, {
      word: `source-${suffix}`,
      meaning: 'Nguồn đã cập nhật',
    });
    await api(admin.request, 'PATCH', `/admin/lessons/${lesson.lessonId}`, { published: false });
    await page.goto('/vocabulary');
    await expect(
      page.getByRole('heading', { name: `source-${suffix}`, exact: true }),
    ).toBeVisible();
    await page.goto('/setup');
    await page.getByRole('radio', { name: /Backlog Course change TEST/ }).check();
    await page.getByRole('radio', { name: 'A2', exact: true }).check();
    await page.getByRole('button', { name: /Bắt đầu học/ }).click();
    await page.goto('/vocabulary');
    await expect(
      page.getByRole('heading', { name: `source-${suffix}`, exact: true }),
    ).toBeVisible();
    await api(admin.request, 'PATCH', `/admin/vocabulary/${vid}`, { published: false });
    await page.reload();
    // Saved-only lookup continues to resolve unpublished source/parent. A missing source
    // (content=null) cannot be produced through this API, which intentionally has no DELETE.
    await expect(
      page.getByRole('heading', { name: `source-${suffix}`, exact: true }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Bỏ lưu', exact: true }).click();
    await expect(page.getByText('Bạn chưa lưu từ nào.')).toBeVisible();
    await api(page.request, 'PUT', '/me/learning-setup', { courseId, levelId });
    await api(admin.request, 'PATCH', `/admin/lessons/${lesson.lessonId}`, { published: true });
    await page.goto(`/assessments/${ex.id}`);
    const starts: { key: string | undefined; attemptId: string }[] = [];
    await page.route(`**/api/v1/assessments/${ex.id}/attempts`, async (route) => {
      const response = await route.fetch();
      expect(response.status()).toBe(201);
      starts.push({
        key: route.request().headers()['idempotency-key'],
        attemptId: (await response.json()).attemptId,
      });
      if (starts.length === 1)
        await route.abort('failed'); // Commit succeeded; its response was lost.
      else await route.fulfill({ response });
    });
    await page.getByRole('button', { name: /Bắt đầu lượt làm mới/ }).click();
    await expect(page.getByRole('alert')).toBeVisible();
    expect(starts).toHaveLength(1); // The client must wait for an explicit retry.
    await page.getByRole('button', { name: /Bắt đầu lượt làm mới/ }).click();
    await expect(page.getByRole('radio', { name: 'Sai', exact: true })).toBeVisible();
    expect(starts).toHaveLength(2);
    expect(starts[0].key).toBeTruthy();
    expect(starts[1]).toEqual(starts[0]);
    await page.getByRole('radio', { name: 'Sai', exact: true }).check();
    await page.getByRole('button', { name: 'Nộp bài', exact: true }).click();
    await expect(page.getByText('0/1', { exact: true })).toBeVisible();
    await expect(page.getByText(/Tiến độ có thể cần thêm thời gian đồng bộ/)).toBeVisible();
    if (process.env.RUN_W7_KAFKA === '1') {
      await expect
        .poll(
          async () =>
            (await api(page.request, 'GET', `/me/progress?courseId=${courseId}`)).completedLessons,
          { timeout: 30000 },
        )
        .toBe(1);
      const firstResultUrl = page.url();
      await page.getByRole('link', { name: 'Làm lại', exact: true }).click();
      await page.getByRole('button', { name: /Bắt đầu lượt làm mới/ }).click();
      await page.getByRole('button', { name: 'Nộp bài', exact: true }).click();
      await expect(page.getByText('0/1', { exact: true })).toBeVisible();
      expect(page.url()).not.toBe(firstResultUrl);
      await expect
        .poll(async () => (await api(page.request, 'GET', '/me/history?size=100')).totalElements, {
          timeout: 30000,
        })
        .toBe(2);
      expect(
        (await api(page.request, 'GET', `/me/progress?courseId=${courseId}`)).completedLessons,
      ).toBe(1);
    }
    await page.goto(`/assessments/${quiz.assessmentId}`);
    const quizKeys: (string | undefined)[] = [];
    page.on('request', (request) => {
      if (
        request.method() === 'POST' &&
        new URL(request.url()).pathname === `/api/v1/assessments/${quiz.assessmentId}/attempts`
      )
        quizKeys.push(request.headers()['idempotency-key']);
    });
    await page.getByRole('button', { name: /Bắt đầu lượt làm mới/ }).click();
    await page.getByRole('radio', { name: 'Sai', exact: true }).check();
    await expect(page.getByText('Đã tải/lưu đáp án từ máy chủ.', { exact: true })).toBeVisible();
    const attemptUrl = page.url();
    const before = await api(page.request, 'GET', `/attempts/${attemptUrl.split('/').at(-1)}`);
    await page.reload();
    await expect(page.getByRole('radio', { name: 'Sai', exact: true })).toBeChecked();
    const after = await api(page.request, 'GET', `/attempts/${before.attemptId}`);
    expect(after.deadlineAt).toBe(before.deadlineAt);
    await expect(page.getByText('1/1', { exact: true })).toBeVisible({ timeout: 25000 });
    await expect(page.getByText('ĐÃ HẾT GIỜ', { exact: true })).toBeVisible();
    const firstResultUrl = page.url();
    await page.getByRole('link', { name: 'Làm lại', exact: true }).click();
    await page.getByRole('button', { name: /Bắt đầu lượt làm mới/ }).click();
    await page.getByRole('button', { name: 'Nộp bài', exact: true }).click();
    await expect(page.getByText('0/1', { exact: true })).toBeVisible();
    expect(page.url()).not.toBe(firstResultUrl);
    expect(quizKeys).toHaveLength(2);
    expect(quizKeys[0]).toBeTruthy();
    expect(quizKeys[1]).not.toBe(quizKeys[0]);
    await report(page, info);
    await admin.close();
  });
}
