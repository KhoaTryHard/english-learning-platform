import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { demoStore, server } from '../../../tests/setup';
import { demoId } from '../../mocks/data';
import { VocabularyPage } from './VocabularyPage';
import type { Schema } from '../../shared/api/client';

const saved: Schema<'SavedVocabulary'> = {
  vocabularyId: demoId(301),
  addedAt: new Date().toISOString(),
  availability: 'UNAVAILABLE',
  content: null,
};
const show = () =>
  render(
    <MemoryRouter>
      <VocabularyPage />
    </MemoryRouter>,
  );
it('keeps an unavailable relation removable and does not drop it when Remove fails', async () => {
  demoStore.saved[demoId(900)] = [saved];
  let removes = 0;
  server.use(
    http.delete('*/api/v1/me/vocabulary/:id', () => {
      if (++removes === 1)
        return HttpResponse.json(
          { code: 'UNAVAILABLE', message: 'Thử lại sau', errors: [] },
          { status: 503 },
        );
      demoStore.saved[demoId(900)] = [];
      return new HttpResponse(null, { status: 204 });
    }),
  );
  const user = userEvent.setup();
  show();
  await screen.findByRole('heading', { name: 'Từ vựng không khả dụng' });
  await user.click(screen.getByRole('button', { name: 'Bỏ lưu' }));
  await screen.findByText('Thử lại sau');
  expect(screen.getByRole('button', { name: 'Bỏ lưu' })).toBeEnabled();
  expect(demoStore.saved[demoId(900)]).toHaveLength(1);
  await user.click(screen.getByRole('button', { name: 'Bỏ lưu' }));
  await screen.findByText('Bạn chưa lưu từ nào.');
  expect(removes).toBe(2);
});
it('uses the current source, fetches every page and distinguishes no search match', async () => {
  const pages: number[] = [];
  server.use(
    http.get('*/api/v1/me/vocabulary', ({ request }) => {
      const page = Number(new URL(request.url).searchParams.get('page'));
      pages.push(page);
      return HttpResponse.json({
        items: Array.from({ length: page ? 1 : 100 }, (_, i) => ({
          ...saved,
          vocabularyId: demoId(1000 + 100 * page + i),
          availability: 'AVAILABLE',
          content: {
            word: page ? 'updated source' : `first source ${i}`,
            meaning: 'Nghĩa mới',
            phonetic: null,
            example: null,
          },
        })),
        page,
        size: 100,
        totalElements: 101,
        totalPages: 2,
      });
    }),
  );
  const user = userEvent.setup();
  show();
  await screen.findByRole('heading', { name: 'updated source' });
  expect(pages).toEqual([0, 1]);
  await user.type(screen.getByLabelText('Tìm từ đã lưu'), 'absent');
  await screen.findByText('Không tìm thấy từ phù hợp.');
  expect(screen.queryByText('Bạn chưa lưu từ nào.')).not.toBeInTheDocument();
});
it('shows a source outage instead of presenting an empty collection and can retry', async () => {
  let count = 0;
  server.use(
    http.get('*/api/v1/me/vocabulary', () =>
      ++count === 1
        ? HttpResponse.json(
            { code: 'UNAVAILABLE', message: 'Nguồn tạm lỗi', errors: [] },
            { status: 503 },
          )
        : HttpResponse.json({ items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }),
    ),
  );
  const user = userEvent.setup();
  show();
  await screen.findByText('Dịch vụ đang tạm gián đoạn.');
  expect(screen.queryByText('Bạn chưa lưu từ nào.')).not.toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: 'Thử lại' }));
  await screen.findByText('Bạn chưa lưu từ nào.');
});
