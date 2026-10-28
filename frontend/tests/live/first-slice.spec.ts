import { test, expect } from '@playwright/test';

// This suite requires real User/Content/Gateway + databases and published test content.
// Assessment readiness may be simulated; that is reported separately from the scope exercised here.
test('LIVE: Register → Login → Profile → Course/Level → Lesson → save/remove word → Logout', async ({
  page,
}, testInfo) => {
  test.skip(
    process.env.RUN_LIVE_INTEGRATION !== '1',
    'Opt in after provisioning the real owner APIs.',
  );
  const courseName = process.env.LIVE_COURSE_NAME || 'Demo English';
  const lessonTitle = process.env.LIVE_LESSON_TITLE || 'W6 — Greetings';
  const email = `hung-w6-${crypto.randomUUID()}@example.com`;
  const password = 'IntegrationTesting2026!';
  const apiResponses: { method: string; path: string; status: number }[] = [];
  page.on('response', (response) => {
    if (new URL(response.url()).pathname.startsWith('/api/v1/'))
      apiResponses.push({
        method: response.request().method(),
        path: new URL(response.url()).pathname,
        status: response.status(),
      });
  });
  await page.goto('/register');
  await expect(page.getByText(/MOCK \/ DEMO/)).toHaveCount(0);
  await page.getByLabel('Họ tên').fill('Lê Tiến Hưng');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByLabel('Xác nhận mật khẩu').fill(password);
  await page.getByRole('button', { name: /Tạo tài khoản/ }).click();
  await expect(page.getByText('Tài khoản đã được tạo. Bạn có thể đăng nhập ngay.')).toBeVisible();
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: /Đăng nhập/ }).click();
  await expect(page.getByRole('heading', { name: /mình bắt đầu nhé/ })).toBeVisible();
  await page.goto('/profile');
  await page.getByLabel('Họ tên', { exact: true }).fill('Lê Tiến Hưng — W6');
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await expect(page.getByText('Đã cập nhật hồ sơ.')).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Họ tên', { exact: true })).toHaveValue('Lê Tiến Hưng — W6');
  await page.goto('/setup');
  await page.getByRole('radio', { name: new RegExp(courseName) }).check();
  await page.getByRole('radio', { name: 'A1', exact: true }).check();
  await page.getByRole('button', { name: /Bắt đầu học/ }).click();
  await page.getByRole('link', { name: new RegExp(lessonTitle) }).click();
  await expect(page.getByRole('heading', { name: lessonTitle, exact: true }).last()).toBeVisible();
  await expect(page.getByRole('heading', { name: 'hello', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Lưu từ', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Bỏ lưu từ' })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('live-lesson.png'), fullPage: true });
  await page.goto('/vocabulary');
  await expect(page.getByRole('heading', { name: 'hello', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Bỏ lưu', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Bạn chưa lưu từ nào.' })).toBeVisible();
  await page.goto('/profile');
  await expect(page.getByRole('heading', { name: 'Hồ sơ học viên' })).toBeVisible();
  const menu = page.getByRole('button', { name: 'Menu', exact: true });
  if (await menu.isVisible()) await menu.click();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Chào mừng trở lại.' })).toBeVisible();
  expect(
    apiResponses.some((r) => r.path === '/api/v1/auth/register' && r.status === 201),
  ).toBeTruthy();
  expect(
    apiResponses.some(
      (r) => r.path === '/api/v1/me/learning-setup' && r.method === 'PUT' && r.status === 200,
    ),
  ).toBeTruthy();
  expect(
    apiResponses.some((r) => r.path === '/api/v1/auth/logout' && r.status === 204),
  ).toBeTruthy();
  await testInfo.attach('sanitized-api-results', {
    body: JSON.stringify(
      {
        scope:
          'REAL User/Content/Gateway/PostgreSQL; Assessment readiness MOCK, no Assessment/Progress/Kafka E2E',
        responses: apiResponses,
      },
      null,
      2,
    ),
    contentType: 'application/json',
  });
});
