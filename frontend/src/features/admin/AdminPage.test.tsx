import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { demoStore, server } from '../../../tests/setup';
import { courses, demoId, lessons, vocabulary } from '../../mocks/data';
import { ResourcePage } from './AdminPage';
import { Protected } from '../../app/App';
import { AuthProvider } from '../auth/AuthProvider';

beforeEach(() => {
  demoStore.currentUserId = demoId(901);
});

it('keeps the selected editor stable while a write is pending', async () => {
  const user = userEvent.setup();
  server.use(
    http.patch('*/api/v1/admin/courses/:id', async () => {
      await delay(500);
      return HttpResponse.json(courses[0]);
    }),
  );
  show('courses');
  await user.click(await screen.findByRole('button', { name: `Xem / sửa ${courses[0].name}` }));
  await screen.findByLabelText('Tên khóa học');
  await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));
  expect(screen.getByRole('button', { name: 'Tạo mới' })).toBeDisabled();
  expect(screen.getByRole('button', { name: `Xem / sửa ${courses[1].name}` })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Đóng chỉnh sửa' })).toBeDisabled();
  await screen.findByText('Đã lưu thay đổi trên máy chủ.');
  expect(screen.getByRole('button', { name: 'Tạo mới' })).toBeEnabled();
});
function show(kind: Parameters<typeof ResourcePage>[0]['kind']) {
  return render(
    <MemoryRouter>
      <ResourcePage kind={kind} />
    </MemoryRouter>,
  );
}

it('blocks a Learner before mounting any Admin data request', async () => {
  demoStore.currentUserId = demoId(900);
  let calls = 0;
  server.use(
    http.get('*/api/v1/admin/users', () => {
      calls++;
      return HttpResponse.json({});
    }),
  );
  render(
    <MemoryRouter initialEntries={['/admin/users']}>
      <AuthProvider>
        <Routes>
          <Route element={<Protected role="ADMIN" />}>
            <Route path="/admin/users" element={<ResourcePage kind="users" />} />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
  await screen.findByRole('heading', { name: 'Bạn chưa có quyền quản trị.' });
  expect(calls).toBe(0);
});

it('reads detail and patches only fullName/status, keeping seeded Admin readonly', async () => {
  const user = userEvent.setup();
  let body: unknown;
  server.use(
    http.patch('*/api/v1/admin/users/:id', async ({ request }) => {
      body = await request.json();
      return HttpResponse.json({ ...demoStore.users[demoId(900)].profile, ...(body as object) });
    }),
  );
  show('users');
  await user.click(await screen.findByRole('button', { name: 'Xem / sửa Hưng' }));
  const form = await screen.findByRole('region', { name: 'Chỉnh sửa bản ghi' });
  await user.clear(within(form).getByLabelText('Họ tên'));
  await user.type(within(form).getByLabelText('Họ tên'), 'Tên mới');
  await user.selectOptions(within(form).getByLabelText('Trạng thái tài khoản'), 'BLOCKED');
  expect(within(form).getByLabelText('Email')).toHaveAttribute('readonly');
  await user.click(within(form).getByRole('button', { name: 'Lưu thay đổi' }));
  await screen.findByText('Đã lưu thay đổi trên máy chủ.');
  expect(body).toEqual({ fullName: 'Tên mới', status: 'BLOCKED' });
  await user.click(await screen.findByRole('button', { name: 'Xem / sửa Quản trị viên' }));
  await screen.findByText('Tài khoản Admin chỉ đọc tại đây.');
  expect(screen.queryByRole('button', { name: 'Lưu thay đổi' })).not.toBeInTheDocument();
});

it('creates a draft Course with only name, then edits with no delete action', async () => {
  const user = userEvent.setup();
  show('courses');
  await user.click(screen.getByRole('button', { name: 'Tạo mới' }));
  await user.type(await screen.findByLabelText('Tên khóa học'), 'Khóa mới');
  expect(screen.queryByLabelText('Xuất bản')).not.toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));
  await screen.findByRole('button', { name: 'Xem / sửa Khóa mới' });
  expect(demoStore.adminData.courses.at(-1)).toMatchObject({ name: 'Khóa mới', published: false });
  expect(screen.queryByRole('button', { name: /xóa/i })).not.toBeInTheDocument();
});

it.each([409, 503])(
  'keeps Lesson edits and published status unconfirmed on HTTP %s',
  async (status) => {
    const user = userEvent.setup();
    server.use(
      http.patch('*/api/v1/admin/lessons/:id', () =>
        HttpResponse.json(
          { code: 'READINESS_FAILED', message: 'Exercise chưa sẵn sàng', errors: [] },
          { status },
        ),
      ),
    );
    show('lessons');
    await user.click(await screen.findByRole('button', { name: `Xem / sửa ${lessons[0].title}` }));
    const title = await screen.findByLabelText('Tiêu đề');
    await user.clear(title);
    await user.type(title, 'Bản đang chỉnh');
    await user.click(screen.getByRole('button', { name: 'Lưu thay đổi' }));
    await screen.findByText('Exercise chưa sẵn sàng');
    expect(title).toHaveValue('Bản đang chỉnh');
    expect(screen.queryByText('Đã lưu thay đổi trên máy chủ.')).not.toBeInTheDocument();
    expect(demoStore.adminData.lessons[0]).toMatchObject({ title: lessons[0].title });
  },
);

it('updates imported Vocabulary without a create or immutable parent field', async () => {
  const user = userEvent.setup();
  show('vocabulary');
  expect(screen.queryByRole('button', { name: 'Tạo mới' })).not.toBeInTheDocument();
  await user.click(await screen.findByRole('button', { name: `Xem / sửa ${vocabulary[0].word}` }));
  await screen.findByLabelText('Nghĩa');
  expect(screen.queryByRole('combobox', { name: 'Bài học' })).not.toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Import từ vựng' })).toHaveAttribute(
    'href',
    '/admin/import',
  );
});

it('fetches all pages rather than truncating a list at 100 rows', async () => {
  const pages: string[] = [];
  server.use(
    http.get('*/api/v1/admin/courses', ({ request }) => {
      const page = new URL(request.url).searchParams.get('page')!;
      pages.push(page);
      return HttpResponse.json({
        items:
          page === '0'
            ? Array.from({ length: 100 }, (_, i) => ({
                ...courses[0],
                courseId: demoId(1000 + i),
                name: `Khóa ${i}`,
              }))
            : [{ ...courses[1], name: 'Trang thứ hai' }],
        page: Number(page),
        size: 100,
        totalElements: 101,
        totalPages: 2,
      });
    }),
  );
  show('courses');
  await screen.findByText('Trang thứ hai');
  expect(pages).toEqual(['0', '1']);
});

it('requires Grammar/Reading text and sends a whitelisted type-specific create', async () => {
  const user = userEvent.setup();
  let body: unknown;
  server.use(
    http.post('*/api/v1/admin/learning-contents', async ({ request }) => {
      body = await request.json();
      return HttpResponse.json(
        {
          contentId: demoId(999),
          ...(body as object),
          published: false,
          availability: 'AVAILABLE',
        },
        { status: 201 },
      );
    }),
  );
  show('learning-contents');
  await user.click(screen.getByRole('button', { name: 'Tạo mới' }));
  const editor = await screen.findByRole('region', { name: 'Chỉnh sửa bản ghi' });
  await waitFor(() =>
    expect(within(editor).getByRole('option', { name: lessons[0].title })).toBeInTheDocument(),
  );
  await user.selectOptions(within(editor).getByLabelText('Bài học'), lessons[0].lessonId);
  await user.type(within(editor).getByLabelText('Tiêu đề'), 'Ngữ pháp mới');
  await user.type(within(editor).getByLabelText('Nội dung học liệu'), 'Nội dung');
  await user.click(within(editor).getByRole('button', { name: 'Lưu thay đổi' }));
  await screen.findByText('Đã lưu thay đổi trên máy chủ.');
  expect(body).toEqual({
    lessonId: lessons[0].lessonId,
    type: 'GRAMMAR',
    title: 'Ngữ pháp mới',
    text: 'Nội dung',
    resourceUrl: null,
  });
});
