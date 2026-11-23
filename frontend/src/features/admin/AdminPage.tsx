import { useCallback, useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import { listAdmin, readAdmin, recordId, recordTitle, writeAdmin } from './api';
import type { AdminKind, AdminRecord, Draft } from './api';
import { useResource } from '../../shared/hooks/useResource';
import { Empty, ErrorState, FormError, Loading, PageHeading } from '../../shared/ui/States';

const labels: Record<AdminKind, string> = {
  users: 'Người dùng',
  courses: 'Khóa học',
  modules: 'Module',
  lessons: 'Bài học',
  vocabulary: 'Từ vựng',
  'learning-contents': 'Học liệu',
};
export function isAdminKind(value: string | undefined): value is AdminKind {
  return !!value && Object.hasOwn(labels, value);
}
export function AdminPage() {
  const { kind } = useParams();
  return isAdminKind(kind) ? (
    <ResourcePage key={kind} kind={kind} />
  ) : (
    <Empty title="Không tìm thấy mục quản trị." />
  );
}

export function ResourcePage({ kind }: { kind: AdminKind }) {
  const [filter, setFilter] = useState('');
  const [applied, setApplied] = useState('');
  const [selection, setSelection] = useState<string>();
  const [open, setOpen] = useState(false);
  const [success, setSuccess] = useState('');
  const [writing, setWriting] = useState(false);
  const load = useCallback(
    (signal: AbortSignal) => listAdmin(kind, applied, signal),
    [kind, applied],
  );
  const list = useResource(load);
  const parentKind = kind === 'modules' ? 'courses' : kind === 'lessons' ? 'modules' : 'lessons';
  const parents = useResource(
    useCallback(
      (signal: AbortSignal) =>
        kind === 'users' || kind === 'courses'
          ? Promise.resolve([] as AdminRecord[])
          : listAdmin(parentKind, '', signal),
      [kind, parentKind],
    ),
  );
  const canCreate = kind !== 'users' && kind !== 'vocabulary';
  return (
    <>
      <PageHeading eyebrow="QUẢN TRỊ NỘI DUNG" title={labels[kind]}>
        {kind === 'vocabulary'
          ? 'Xem và cập nhật từ đã nhập. Thêm từ mới bằng Import Excel.'
          : kind === 'users'
            ? 'Tìm tài khoản và quản lý thông tin, quyền truy cập của Learner.'
            : 'Tạo bản nháp, cập nhật nội dung và quản lý hiển thị.'}
      </PageHeading>
      <div className="admin-toolbar">
        {kind !== 'courses' && (
          <form
            className="admin-filter"
            onSubmit={(e) => {
              e.preventDefault();
              if (writing) return;
              setApplied(filter);
              setOpen(false);
            }}
          >
            <label>
              {kind === 'users'
                ? 'Tìm người dùng'
                : `Lọc theo ${kind === 'modules' ? 'khóa học' : kind === 'lessons' ? 'module' : 'bài học'}`}
              {kind === 'users' ? (
                <input
                  type="search"
                  disabled={writing}
                  value={filter}
                  maxLength={200}
                  onChange={(e) => setFilter(e.target.value)}
                  placeholder="Tên hoặc email"
                />
              ) : (
                <select
                  value={filter}
                  onChange={(e) => setFilter(e.target.value)}
                  disabled={writing || parents.loading || !!parents.error}
                >
                  <option value="">Tất cả</option>
                  {parents.data?.map((row) => (
                    <option key={recordId(row)} value={recordId(row)}>
                      {recordTitle(row)}
                    </option>
                  ))}
                </select>
              )}
            </label>
            <button className="button secondary" type="submit" disabled={writing}>
              Áp dụng
            </button>
          </form>
        )}
        <div className="admin-actions">
          <button className="button secondary" onClick={list.retry} disabled={writing}>
            Làm mới
          </button>
          {canCreate && (
            <button
              className="button"
              disabled={writing}
              onClick={() => {
                setSelection(undefined);
                setOpen(true);
                setSuccess('');
              }}
            >
              Tạo mới
            </button>
          )}
          {kind === 'vocabulary' && (
            <Link className="button" to="/admin/import">
              Import từ vựng
            </Link>
          )}
        </div>
      </div>
      {!!parents.error && <ErrorState error={parents.error} retry={parents.retry} />}
      {success && (
        <p className="notice success" role="status">
          {success}
        </p>
      )}
      {open && (
        <AdminEditor
          key={`${kind}-${selection || 'new'}`}
          kind={kind}
          id={selection}
          onPending={setWriting}
          onClose={() => setOpen(false)}
          onSaved={() => {
            setSuccess('Đã lưu thay đổi trên máy chủ.');
            setOpen(false);
            list.retry();
            parents.retry();
          }}
        />
      )}
      {list.loading ? (
        <Loading />
      ) : list.error ? (
        <ErrorState error={list.error} retry={list.retry} />
      ) : !list.data?.length ? (
        <Empty title={applied ? 'Không có kết quả phù hợp.' : 'Chưa có dữ liệu.'} />
      ) : (
        <div className="panel admin-table-wrap">
          <table className="admin-table">
            <caption>
              {labels[kind]} · {list.data.length} bản ghi
            </caption>
            <thead>
              <tr>
                <th scope="col">{kind === 'users' ? 'Tài khoản' : 'Tên / Nội dung'}</th>
                <th scope="col">Thông tin</th>
                <th scope="col">Trạng thái</th>
                <th scope="col">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {list.data.map((row) => (
                <tr key={recordId(row)}>
                  <th scope="row">
                    <strong>{recordTitle(row)}</strong>
                    <small className="admin-id">{recordId(row)}</small>
                  </th>
                  <td>
                    {'email' in row
                      ? row.email
                      : 'meaning' in row
                        ? row.meaning
                        : 'type' in row
                          ? row.type
                          : 'orderNo' in row
                            ? `Thứ tự ${row.orderNo}`
                            : 'Khóa học'}
                    {'availability' in row && row.availability === 'UNAVAILABLE' && (
                      <p className="notice">Tài nguyên không khả dụng</p>
                    )}
                  </td>
                  <td>
                    <span
                      className={`admin-status ${'published' in row && row.published ? 'published' : ''}`}
                    >
                      {'status' in row
                        ? row.status === 'ACTIVE'
                          ? 'Hoạt động'
                          : 'Đã chặn'
                        : 'published' in row && row.published
                          ? 'Đã xuất bản'
                          : 'Bản nháp'}
                    </span>
                  </td>
                  <td>
                    <button
                      className="text-button"
                      disabled={writing}
                      aria-label={`Xem / sửa ${recordTitle(row)}`}
                      onClick={() => {
                        setSelection(recordId(row));
                        setOpen(true);
                        setSuccess('');
                      }}
                    >
                      Xem / sửa
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function toDraft(row?: AdminRecord): Draft {
  const d: Draft = {
    fullName: '',
    status: 'ACTIVE',
    name: '',
    title: '',
    content: '',
    orderNo: '1',
    courseId: '',
    moduleId: '',
    lessonId: '',
    word: '',
    meaning: '',
    example: '',
    phonetic: '',
    type: 'GRAMMAR',
    text: '',
    resourceUrl: '',
    published: 'false',
  };
  for (const [key, value] of Object.entries(row || {}))
    d[key] = value === null ? '' : String(value);
  return d;
}

function AdminEditor({
  kind,
  id,
  onClose,
  onSaved,
  onPending,
}: {
  kind: AdminKind;
  id?: string;
  onClose: () => void;
  onSaved: () => void;
  onPending: (value: boolean) => void;
}) {
  const detail = useResource(
    useCallback(
      (signal: AbortSignal) => (id ? readAdmin(kind, id, signal) : Promise.resolve(undefined)),
      [kind, id],
    ),
  );
  if (detail.loading) return <Loading text="Đang tải bản ghi…" />;
  if (detail.error)
    return (
      <section className="panel">
        <ErrorState error={detail.error} retry={detail.retry} />
        <button className="text-button" onClick={onClose}>
          Đóng chỉnh sửa
        </button>
      </section>
    );
  return (
    <EditorForm
      key={recordIdOrNew(detail.data)}
      kind={kind}
      id={id}
      row={detail.data}
      onClose={onClose}
      onSaved={onSaved}
      onPending={onPending}
    />
  );
}
function recordIdOrNew(row?: AdminRecord) {
  return row ? recordId(row) : 'new';
}

function EditorForm({
  kind,
  id,
  row,
  onClose,
  onSaved,
  onPending,
}: {
  kind: AdminKind;
  id?: string;
  row?: AdminRecord;
  onClose: () => void;
  onSaved: () => void;
  onPending: (value: boolean) => void;
}) {
  const [draft, setDraft] = useState(() => toDraft(row));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const lock = useRef(false);
  const mounted = useRef(true);
  const titleRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    mounted.current = true;
    titleRef.current?.focus();
    return () => {
      mounted.current = false;
    };
  }, []);
  const parentKind = kind === 'modules' ? 'courses' : kind === 'lessons' ? 'modules' : 'lessons';
  const needsParent = !id && ['modules', 'lessons', 'learning-contents'].includes(kind);
  const parents = useResource(
    useCallback(
      (signal: AbortSignal) =>
        needsParent ? listAdmin(parentKind, '', signal) : Promise.resolve([] as AdminRecord[]),
      [needsParent, parentKind],
    ),
  );
  const set = (key: string, value: string) => setDraft((prev) => ({ ...prev, [key]: value }));
  const text = (
    key: string,
    label: string,
    maxLength: number,
    required = true,
    multiline = false,
  ) => (
    <label key={key}>
      {label}
      {multiline ? (
        <textarea
          name={key}
          required={required}
          maxLength={maxLength}
          value={draft[key]}
          onChange={(e) => set(key, e.target.value)}
          rows={5}
        />
      ) : (
        <input
          name={key}
          required={required}
          maxLength={maxLength}
          value={draft[key]}
          onChange={(e) => set(key, e.target.value)}
        />
      )}
    </label>
  );
  const readonlyAdmin = kind === 'users' && row && 'role' in row && row.role === 'ADMIN';
  async function submit(e: FormEvent) {
    e.preventDefault();
    if (lock.current || readonlyAdmin) return;
    const requiredText =
      kind === 'courses'
        ? ['name']
        : kind === 'users'
          ? []
          : kind === 'vocabulary'
            ? ['word', 'meaning']
            : kind === 'lessons'
              ? ['title', 'content']
              : kind === 'learning-contents'
                ? ['title', draft.type === 'LISTENING' ? 'resourceUrl' : 'text']
                : ['title'];
    if (requiredText.some((key) => !draft[key]?.trim())) {
      setError(new Error('Nội dung bắt buộc không được chỉ chứa khoảng trắng.'));
      return;
    }
    if (
      ['modules', 'lessons'].includes(kind) &&
      (!Number.isSafeInteger(Number(draft.orderNo)) || Number(draft.orderNo) < 1)
    ) {
      setError(new Error('Thứ tự phải là số nguyên dương.'));
      return;
    }
    lock.current = true;
    setBusy(true);
    onPending(true);
    setError(undefined);
    try {
      await writeAdmin(kind, id, draft);
      if (mounted.current) onSaved();
    } catch (e) {
      if (mounted.current) setError(e);
    } finally {
      lock.current = false;
      if (mounted.current) {
        setBusy(false);
        onPending(false);
      }
    }
  }
  return (
    <section className="panel admin-editor" aria-label="Chỉnh sửa bản ghi">
      <div className="between">
        <h2 tabIndex={-1} ref={titleRef}>
          {id ? 'Chi tiết và chỉnh sửa' : 'Tạo bản nháp mới'}
        </h2>
        <button className="text-button" disabled={busy} onClick={onClose}>
          Đóng chỉnh sửa
        </button>
      </div>
      {readonlyAdmin ? (
        <p className="notice">Tài khoản Admin chỉ đọc tại đây.</p>
      ) : (
        <p className="muted">
          {id
            ? 'Cấp cha và định danh được giữ nguyên.'
            : 'Bản ghi mới chưa xuất bản. Hoàn thiện nội dung trước khi bật hiển thị.'}
        </p>
      )}
      {row && (
        <p className="admin-id">
          ID: {recordId(row)}
          {'courseId' in row && ` · Course: ${row.courseId}`}
          {'moduleId' in row && ` · Module: ${row.moduleId}`}
          {'lessonId' in row && ` · Lesson: ${row.lessonId}`}
        </p>
      )}
      <FormError error={error} />
      {!!parents.error && <ErrorState error={parents.error} retry={parents.retry} />}
      <form onSubmit={(e) => void submit(e)} aria-busy={busy}>
        <fieldset disabled={busy || !!readonlyAdmin} className="admin-form-grid">
          {needsParent && (
            <label>
              {kind === 'modules' ? 'Khóa học' : kind === 'lessons' ? 'Module' : 'Bài học'}
              <select
                required
                value={
                  draft[
                    kind === 'modules' ? 'courseId' : kind === 'lessons' ? 'moduleId' : 'lessonId'
                  ]
                }
                onChange={(e) =>
                  set(
                    kind === 'modules' ? 'courseId' : kind === 'lessons' ? 'moduleId' : 'lessonId',
                    e.target.value,
                  )
                }
                disabled={parents.loading || !!parents.error}
              >
                <option value="">Chọn cấp cha</option>
                {parents.data?.map((p) => (
                  <option key={recordId(p)} value={recordId(p)}>
                    {recordTitle(p)}
                  </option>
                ))}
              </select>
            </label>
          )}
          {kind === 'users' && (
            <>
              <label>
                Email
                <input value={row && 'email' in row ? row.email : ''} readOnly />
              </label>
              {text('fullName', 'Họ tên', 100, false)}
              <label>
                Trạng thái tài khoản
                <select value={draft.status} onChange={(e) => set('status', e.target.value)}>
                  <option value="ACTIVE">Hoạt động</option>
                  <option value="BLOCKED">Đã chặn</option>
                </select>
              </label>
            </>
          )}
          {kind === 'courses' && text('name', 'Tên khóa học', 200)}
          {['modules', 'lessons', 'learning-contents'].includes(kind) &&
            text('title', 'Tiêu đề', 200)}
          {['modules', 'lessons'].includes(kind) && (
            <label>
              Thứ tự
              <input
                type="number"
                min={1}
                step={1}
                required
                value={draft.orderNo}
                onChange={(e) => set('orderNo', e.target.value)}
              />
            </label>
          )}
          {kind === 'lessons' && text('content', 'Nội dung bài học', 20000, true, true)}
          {kind === 'vocabulary' && (
            <>
              {text('word', 'Từ vựng', 128)}
              {text('meaning', 'Nghĩa', 500)}
              {text('phonetic', 'Phiên âm', 128, false)}
              {text('example', 'Ví dụ', 1000, false, true)}
            </>
          )}
          {kind === 'learning-contents' && (
            <>
              <label>
                Loại học liệu
                <select
                  value={draft.type}
                  disabled={!!id}
                  onChange={(e) => set('type', e.target.value)}
                >
                  <option value="GRAMMAR">Grammar</option>
                  <option value="LISTENING">Listening</option>
                  <option value="READING">Reading</option>
                </select>
              </label>
              {draft.type === 'LISTENING' ? (
                <label>
                  URL nghe HTTPS
                  <input
                    type="url"
                    required
                    pattern="https://.*"
                    maxLength={2048}
                    value={draft.resourceUrl}
                    onChange={(e) => set('resourceUrl', e.target.value)}
                  />
                </label>
              ) : (
                text('text', 'Nội dung học liệu', 20000, true, true)
              )}
            </>
          )}
          {id && kind !== 'users' && (
            <label className="admin-checkbox">
              <input
                type="checkbox"
                checked={draft.published === 'true'}
                onChange={(e) => set('published', String(e.target.checked))}
              />
              Xuất bản
            </label>
          )}
        </fieldset>
        {kind === 'lessons' && (
          <p className="notice">
            Xuất bản bài học cần Exercise cuối bài sẵn sàng. Nếu kiểm tra không đạt hoặc dịch vụ
            gián đoạn, thay đổi chưa được xác nhận.
          </p>
        )}
        {!readonlyAdmin && (
          <button
            className="button"
            type="submit"
            disabled={busy || (needsParent && (parents.loading || !!parents.error))}
          >
            {busy ? 'Đang lưu…' : 'Lưu thay đổi'}
          </button>
        )}
      </form>
    </section>
  );
}
