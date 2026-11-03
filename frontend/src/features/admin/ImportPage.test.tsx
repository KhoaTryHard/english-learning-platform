import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { demoStore } from '../../../tests/setup';
import { demoId, lessons, modules } from '../../mocks/data';
import { ImportPage } from './ImportPage';
import { ApiFailure } from '../../shared/api/client';
import type { Schema } from '../../shared/api/client';
import { uploadImport } from './api';

// UI outcomes are isolated from jsdom's File serialization. Real multipart/CSRF and
// original OOXML files are exercised through Chromium against the local LIVE stack.
vi.mock('./api', async (original) => ({
  ...(await original<typeof import('./api')>()),
  uploadImport: vi.fn(),
}));
const upload = vi.mocked(uploadImport);
beforeEach(() => {
  demoStore.currentUserId = demoId(901);
  upload.mockReset();
});
const rejected: Schema<'ImportOutcome'> = {
  status: 'REJECTED',
  totalItems: 2,
  validCount: 1,
  invalidCount: 1,
  createdCount: 0,
  createdIds: [],
  errors: [{ row: 3, paragraph: null, field: 'meaning', code: 'REQUIRED', message: 'Thiếu nghĩa' }],
};
const accepted: Schema<'ImportOutcome'> = {
  status: 'SUCCESS',
  totalItems: 1,
  validCount: 1,
  invalidCount: 0,
  createdCount: 1,
  createdIds: [demoId(990)],
  errors: [],
};
async function chooseFile(
  user: ReturnType<typeof userEvent.setup>,
  kind: 'vocabulary' | 'lessons' = 'vocabulary',
) {
  if (kind === 'lessons') await user.click(screen.getByRole('button', { name: 'Lesson Word' }));
  const label = kind === 'lessons' ? 'Module đích' : 'Bài học đích';
  await waitFor(() => expect(screen.getByLabelText(label)).toBeEnabled());
  await user.selectOptions(
    screen.getByLabelText(label),
    kind === 'lessons' ? modules[0].moduleId : lessons[0].lessonId,
  );
  const ext = kind === 'lessons' ? 'docx' : 'xlsx';
  const file = new File(['UI fixture only'], `fixture.${ext}`);
  await user.upload(screen.getByLabelText(`File .${ext}`), file);
  // jsdom's native validation does not recognize its uploaded FileList.
  // Browser tests retain required validation and submit actual file bytes.
  (screen.getByLabelText(`File .${ext}`) as HTMLInputElement).form!.noValidate = true;
  return file;
}
it('passes the selected vocabulary file/target and presents atomic 422 row errors', async () => {
  upload.mockRejectedValue(
    new ApiFailure(422, 'IMPORT_REJECTED', 'File có lỗi', [], 'test', rejected),
  );
  const user = userEvent.setup();
  render(<ImportPage />);
  const file = await chooseFile(user);
  await user.click(screen.getByRole('button', { name: 'Import file' }));
  await screen.findByRole('heading', { name: 'File đã bị từ chối' });
  expect(upload).toHaveBeenCalledWith('vocabulary', file, lessons[0].lessonId, '1');
  expect(screen.getByText('Dòng 3')).toBeVisible();
  expect(screen.getByText('Thiếu nghĩa')).toBeVisible();
  expect(
    screen.getByText('Không tạo bản ghi nào từ file này. Sửa file rồi import lại.'),
  ).toBeVisible();
  expect(screen.queryByText('Import thành công')).not.toBeInTheDocument();
});
it('passes Lesson module/order and prevents switching or duplicate upload while pending', async () => {
  let complete!: (value: Schema<'ImportOutcome'>) => void;
  upload.mockImplementation(
    () =>
      new Promise((resolve) => {
        complete = resolve;
      }),
  );
  const user = userEvent.setup();
  render(<ImportPage />);
  const file = await chooseFile(user, 'lessons');
  await user.clear(screen.getByLabelText('Thứ tự bài học'));
  await user.type(screen.getByLabelText('Thứ tự bài học'), '4');
  await user.dblClick(screen.getByRole('button', { name: 'Import file' }));
  expect(screen.getByRole('button', { name: 'Vocabulary Excel' })).toBeDisabled();
  expect(upload).toHaveBeenCalledTimes(1);
  expect(upload).toHaveBeenCalledWith('lessons', file, modules[0].moduleId, '4');
  complete(accepted);
  await screen.findByText('Import thành công');
});
it('does not claim rollback or automatically retry when the result is unknown', async () => {
  upload.mockRejectedValue(new ApiFailure(0, 'NETWORK_ERROR', 'Mất kết nối'));
  const user = userEvent.setup();
  render(<ImportPage />);
  await chooseFile(user);
  await user.click(screen.getByRole('button', { name: 'Import file' }));
  await screen.findByText(/Chưa xác định được kết quả import/);
  expect(upload).toHaveBeenCalledTimes(1);
  expect(screen.queryByText('Import thành công')).not.toBeInTheDocument();
  expect(screen.queryByText(/Không tạo bản ghi nào/)).not.toBeInTheDocument();
});
it.each([409, 413, 415])('preserves file-level rejection details on HTTP %s', async (status) => {
  upload.mockRejectedValue(
    new ApiFailure(status, 'IMPORT_REJECTED', 'Không nhận file này', [], 'test', {
      ...rejected,
      totalItems: null,
      validCount: null,
      invalidCount: null,
      errors: [
        {
          row: null,
          paragraph: null,
          field: 'file',
          code: 'INVALID_FILE',
          message: 'Sai container hoặc duplicate',
        },
      ],
    }),
  );
  const user = userEvent.setup();
  render(<ImportPage />);
  await chooseFile(user);
  await user.click(screen.getByRole('button', { name: 'Import file' }));
  await screen.findByText('File đã bị từ chối');
  expect(screen.getByText('Sai container hoặc duplicate')).toBeVisible();
  expect(
    within(screen.getByRole('status', { name: 'Kết quả import' })).getByText('Không đọc được'),
  ).toBeVisible();
});
it('flags a response violating rejection atomicity instead of claiming success', async () => {
  upload.mockResolvedValue({ ...rejected, createdCount: 1, createdIds: [demoId(777)] });
  const user = userEvent.setup();
  render(<ImportPage />);
  await chooseFile(user);
  await user.click(screen.getByRole('button', { name: 'Import file' }));
  await screen.findByText('Kết quả cần kiểm tra lại');
  expect(screen.queryByText('Import thành công')).not.toBeInTheDocument();
});
