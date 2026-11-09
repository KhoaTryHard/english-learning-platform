import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { delay, http, HttpResponse } from 'msw';
import { demoStore, server } from '../../../tests/setup';
import { courses, demoId, lessons } from '../../mocks/data';
import { AdminAssessmentsPage } from './AssessmentsPage';
import type { Schema } from '../../shared/api/client';

let detail: Schema<'AssessmentDetail'>;
beforeEach(() => {
  demoStore.currentUserId = demoId(901);
  detail = {
    assessmentId: demoId(850),
    type: 'EXERCISE',
    courseId: courses[0].courseId,
    lessonId: lessons[0].lessonId,
    title: 'Bài tập tuần 8',
    timeLimitSeconds: null,
    ready: false,
    questionCount: 0,
    questions: [],
  };
  server.use(
    http.get('*/api/v1/admin/assessments', () =>
      HttpResponse.json({ items: [detail], page: 0, size: 100, totalElements: 1, totalPages: 1 }),
    ),
    http.get('*/api/v1/admin/assessments/:id', () => HttpResponse.json(detail)),
  );
});

it('creates a draft Exercise with no timer and no readiness field', async () => {
  let body: unknown;
  server.use(
    http.post('*/api/v1/admin/assessments', async ({ request }) => {
      body = await request.json();
      return HttpResponse.json(detail, { status: 201 });
    }),
  );
  const user = userEvent.setup();
  render(<AdminAssessmentsPage />);
  await screen.findByText('Bài tập tuần 8');
  await user.click(screen.getByRole('button', { name: 'Tạo bài mới' }));
  await user.type(await screen.findByLabelText('Tên bài'), 'Bài tập tuần 8');
  await user.selectOptions(screen.getByLabelText('Khóa học của bài'), courses[0].courseId);
  await user.selectOptions(screen.getByLabelText('Bài học liên kết'), lessons[0].lessonId);
  await user.click(screen.getByRole('button', { name: 'Lưu cấu hình' }));
  await waitFor(() =>
    expect(body).toEqual({
      type: 'EXERCISE',
      courseId: courses[0].courseId,
      lessonId: lessons[0].lessonId,
      title: 'Bài tập tuần 8',
      timeLimitSeconds: null,
    }),
  );
  expect(await screen.findByText('Câu hỏi (0)')).toBeInTheDocument();
});

it('preserves false answer and server-generated question identity with POST then PUT', async () => {
  const writes: { method: string; body: unknown; questionId?: string }[] = [];
  const respond = async (request: Request, questionId?: string) => {
    const body = (await request.json()) as Schema<'TrueFalseWrite'>;
    writes.push({ method: request.method, body, questionId });
    const q: Schema<'TrueFalseQuestion'> = { ...body, questionId: demoId(860) };
    detail = { ...detail, questions: [q], questionCount: 1 };
    return HttpResponse.json(q, { status: request.method === 'POST' ? 201 : 200 });
  };
  server.use(
    http.post('*/api/v1/admin/assessments/:id/questions', ({ request }) => respond(request)),
    http.put('*/api/v1/admin/assessments/:id/questions/:qid', ({ request, params }) =>
      respond(request, String(params.qid)),
    ),
  );
  const user = userEvent.setup();
  render(<AdminAssessmentsPage />);
  await user.click(await screen.findByRole('button', { name: 'Xem / sửa Bài tập tuần 8' }));
  await user.click(await screen.findByRole('button', { name: 'Thêm câu hỏi' }));
  await user.selectOptions(screen.getByLabelText('Loại câu hỏi'), 'TRUE_FALSE');
  await user.type(screen.getByLabelText('Nội dung câu hỏi'), 'This is false.');
  await user.selectOptions(screen.getByLabelText('Đáp án đúng'), 'false');
  await user.click(screen.getByRole('button', { name: 'Lưu câu hỏi' }));
  await user.click(await screen.findByRole('button', { name: 'Sửa câu hỏi This is false.' }));
  expect(screen.getByLabelText('Đáp án đúng')).toHaveValue('false');
  await user.clear(screen.getByLabelText('Điểm'));
  await user.type(screen.getByLabelText('Điểm'), '2');
  await user.click(screen.getByRole('button', { name: 'Lưu câu hỏi' }));
  await waitFor(() => expect(writes).toHaveLength(2));
  expect(writes[0]).toMatchObject({ method: 'POST', body: { correctAnswer: false, points: 1 } });
  expect(writes[1]).toMatchObject({
    method: 'PUT',
    questionId: demoId(860),
    body: { correctAnswer: false, points: 2 },
  });
});

it('keeps invalid readiness draft on 409 and locks navigation during the write', async () => {
  let patch: unknown;
  server.use(
    http.patch('*/api/v1/admin/assessments/:id', async ({ request }) => {
      patch = await request.json();
      await delay(150);
      return HttpResponse.json(
        { code: 'NOT_READY', message: 'Cần câu hỏi hợp lệ' },
        { status: 409 },
      );
    }),
  );
  const user = userEvent.setup();
  render(<AdminAssessmentsPage />);
  await user.click(await screen.findByRole('button', { name: 'Xem / sửa Bài tập tuần 8' }));
  await user.click(await screen.findByLabelText('Sẵn sàng nhận lượt làm mới'));
  await user.click(screen.getByRole('button', { name: 'Lưu cấu hình' }));
  expect(screen.getByRole('button', { name: 'Tạo bài mới' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Đóng chỉnh sửa' })).toBeDisabled();
  expect(await screen.findByText('Cần câu hỏi hợp lệ')).toBeInTheDocument();
  expect(patch).toEqual({ title: detail.title, timeLimitSeconds: null, ready: true });
  expect(screen.getByText('Bản nháp')).toBeInTheDocument();
  expect(screen.getByLabelText('Tên bài')).toHaveValue(detail.title);
});

it('requires an explicit single-choice answer, keeps option IDs and limits options to six', async () => {
  let payload: Schema<'SingleChoiceWrite'> | undefined;
  server.use(
    http.post('*/api/v1/admin/assessments/:id/questions', async ({ request }) => {
      payload = (await request.json()) as Schema<'SingleChoiceWrite'>;
      const q = { ...payload, questionId: demoId(861) };
      detail = { ...detail, questions: [q], questionCount: 1 };
      return HttpResponse.json(q, { status: 201 });
    }),
  );
  const user = userEvent.setup();
  render(<AdminAssessmentsPage />);
  await user.click(await screen.findByRole('button', { name: 'Xem / sửa Bài tập tuần 8' }));
  await user.click(await screen.findByRole('button', { name: 'Thêm câu hỏi' }));
  await user.type(screen.getByLabelText('Nội dung câu hỏi'), 'Choose one');
  await user.type(screen.getByLabelText('Lựa chọn 1'), 'A');
  await user.type(screen.getByLabelText('Lựa chọn 2'), 'B');
  await user.click(screen.getByRole('button', { name: 'Lưu câu hỏi' }));
  expect(
    await screen.findByText('Cần 2–6 lựa chọn khác nhau và chọn một đáp án đúng.'),
  ).toBeInTheDocument();
  expect(payload).toBeUndefined();
  for (let i = 3; i <= 6; i++) {
    await user.click(screen.getByRole('button', { name: 'Thêm lựa chọn' }));
    await user.type(screen.getByLabelText(`Lựa chọn ${i}`), `Option ${i}`);
  }
  expect(screen.getByRole('button', { name: 'Thêm lựa chọn' })).toBeDisabled();
  await user.click(screen.getByLabelText('Đáp án đúng 2'));
  await user.click(screen.getByRole('button', { name: 'Lưu câu hỏi' }));
  await waitFor(() => expect(payload).toBeDefined());
  expect(payload?.options).toHaveLength(6);
  expect(payload?.correctOptionId).toBe(payload?.options[1].optionId);
  expect(new Set(payload?.options.map((o) => o.optionId)).size).toBe(6);
});
