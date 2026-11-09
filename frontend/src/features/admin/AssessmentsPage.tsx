import { useCallback, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { allPages, mutate, request, type Schema } from '../../shared/api/client';
import { useResource } from '../../shared/hooks/useResource';
import { Empty, ErrorState, FormError, Loading, PageHeading } from '../../shared/ui/States';

type Assessment = Schema<'Assessment'>;
type Detail = Schema<'AssessmentDetail'>;
type Question = Schema<'AdminQuestion'>;
const path = (id: string) => `/admin/assessments/${encodeURIComponent(id)}`;

export function AdminAssessmentsPage() {
  const [courseId, setCourseId] = useState('');
  const [type, setType] = useState('');
  const [filter, setFilter] = useState('');
  const [selected, setSelected] = useState<string>();
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const list = useResource(
    useCallback(
      (signal: AbortSignal) => allPages<Assessment>(`/admin/assessments${filter}`, signal),
      [filter],
    ),
  );
  const courses = useResource(
    useCallback((signal: AbortSignal) => allPages<Schema<'Course'>>('/admin/courses', signal), []),
  );
  return (
    <>
      <PageHeading title="Bài luyện tập và kiểm tra">
        Quản lý Exercise, Quiz, Test và câu hỏi. Lượt làm cũ giữ nguyên nội dung tại thời điểm bắt
        đầu.
      </PageHeading>
      <form
        className="admin-toolbar assessment-toolbar"
        onSubmit={(e) => {
          e.preventDefault();
          if (busy) return;
          const q = new URLSearchParams();
          if (courseId) q.set('courseId', courseId);
          if (type) q.set('type', type);
          setFilter(q.size ? `?${q}` : '');
          setSelected(undefined);
          setCreating(false);
        }}
      >
        <label>
          Khóa học
          <select
            disabled={busy || courses.loading || !!courses.error}
            value={courseId}
            onChange={(e) => setCourseId(e.target.value)}
          >
            <option value="">Tất cả</option>
            {courses.data?.map((c) => (
              <option key={c.courseId} value={c.courseId}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Loại bài
          <select disabled={busy} value={type} onChange={(e) => setType(e.target.value)}>
            <option value="">Tất cả</option>
            {['EXERCISE', 'QUIZ', 'TEST'].map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </label>
        <button className="button secondary" disabled={busy}>
          Áp dụng
        </button>
        <button className="button secondary" type="button" disabled={busy} onClick={list.retry}>
          Làm mới
        </button>
        <button
          className="button"
          type="button"
          disabled={busy}
          onClick={() => {
            setCreating(true);
            setSelected(undefined);
          }}
        >
          Tạo bài mới
        </button>
      </form>
      {courses.error ? <ErrorState error={courses.error} retry={courses.retry} /> : null}
      {(creating || selected) && (
        <AssessmentEditor
          key={selected || 'new'}
          id={selected}
          onPending={setBusy}
          onClose={() => {
            setCreating(false);
            setSelected(undefined);
          }}
          onSaved={(id) => {
            setCreating(false);
            setSelected(id);
            list.retry();
          }}
        />
      )}
      {list.loading ? (
        <Loading />
      ) : list.error ? (
        <ErrorState error={list.error} retry={list.retry} />
      ) : !list.data?.length ? (
        <Empty title="Chưa có bài phù hợp." />
      ) : (
        <div className="panel admin-table-wrap">
          <table className="admin-table">
            <caption>Exercise / Quiz / Test</caption>
            <thead>
              <tr>
                <th scope="col">Tên bài</th>
                <th scope="col">Loại</th>
                <th scope="col">Trạng thái</th>
                <th scope="col">Thao tác</th>
              </tr>
            </thead>
            <tbody>
              {list.data.map((a) => (
                <tr key={a.assessmentId}>
                  <th scope="row">
                    {a.title}
                    <small className="admin-id">{a.assessmentId}</small>
                  </th>
                  <td>
                    {a.type} · {a.questionCount} câu
                  </td>
                  <td>{a.ready ? 'Sẵn sàng' : 'Bản nháp'}</td>
                  <td>
                    <button
                      className="text-button"
                      disabled={busy}
                      onClick={() => {
                        setCreating(false);
                        setSelected(a.assessmentId);
                      }}
                    >
                      Xem / sửa {a.title}
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

function AssessmentEditor({
  id,
  onPending,
  onClose,
  onSaved,
}: {
  id?: string;
  onPending: (v: boolean) => void;
  onClose: () => void;
  onSaved: (id: string) => void;
}) {
  const resource = useResource(
    useCallback(
      (signal: AbortSignal) => (id ? request<Detail>(path(id), { signal }) : Promise.resolve(null)),
      [id],
    ),
  );
  if (resource.loading) return <Loading />;
  if (resource.error) return <ErrorState error={resource.error} retry={resource.retry} />;
  return (
    <AssessmentForm
      key={`${id}-${resource.data?.questions.length}`}
      initial={resource.data || undefined}
      onPending={onPending}
      onClose={onClose}
      onSaved={onSaved}
      reload={resource.retry}
    />
  );
}

function AssessmentForm({
  initial,
  onPending,
  onClose,
  onSaved,
  reload,
}: {
  initial?: Detail;
  onPending: (v: boolean) => void;
  onClose: () => void;
  onSaved: (id: string) => void;
  reload: () => void;
}) {
  const [title, setTitle] = useState(initial?.title || '');
  const [type, setType] = useState<Assessment['type']>(initial?.type || 'EXERCISE');
  const [courseId, setCourseId] = useState(initial?.courseId || '');
  const [lessonId, setLessonId] = useState(initial?.lessonId || '');
  const [seconds, setSeconds] = useState(String(initial?.timeLimitSeconds || ''));
  const [ready, setReady] = useState(initial?.ready || false);
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const [error, setError] = useState<unknown>();
  const [notice, setNotice] = useState('');
  const [question, setQuestion] = useState<Question | 'new'>();
  const courses = useResource(
    useCallback(
      (signal: AbortSignal) =>
        initial
          ? Promise.resolve([] as Schema<'Course'>[])
          : allPages<Schema<'Course'>>('/admin/courses', signal),
      [initial],
    ),
  );
  const lessons = useResource(
    useCallback(
      (signal: AbortSignal) =>
        initial
          ? Promise.resolve([] as Schema<'Lesson'>[])
          : allPages<Schema<'Lesson'>>('/admin/lessons', signal),
      [initial],
    ),
  );
  async function save(e: FormEvent) {
    e.preventDefault();
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    onPending(true);
    setError(undefined);
    setNotice('');
    try {
      const configuration = {
        title: title.trim(),
        timeLimitSeconds: type === 'EXERCISE' ? null : Number(seconds),
      };
      if (
        !configuration.title ||
        (type !== 'EXERCISE' &&
          (!Number.isInteger(configuration.timeLimitSeconds) || Number(seconds) <= 0))
      )
        throw new Error('Nhập tên bài và thời gian nguyên dương cho Quiz/Test.');
      if (!initial && (!courseId || (type === 'EXERCISE' && !lessonId)))
        throw new Error('Chọn khóa học và bài học cho Exercise.');
      const body: Schema<'AssessmentPatch'> | Schema<'AssessmentCreate'> = initial
        ? { ...configuration, ready }
        : { ...configuration, type, courseId, lessonId: lessonId || null };
      const result = await mutate<Assessment>(
        initial ? path(initial.assessmentId) : '/admin/assessments',
        initial ? 'PATCH' : 'POST',
        body,
      );
      setNotice('Đã lưu cấu hình trên máy chủ.');
      onSaved(result.assessmentId);
    } catch (e) {
      setError(e);
    } finally {
      lock.current = false;
      setBusy(false);
      onPending(false);
    }
  }
  const pending = (value: boolean) => {
    setBusy(value);
    onPending(value);
  };
  return (
    <section className="panel stack">
      <h2>{initial ? 'Cấu hình bài và câu hỏi' : 'Tạo bài nháp'}</h2>
      <form className="stack" onSubmit={(e) => void save(e)}>
        <fieldset disabled={busy} className="stack">
          <label>
            Tên bài
            <input
              required
              maxLength={200}
              value={title}
              onChange={(e) => setTitle(e.target.value)}
            />
          </label>
          <label>
            Loại bài
            <select
              disabled={!!initial}
              value={type}
              onChange={(e) => setType(e.target.value as Assessment['type'])}
            >
              {['EXERCISE', 'QUIZ', 'TEST'].map((t) => (
                <option key={t}>{t}</option>
              ))}
            </select>
          </label>
          {initial ? (
            <p>
              Khóa học: {initial.courseId} · Bài học: {initial.lessonId || 'Không gắn bài học'}
            </p>
          ) : (
            <>
              <label>
                Khóa học của bài
                <select
                  required
                  value={courseId}
                  onChange={(e) => {
                    setCourseId(e.target.value);
                    setLessonId('');
                  }}
                >
                  <option value="">Chọn khóa học</option>
                  {courses.data?.map((c) => (
                    <option key={c.courseId} value={c.courseId}>
                      {c.name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Bài học liên kết
                <select
                  required={type === 'EXERCISE'}
                  value={lessonId}
                  onChange={(e) => setLessonId(e.target.value)}
                >
                  <option value="">
                    {type === 'EXERCISE' ? 'Chọn bài học' : 'Không gắn bài học'}
                  </option>
                  {lessons.data
                    ?.filter((l) => l.courseId === courseId)
                    .map((l) => (
                      <option key={l.lessonId} value={l.lessonId}>
                        {l.title}
                      </option>
                    ))}
                </select>
              </label>
              {courses.error ? <ErrorState error={courses.error} retry={courses.retry} /> : null}
              {lessons.error ? <ErrorState error={lessons.error} retry={lessons.retry} /> : null}
            </>
          )}
          {type === 'EXERCISE' ? (
            <p>Exercise không giới hạn thời gian.</p>
          ) : (
            <label>
              Thời gian (giây)
              <input
                type="number"
                min="1"
                step="1"
                required
                value={seconds}
                onChange={(e) => setSeconds(e.target.value)}
              />
            </label>
          )}
          {initial && (
            <label className="admin-checkbox">
              <input type="checkbox" checked={ready} onChange={(e) => setReady(e.target.checked)} />{' '}
              Sẵn sàng nhận lượt làm mới
            </label>
          )}
          <p className="muted">
            Máy chủ xác nhận câu hỏi, đáp án và điều kiện sẵn sàng trước khi lưu.
          </p>
          <button className="button" type="submit">
            {busy ? 'Đang lưu…' : 'Lưu cấu hình'}
          </button>
        </fieldset>
        <FormError error={error} />
        {notice && <p role="status">{notice}</p>}
      </form>
      {initial && (
        <>
          <h3>Câu hỏi ({initial.questionCount})</h3>
          {initial.questions.map((q) => (
            <div className="between" key={q.questionId}>
              <p>
                {q.content} · {q.points} điểm · {q.questionType}
              </p>
              <button className="text-button" disabled={busy} onClick={() => setQuestion(q)}>
                Sửa câu hỏi {q.content}
              </button>
            </div>
          ))}
          <button className="button secondary" disabled={busy} onClick={() => setQuestion('new')}>
            Thêm câu hỏi
          </button>
          {question && (
            <QuestionForm
              key={question === 'new' ? 'new' : question.questionId}
              assessmentId={initial.assessmentId}
              initial={question === 'new' ? undefined : question}
              onPending={pending}
              onClose={() => setQuestion(undefined)}
              onSaved={() => {
                setQuestion(undefined);
                reload();
                onSaved(initial.assessmentId);
              }}
            />
          )}
        </>
      )}
      <button className="text-button" disabled={busy} onClick={onClose}>
        Đóng chỉnh sửa
      </button>
    </section>
  );
}

function QuestionForm({
  assessmentId,
  initial,
  onPending,
  onClose,
  onSaved,
}: {
  assessmentId: string;
  initial?: Question;
  onPending: (v: boolean) => void;
  onClose: () => void;
  onSaved: () => void;
}) {
  const [type, setType] = useState<Question['questionType']>(
    initial?.questionType || 'SINGLE_CHOICE',
  );
  const [content, setContent] = useState(initial?.content || '');
  const [points, setPoints] = useState(String(initial?.points || 1));
  const [options, setOptions] = useState(
    initial?.questionType === 'SINGLE_CHOICE'
      ? initial.options
      : Array.from({ length: 2 }, () => ({ optionId: crypto.randomUUID(), text: '' })),
  );
  const [correct, setCorrect] = useState(
    initial?.questionType === 'SINGLE_CHOICE' ? initial.correctOptionId : '',
  );
  const [answer, setAnswer] = useState(
    initial?.questionType === 'TRUE_FALSE' ? String(initial.correctAnswer) : '',
  );
  const [busy, setBusy] = useState(false);
  const lock = useRef(false);
  const [error, setError] = useState<unknown>();
  async function save(e: FormEvent) {
    e.preventDefault();
    if (lock.current) return;
    setError(undefined);
    const base = { content: content.trim(), points: Number(points) };
    if (!base.content || !Number.isFinite(base.points) || base.points <= 0) {
      setError(new Error('Nội dung và điểm dương là bắt buộc.'));
      return;
    }
    if (
      type === 'SINGLE_CHOICE' &&
      (options.length < 2 ||
        options.length > 6 ||
        options.some((o) => !o.text.trim()) ||
        new Set(options.map((o) => o.text.trim())).size !== options.length ||
        !options.some((o) => o.optionId === correct))
    ) {
      setError(new Error('Cần 2–6 lựa chọn khác nhau và chọn một đáp án đúng.'));
      return;
    }
    if (type === 'TRUE_FALSE' && answer === '') {
      setError(new Error('Chọn đáp án Đúng hoặc Sai.'));
      return;
    }
    const body: Schema<'QuestionWrite'> =
      type === 'TRUE_FALSE'
        ? { ...base, questionType: type, correctAnswer: answer === 'true' }
        : {
            ...base,
            questionType: type,
            options: options.map((o) => ({ ...o, text: o.text.trim() })),
            correctOptionId: correct,
          };
    lock.current = true;
    setBusy(true);
    onPending(true);
    try {
      await mutate(
        `${path(assessmentId)}/questions${initial ? `/${encodeURIComponent(initial.questionId)}` : ''}`,
        initial ? 'PUT' : 'POST',
        body,
      );
      onSaved();
    } catch (e) {
      setError(e);
    } finally {
      lock.current = false;
      setBusy(false);
      onPending(false);
    }
  }
  return (
    <form className="panel stack" onSubmit={(e) => void save(e)}>
      <h3>{initial ? 'Sửa câu hỏi' : 'Câu hỏi mới'}</h3>
      <fieldset className="stack" disabled={busy}>
        <label>
          Loại câu hỏi
          <select
            value={type}
            onChange={(e) => setType(e.target.value as Question['questionType'])}
          >
            <option value="SINGLE_CHOICE">Chọn một đáp án</option>
            <option value="TRUE_FALSE">Đúng / Sai</option>
          </select>
        </label>
        <label>
          Nội dung câu hỏi
          <textarea required value={content} onChange={(e) => setContent(e.target.value)} />
        </label>
        <label>
          Điểm
          <input
            type="number"
            required
            min="0.01"
            step="any"
            value={points}
            onChange={(e) => setPoints(e.target.value)}
          />
        </label>
        {type === 'TRUE_FALSE' ? (
          <label>
            Đáp án đúng
            <select required value={answer} onChange={(e) => setAnswer(e.target.value)}>
              <option value="">Chọn đáp án</option>
              <option value="true">Đúng</option>
              <option value="false">Sai</option>
            </select>
          </label>
        ) : (
          <fieldset className="stack">
            <legend>Lựa chọn và đáp án đúng</legend>
            {options.map((o, i) => (
              <div className="stack" key={o.optionId}>
                <label>
                  Lựa chọn {i + 1}
                  <input
                    required
                    value={o.text}
                    onChange={(e) =>
                      setOptions(
                        options.map((v) =>
                          v.optionId === o.optionId ? { ...v, text: e.target.value } : v,
                        ),
                      )
                    }
                  />
                </label>
                <label className="admin-checkbox">
                  <input
                    type="radio"
                    name="correct-option"
                    checked={correct === o.optionId}
                    onChange={() => setCorrect(o.optionId)}
                  />{' '}
                  Đáp án đúng {i + 1}
                </label>
                <button
                  type="button"
                  className="text-button"
                  disabled={options.length <= 2}
                  onClick={() => {
                    setOptions(options.filter((v) => v.optionId !== o.optionId));
                    if (correct === o.optionId) setCorrect('');
                  }}
                >
                  Bỏ lựa chọn {i + 1}
                </button>
              </div>
            ))}
            <button
              type="button"
              className="button secondary"
              disabled={options.length >= 6}
              onClick={() => setOptions([...options, { optionId: crypto.randomUUID(), text: '' }])}
            >
              Thêm lựa chọn
            </button>
          </fieldset>
        )}
        <button type="submit" className="button">
          {busy ? 'Đang lưu…' : 'Lưu câu hỏi'}
        </button>
        <button type="button" className="text-button" onClick={onClose}>
          Hủy câu hỏi
        </button>
      </fieldset>
      <FormError error={error} />
    </form>
  );
}
