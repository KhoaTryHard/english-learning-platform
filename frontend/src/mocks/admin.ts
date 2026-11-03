import { http, HttpResponse } from 'msw';
import type { DemoStore } from './data';
import { recordId, recordTitle } from '../features/admin/api';
import { isAdminKind } from '../features/admin/AdminPage';
import type { AdminRecord } from '../features/admin/api';

// UI simulator, not a backend parser, authorization test or publish-readiness service.
export function adminHandlers(store: DemoStore, persist: () => void) {
  const error = (status: number, message: string) =>
    HttpResponse.json({ code: 'DEMO_RESPONSE', message, errors: [] }, { status });
  return [
    http.all('*/api/v1/admin/*', async ({ request }) => {
      const user = store.currentUserId && store.users[store.currentUserId]?.profile;
      if (!user) return error(401, 'Đăng nhập để tiếp tục.');
      if (user.role !== 'ADMIN') return error(403, 'Chỉ Admin được mở nội dung này.');
      if (
        !['GET', 'HEAD'].includes(request.method) &&
        request.headers.get('X-CSRF-Token') !== store.csrfToken
      )
        return error(403, 'CSRF không hợp lệ.');
      const url = new URL(request.url),
        [, kind, id] = url.pathname.replace('/api/v1', '').split('/').slice(1);
      if (!isAdminKind(kind))
        return error(
          503,
          'Import file cần backend thật để kiểm tra cấu trúc và ghi dữ liệu. Chế độ demo không phân tích file.',
        );
      const rows =
        kind === 'users' ? Object.values(store.users).map((u) => u.profile) : store.adminData[kind];
      const current = rows.find((r) => recordId(r) === id);
      if (request.method === 'GET') {
        if (id) return current ? HttpResponse.json(current) : error(404, 'Không tìm thấy bản ghi.');
        const q = url.searchParams.get('q')?.toLocaleLowerCase() || '';
        const filter = ['courseId', 'moduleId', 'lessonId'].find((key) =>
          url.searchParams.has(key),
        );
        const filtered = rows.filter(
          (r) =>
            (!q ||
              `${recordTitle(r)} ${'email' in r ? r.email : ''}`.toLocaleLowerCase().includes(q)) &&
            (!filter || String(r[filter as keyof typeof r]) === url.searchParams.get(filter)),
        );
        const page = Number(url.searchParams.get('page') || 0),
          size = Number(url.searchParams.get('size') || 20);
        return HttpResponse.json({
          items: filtered.slice(page * size, (page + 1) * size),
          page,
          size,
          totalElements: filtered.length,
          totalPages: Math.ceil(filtered.length / size),
        });
      }
      if (!['POST', 'PATCH'].includes(request.method)) return error(405, 'Không có thao tác xóa.');
      const body = (await request.json()) as Record<string, unknown>;
      if (kind === 'users') {
        if (!id || !current || !('role' in current) || current.role !== 'LEARNER')
          return error(403, 'Chỉ cập nhật Learner.');
        const allowed = ['fullName', 'status'];
        if (Object.keys(body).some((key) => !allowed.includes(key)))
          return error(400, 'Trường ngoài whitelist.');
        Object.assign(current, body);
        persist();
        return HttpResponse.json(current);
      }
      if (request.method === 'PATCH') {
        if (!current) return error(404, 'Không tìm thấy bản ghi.');
        if (kind === 'lessons' && body.published === true)
          return error(
            409,
            'Demo chưa xác minh Exercise readiness. Dùng backend thật để xuất bản.',
          );
        Object.assign(current, body);
        persist();
        return HttpResponse.json(current);
      }
      if (kind === 'vocabulary') return error(405, 'Từ vựng mới chỉ được import.');
      const idKey = {
        courses: 'courseId',
        modules: 'moduleId',
        lessons: 'lessonId',
        'learning-contents': 'contentId',
      }[kind];
      const added = {
        ...body,
        [idKey]: crypto.randomUUID(),
        published: false,
        ...(kind === 'learning-contents' ? { availability: 'AVAILABLE' } : {}),
      } as unknown as AdminRecord;
      store.adminData[kind].push(added);
      persist();
      return HttpResponse.json(added, { status: 201 });
    }),
  ];
}
