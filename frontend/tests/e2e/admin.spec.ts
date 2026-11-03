import { expect, test } from '@playwright/test';

test('Admin routes, draft CRUD and Learner boundary on desktop/mobile (MOCK)', async ({
  page,
}, testInfo) => {
  await page.goto('/login');
  await page.getByLabel('Email', { exact: true }).fill('admin.demo@example.com');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('LearningDemo2026!');
  await page.getByRole('button', { name: /Đăng nhập/ }).click();
  await page.waitForURL((url) => url.pathname !== '/login');
  await expect(page.getByRole('heading', { name: 'Chăm chút từng bài học.' })).toBeVisible();
  await page.goto('/admin/courses');
  await page.getByRole('button', { name: 'Tạo mới' }).click();
  await page.getByLabel('Tên khóa học').fill('Course browser MOCK');
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await expect(page.getByRole('button', { name: 'Xem / sửa Course browser MOCK' })).toBeVisible();
  await page.getByRole('button', { name: 'Xem / sửa Course browser MOCK' }).click();
  await page.getByLabel('Xuất bản').check();
  await page.getByRole('button', { name: 'Lưu thay đổi' }).click();
  await expect(page.getByText('Đã lưu thay đổi trên máy chủ.')).toBeVisible();
  await expect(page.getByRole('button', { name: /Xóa/i })).toHaveCount(0);
  await page.goto('/admin/import');
  await expect(page.getByLabel('Bài học đích')).toBeVisible();
  await page.getByRole('button', { name: 'Lesson Word' }).click();
  await expect(page.getByLabel('Module đích')).toBeVisible();
  await expect(page.getByLabel('Thứ tự bài học')).toHaveValue('1');
  await page.screenshot({ path: testInfo.outputPath('admin-import.png'), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await page.goto('/admin/users');
  await expect(page.getByRole('button', { name: 'Xem / sửa Hưng' })).toBeVisible();
  if (testInfo.project.name === 'mobile')
    await page.getByRole('button', { name: 'Menu', exact: true }).click();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Chào mừng trở lại.' })).toBeVisible();
  await page.getByLabel('Email', { exact: true }).fill('hung.demo@example.com');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('LearningDemo2026!');
  await page.getByRole('button', { name: /Đăng nhập/ }).click();
  await page.waitForURL((url) => url.pathname !== '/login');
  await page.goto('/admin/users');
  await expect(page.getByRole('heading', { name: 'Bạn chưa có quyền quản trị.' })).toBeVisible();
});
