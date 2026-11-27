import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiFailure } from '../../shared/api/client';
import type { Schema } from '../../shared/api/client';
import { useResource } from '../../shared/hooks/useResource';
import { ErrorState, FormError, Loading, PageHeading } from '../../shared/ui/States';
import { downloadTemplate, listAdmin, recordId, recordTitle, uploadImport } from './api';
import type { ImportKind } from './api';

export function ImportPage() {
  const [kind, setKind] = useState<ImportKind>('vocabulary');
  const [pending, setPending] = useState(false);
  return (
    <>
      <PageHeading eyebrow="QUẢN TRỊ / IMPORT" title="Đưa học liệu vào đúng chỗ.">
        Tải mẫu, chọn đích và nhập một file mỗi lần. Dữ liệu trùng được báo lỗi để kiểm tra lại.
      </PageHeading>
      <div className="admin-import-tabs" role="group" aria-label="Loại import">
        <button
          disabled={pending}
          className={`button ${kind === 'vocabulary' ? '' : 'secondary'}`}
          aria-pressed={kind === 'vocabulary'}
          onClick={() => setKind('vocabulary')}
        >
          Vocabulary Excel
        </button>
        <button
          disabled={pending}
          className={`button ${kind === 'lessons' ? '' : 'secondary'}`}
          aria-pressed={kind === 'lessons'}
          onClick={() => setKind('lessons')}
        >
          Lesson Word
        </button>
      </div>
      <ImportForm key={kind} kind={kind} onPending={setPending} />
    </>
  );
}

function ImportForm({
  kind,
  onPending,
}: {
  kind: ImportKind;
  onPending: (value: boolean) => void;
}) {
  const targets = useResource(
    useCallback(
      (signal: AbortSignal) => listAdmin(kind === 'vocabulary' ? 'lessons' : 'modules', '', signal),
      [kind],
    ),
  );
  const [target, setTarget] = useState('');
  const [orderNo, setOrderNo] = useState('1');
  const [file, setFile] = useState<File>();
  const [outcome, setOutcome] = useState<Schema<'ImportOutcome'>>();
  const [error, setError] = useState<unknown>();
  const [uncertain, setUncertain] = useState(false);
  const [busy, setBusy] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [templateError, setTemplateError] = useState<unknown>();
  const lock = useRef(false);
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);
  const extension = kind === 'vocabulary' ? '.xlsx' : '.docx';
  useEffect(() => {
    if (!busy) return;
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [busy]);
  async function template() {
    setDownloading(true);
    setTemplateError(undefined);
    try {
      await downloadTemplate(kind);
    } catch (e) {
      setTemplateError(e);
    } finally {
      setDownloading(false);
    }
  }
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (lock.current) return;
    setError(undefined);
    setUncertain(false);
    setOutcome(undefined);
    if (!file || !file.name.toLowerCase().endsWith(extension)) {
      setError(new Error(`Chọn đúng file ${extension}.`));
      return;
    }
    if (file.size > 5 * 1024 * 1024 || file.size === 0) {
      setError(new Error('File phải có dữ liệu và không quá 5 MiB.'));
      return;
    }
    if (
      !target ||
      (kind === 'lessons' && (!Number.isSafeInteger(Number(orderNo)) || Number(orderNo) < 1))
    ) {
      setError(new Error('Chọn đích và thứ tự nguyên dương hợp lệ.'));
      return;
    }
    lock.current = true;
    setBusy(true);
    onPending(true);
    try {
      const result = await uploadImport(kind, file, target, orderNo);
      if (mounted.current) setOutcome(result);
    } catch (e) {
      if (mounted.current) {
        setError(e);
        if (e instanceof ApiFailure) setOutcome(e.importOutcome);
        setUncertain(!(e instanceof ApiFailure) || e.status === 0 || e.status >= 500);
      }
    } finally {
      lock.current = false;
      if (mounted.current) {
        setBusy(false);
        onPending(false);
      }
    }
  }
  return (
    <div className="admin-import-grid">
      <section className="panel admin-editor">
        <h2>{kind === 'vocabulary' ? 'Import Vocabulary' : 'Import Lesson'}</h2>
        <button className="button secondary" disabled={downloading} onClick={() => void template()}>
          {downloading ? 'Đang tải…' : `Tải mẫu ${extension}`}
        </button>
        <FormError error={templateError} />
        {targets.loading ? (
          <Loading />
        ) : targets.error ? (
          <ErrorState error={targets.error} retry={targets.retry} />
        ) : (
          <form onSubmit={(e) => void submit(e)} aria-busy={busy} className="stack">
            <fieldset disabled={busy} className="stack">
              <label>
                {kind === 'vocabulary' ? 'Bài học đích' : 'Module đích'}
                <select
                  value={target}
                  required
                  onChange={(e) => {
                    setTarget(e.target.value);
                    setOutcome(undefined);
                    setError(undefined);
                    setUncertain(false);
                  }}
                >
                  <option value="">Chọn đích import</option>
                  {targets.data?.map((t) => (
                    <option key={recordId(t)} value={recordId(t)}>
                      {recordTitle(t)}
                    </option>
                  ))}
                </select>
              </label>
              {kind === 'lessons' && (
                <label>
                  Thứ tự bài học
                  <input
                    type="number"
                    min={1}
                    step={1}
                    required
                    value={orderNo}
                    onChange={(e) => {
                      setOrderNo(e.target.value);
                      setOutcome(undefined);
                      setError(undefined);
                      setUncertain(false);
                    }}
                  />
                </label>
              )}
              <label>
                File {extension}
                <input
                  name="importFile"
                  type="file"
                  required
                  accept={extension}
                  onChange={(e) => {
                    setFile(e.target.files?.[0]);
                    setOutcome(undefined);
                    setError(undefined);
                    setUncertain(false);
                  }}
                />
              </label>
              {file && (
                <p className="muted">
                  {file.name} · {(file.size / 1024).toFixed(1)} KiB
                </p>
              )}
            </fieldset>
            <FormError error={error} />
            {uncertain && !outcome && (
              <p className="notice">
                Chưa xác định được kết quả import. Kiểm tra danh sách đích trước khi gửi lại nếu kết
                nối bị ngắt.
              </p>
            )}
            <button className="button" disabled={busy || !file || !target} type="submit">
              {busy ? 'Đang import…' : 'Import file'}
            </button>
          </form>
        )}
      </section>
      <aside className="panel import-guide">
        <p className="eyebrow">CHUẨN BỊ FILE</p>
        <h2>{kind === 'vocabulary' ? 'Một dòng, một từ và nghĩa.' : 'Một file, một bài học.'}</h2>
        <p>
          {kind === 'vocabulary'
            ? 'Dùng sheet VOCABULARY; các cột word, meaning, example, phonetic. word và meaning bắt buộc. Không dùng công thức hoặc ô gộp.'
            : 'Đoạn không rỗng đầu tiên dùng style Title, tiếp theo là nội dung dạng văn bản. Không nhúng bảng, ảnh, hyperlink hoặc macro.'}
        </p>
        <p>Một mục lỗi sẽ từ chối toàn file. Dữ liệu trùng không được bỏ qua hoặc ghi đè.</p>
        <p>
          {kind === 'vocabulary'
            ? 'Tối đa 1.000 dòng dữ liệu và 5 MiB.'
            : 'Bài mới ở trạng thái bản nháp; cấu hình Exercise trước khi xuất bản. Tối đa 5 MiB.'}
        </p>
      </aside>
      {outcome && <ImportOutcome outcome={outcome} />}
    </div>
  );
}

export function ImportOutcome({ outcome }: { outcome: Schema<'ImportOutcome'> }) {
  const rejected = outcome.status === 'REJECTED';
  const consistent = rejected
    ? outcome.createdCount === 0 && outcome.createdIds.length === 0
    : outcome.invalidCount === 0 &&
      outcome.errors.length === 0 &&
      outcome.createdCount === outcome.createdIds.length;
  return (
    <section className="panel import-outcome" aria-label="Kết quả import" role="status">
      <h2>
        {!consistent
          ? 'Kết quả cần kiểm tra lại'
          : rejected
            ? 'File đã bị từ chối'
            : 'Import thành công'}
      </h2>
      {!consistent && (
        <p className="notice">
          Phản hồi máy chủ chưa khớp quy tắc import. Kiểm tra dữ liệu đích trước khi gửi lại.
        </p>
      )}
      <dl className="import-counts">
        <div>
          <dt>Tổng mục</dt>
          <dd>{outcome.totalItems ?? 'Không đọc được'}</dd>
        </div>
        <div>
          <dt>Hợp lệ</dt>
          <dd>{outcome.validCount ?? '—'}</dd>
        </div>
        <div>
          <dt>Không hợp lệ</dt>
          <dd>{outcome.invalidCount ?? '—'}</dd>
        </div>
        <div>
          <dt>Đã tạo</dt>
          <dd>{outcome.createdCount}</dd>
        </div>
      </dl>
      {consistent && rejected && <p>Không tạo bản ghi nào từ file này. Sửa file rồi import lại.</p>}
      {outcome.errors.length > 0 && (
        <div className="admin-table-wrap">
          <table className="admin-table">
            <caption>Chi tiết lỗi trong file</caption>
            <thead>
              <tr>
                <th scope="col">Vị trí</th>
                <th scope="col">Trường</th>
                <th scope="col">Mã lỗi</th>
                <th scope="col">Nội dung</th>
              </tr>
            </thead>
            <tbody>
              {outcome.errors.map((e, i) => (
                <tr key={i}>
                  <td>{e.row ? `Dòng ${e.row}` : e.paragraph ? `Đoạn ${e.paragraph}` : 'File'}</td>
                  <td>{e.field}</td>
                  <td>{e.code}</td>
                  <td>{e.message}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
