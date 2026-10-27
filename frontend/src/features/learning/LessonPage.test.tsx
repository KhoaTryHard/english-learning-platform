import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { demoStore, server } from '../../../tests/setup';
import { contents, demoId, lessons, vocabulary } from '../../mocks/data';
import { api } from '../../shared/api/platform';
import type { Schema } from '../../shared/api/client';
import { LessonPage } from './LessonPage';

const lessonId = demoId(201);

function payload(): Schema<'LessonLearning'> {
  return structuredClone({
    lesson: lessons[0],
    vocabulary: vocabulary.filter((word) => word.lessonId === lessonId),
    contents: contents.filter((item) => item.lessonId === lessonId),
    exerciseId: demoId(501),
  });
}

function serveLesson(data: Schema<'LessonLearning'>) {
  server.use(http.get('*/api/v1/lessons/:id', () => HttpResponse.json(data)));
}

function renderLesson() {
  return render(
    <MemoryRouter initialEntries={[`/lessons/${lessonId}`]}>
      <Routes>
        <Route path="/lessons/:lessonId" element={<LessonPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

it.each([
  [403, 'Bạn chưa có quyền truy cập.'],
  [404, 'Nội dung không còn khả dụng.'],
])('does not expose lesson content when the owner API returns %s', async (status, title) => {
  server.use(
    http.get('*/api/v1/lessons/:id', () =>
      HttpResponse.json(
        { code: 'CONTENT_UNAVAILABLE', message: 'Owner rejected this lesson', errors: [] },
        { status },
      ),
    ),
  );
  renderLesson();
  await screen.findByRole('heading', { name: title });
  expect(screen.queryByRole('heading', { name: lessons[0].title })).not.toBeInTheDocument();
  expect(screen.queryByRole('button', { name: 'Lưu từ' })).not.toBeInTheDocument();
  expect(screen.queryByRole('link', { name: /Đến bài tập cuối bài/ })).not.toBeInTheDocument();
});

it('shows missing content explicitly and does not create a completion just by viewing', async () => {
  serveLesson({ ...payload(), vocabulary: [], contents: [] });
  renderLesson();
  await screen.findByRole('heading', { name: 'Chưa có từ vựng trong bài này.' });
  expect(screen.getByText('Bài học chưa có nội dung ngữ pháp.')).toBeVisible();
  expect(screen.getByText('Bài học chưa có nội dung nghe.')).toBeVisible();
  expect(screen.getByText('Bài học chưa có nội dung đọc.')).toBeVisible();
  expect(screen.getByRole('link', { name: /Đến bài tập cuối bài/ })).toHaveAttribute(
    'href',
    `/assessments/${demoId(501)}`,
  );
  expect(Object.keys(demoStore.attempts)).toHaveLength(0);
  expect(Object.keys(demoStore.results)).toHaveLength(0);
  expect(demoStore.completed[demoId(900)] || []).toHaveLength(0);
});

it('keeps a Content outage visible and retries only when the learner requests it', async () => {
  let calls = 0;
  server.use(
    http.get('*/api/v1/lessons/:id', () => {
      calls += 1;
      return calls === 1
        ? HttpResponse.json(
            {
              code: 'DEPENDENCY_UNAVAILABLE',
              message: 'Content is temporarily unavailable',
              traceId: 'lesson-outage-test',
              errors: [],
            },
            { status: 503 },
          )
        : HttpResponse.json(payload());
    }),
  );
  const user = userEvent.setup();
  renderLesson();
  await screen.findByRole('heading', { name: 'Dịch vụ đang tạm gián đoạn.' });
  expect(screen.getByText('Mã tra cứu: lesson-outage-test')).toBeVisible();
  expect(calls).toBe(1);
  await user.click(screen.getByRole('button', { name: 'Thử lại' }));
  await screen.findByRole('heading', { name: lessons[0].title, level: 1 });
  expect(calls).toBe(2);
});

it('does not render an audio source for an unavailable Listening item', async () => {
  const data = payload();
  data.contents = data.contents.filter((item) => item.type === 'LISTENING');
  data.contents[0].resourceUrl = 'https://media.example.invalid/private-audio.mp3';
  data.contents[0].availability = 'UNAVAILABLE';
  serveLesson(data);
  const { container } = renderLesson();
  await screen.findByText('Tài nguyên hiện không khả dụng.');
  expect(container.querySelector('audio')).toBeNull();
});

it('shows an explicit resource error when an available audio cannot play', async () => {
  const data = payload();
  data.contents = data.contents.filter((item) => item.type === 'LISTENING');
  data.contents[0].resourceUrl = 'https://media.example.invalid/audio.mp3';
  data.contents[0].availability = 'AVAILABLE';
  serveLesson(data);
  const { container } = renderLesson();
  await screen.findByRole('heading', { name: 'Ordering dinner' });
  const audio = container.querySelector('audio');
  expect(audio).not.toBeNull();
  fireEvent.error(audio!);
  expect(screen.getByText('Chưa phát được tài nguyên nghe. Hãy thử lại sau.')).toBeVisible();
  expect(container.querySelector('audio')).toBeNull();
});

it('does not pretend a word was saved when the Content write fails', async () => {
  let writes = 0;
  server.use(
    http.put('*/api/v1/me/vocabulary/:id', () => {
      writes += 1;
      return HttpResponse.json(
        { code: 'DEPENDENCY_UNAVAILABLE', message: 'Save word failed', errors: [] },
        { status: 503 },
      );
    }),
  );
  const user = userEvent.setup();
  renderLesson();
  const save = await screen.findByRole('button', { name: 'Lưu từ' });
  await waitFor(() => expect(save).toBeEnabled());
  await user.click(save);
  await screen.findByText('Save word failed');
  expect(screen.queryByRole('button', { name: 'Bỏ lưu từ' })).not.toBeInTheDocument();
  expect(demoStore.saved[demoId(900)] || []).toHaveLength(0);
  expect(writes).toBe(1);
});

it('keeps the saved relation visible when Remove fails', async () => {
  await api.saveWord(vocabulary[0].vocabularyId);
  server.use(
    http.delete('*/api/v1/me/vocabulary/:id', () =>
      HttpResponse.json(
        { code: 'DEPENDENCY_UNAVAILABLE', message: 'Remove word failed', errors: [] },
        { status: 503 },
      ),
    ),
  );
  const user = userEvent.setup();
  renderLesson();
  const remove = await screen.findByRole('button', { name: 'Bỏ lưu từ' });
  await waitFor(() => expect(remove).toBeEnabled());
  await user.click(remove);
  await screen.findByText('Remove word failed');
  expect(screen.getByRole('button', { name: 'Bỏ lưu từ' })).toBeEnabled();
  expect(demoStore.saved[demoId(900)]).toHaveLength(1);
});
