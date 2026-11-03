import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

const uid = (n: number) => `00000000-0000-4000-8000-${n.toString().padStart(12, '0')}`;
async function login(page: Page) {
  await page.goto('/login');
  await expect(page.getByText(/MOCK \/ DEMO/)).toBeVisible();
  await page.getByLabel('Email', { exact: true }).fill('hung.demo@example.com');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('LearningDemo2026!');
  await page.getByRole('button', { name: /Đăng nhập/ }).click();
  await expect(page.getByRole('heading', { name: 'Chào Hưng, mình học tiếp nhé.' })).toBeVisible();
}

test('register → login → setup → lesson → Exercise/Result, save vocabulary and retake', async ({
  page,
}) => {
  const email = `learner-${crypto.randomUUID()}@example.com`;
  await page.goto('/register');
  await page.getByLabel('Họ tên').fill('Lê Tiến Hưng');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill('LearningDemo2026!');
  await page.getByLabel('Xác nhận mật khẩu').fill('LearningDemo2026!');
  await page.getByRole('button', { name: /Tạo tài khoản/ }).click();
  await expect(page.getByText('Tài khoản đã được tạo. Bạn có thể đăng nhập ngay.')).toBeVisible();
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill('LearningDemo2026!');
  await page.getByRole('button', { name: /Đăng nhập/ }).click();
  await page.getByRole('link', { name: /Chọn khóa học và trình độ/ }).click();
  await page.getByRole('radio', { name: /Tiếng Anh B1/ }).check();
  await page.getByRole('radio', { name: 'B1', exact: true }).check();
  await page.getByRole('button', { name: /Bắt đầu học/ }).click();
  await page.getByRole('link', { name: /Bắt đầu học/ }).click();
  await expect(page.getByRole('heading', { name: 'restaurant', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Lưu từ', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Bỏ lưu từ' })).toBeVisible();
  await page.getByRole('link', { name: /Đến bài tập cuối bài/ }).click();
  await page.getByRole('button', { name: /Bắt đầu lượt làm mới/ }).click();
  await expect(page.getByRole('timer')).toHaveCount(0);
  await page.getByRole('radio', { name: /Could I have the menu/ }).check();
  await page.getByRole('button', { name: /Câu tiếp theo/ }).click();
  await page.getByRole('radio', { name: 'Sai', exact: true }).check();
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Kết quả bài tập cuối bài' })).toBeVisible();
  await expect(page.getByText('2/2', { exact: true })).toBeVisible();
  const firstResult = page.url();
  await page.getByRole('link', { name: 'Làm lại', exact: true }).click();
  await page.getByRole('button', { name: /Bắt đầu lượt làm mới/ }).click();
  await page.getByRole('button', { name: 'Nộp bài', exact: true }).click();
  await expect(page.getByText('0/2', { exact: true })).toBeVisible();
  expect(page.url()).not.toBe(firstResult);
  await page.goto('/progress');
  await expect(page.getByText('1/2 bài học hoàn thành', { exact: true })).toBeVisible();
  await page.goto('/vocabulary');
  await expect(page.getByRole('heading', { name: 'restaurant', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Bỏ lưu', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Bạn chưa lưu từ nào.' })).toBeVisible();
});

test('Quiz deadline and saved FALSE survive refresh and logout/login', async ({ page }) => {
  await login(page);
  await page.goto(`/assessments/${uid(510)}`);
  await page.getByRole('button', { name: /Bắt đầu lượt làm mới/ }).click();
  await expect(page.getByRole('timer')).toBeVisible();
  const attemptUrl = page.url();
  await page.getByRole('radio', { name: /Could I have the menu/ }).check();
  await page.getByRole('button', { name: /Câu tiếp theo/ }).click();
  await page.getByRole('radio', { name: 'Sai', exact: true }).check();
  await expect(page.getByText('Đã tải/lưu đáp án từ máy chủ.')).toBeVisible();
  const before = await page.evaluate(() => {
    const store = JSON.parse(sessionStorage.getItem('lt:demo:v1')!);
    return Object.values(store.attempts).map(
      (a) => (a as { data: { deadlineAt: string } }).data.deadlineAt,
    );
  });
  await page.reload();
  await expect(page.getByRole('radio', { name: /Could I have the menu/ })).toBeChecked();
  await page.getByRole('button', { name: /Câu tiếp theo/ }).click();
  await expect(page.getByRole('radio', { name: 'Sai', exact: true })).toBeChecked();
  await page.goto('/profile');
  await expect(page.getByRole('heading', { name: 'Hồ sơ học viên' })).toBeVisible();
  const menu = page.getByRole('button', { name: 'Menu', exact: true });
  if (await menu.isVisible()) await menu.click();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Chào mừng trở lại.' })).toBeVisible();
  await page.goto(new URL(attemptUrl).pathname);
  await expect(page.getByRole('heading', { name: 'Chào mừng trở lại.' })).toBeVisible();
  await page.getByLabel('Email', { exact: true }).fill('hung.demo@example.com');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('LearningDemo2026!');
  await page.getByRole('button', { name: /Đăng nhập/ }).click();
  await expect(page).toHaveURL(attemptUrl);
  const after = await page.evaluate(() => {
    const store = JSON.parse(sessionStorage.getItem('lt:demo:v1')!);
    return Object.values(store.attempts).map(
      (a) => (a as { data: { deadlineAt: string } }).data.deadlineAt,
    );
  });
  expect(after).toEqual(before);
});

test('responsive screens and assets have no horizontal overflow', async ({ page }, testInfo) => {
  await login(page);
  for (const [name, path] of [
    ['dashboard', '/'],
    ['courses', '/courses'],
    ['profile', '/profile'],
    ['lesson', `/lessons/${uid(201)}`],
  ]) {
    await page.goto(path);
    await expect(page.getByRole('heading', { level: 1 }).first()).toBeVisible();
    await page.evaluate(() => document.fonts.ready);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    const broken = await page.locator('img').evaluateAll((images) =>
      images
        .filter((img): img is HTMLImageElement => img instanceof HTMLImageElement)
        .filter((img) => !img.complete || img.naturalWidth === 0)
        .map((img) => img.src),
    );
    expect(broken).toEqual([]);
    await page.screenshot({ path: testInfo.outputPath(`${name}.png`), fullPage: true });
  }
});
