import { useCallback, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { useLearning } from '../learning-setup/LearningProvider';
import { api } from '../../shared/api/platform';
import { useResource } from '../../shared/hooks/useResource';
import { Empty, ErrorState, FormError, Loading, PageHeading } from '../../shared/ui/States';

export function AssessmentsPage() {
  const { setup } = useLearning();
  const courseId = setup?.courseId ?? undefined;
  const list = useResource(
    useCallback((signal: AbortSignal) => api.assessments(courseId, signal), [courseId]),
  );
  if (list.loading) return <Loading />;
  if (list.error) return <ErrorState error={list.error} retry={list.retry} />;
  return (
    <>
      <PageHeading title="Luyện tập Quiz / Test">
        Mỗi lần làm là một cơ hội luyện tập. Bạn có thể làm lại không giới hạn.
      </PageHeading>
      {!list.data?.length ? (
        <Empty title="Chưa có bài đánh giá khả dụng." />
      ) : (
        <div className="course-grid">
          {list.data.map((a) => (
            <article className="panel stack" key={a.assessmentId}>
              <span className="badge">{a.type}</span>
              <h2>{a.title}</h2>
              <p className="muted">
                {a.questionCount} câu hỏi ·{' '}
                {a.timeLimitSeconds === null
                  ? 'Không giới hạn thời gian'
                  : `${Math.ceil(a.timeLimitSeconds / 60)} phút`}
              </p>
              <Link className="button secondary" to={`/assessments/${a.assessmentId}`}>
                Xem bài →
              </Link>
            </article>
          ))}
        </div>
      )}
    </>
  );
}

export function AssessmentPage() {
  const { assessmentId = '' } = useParams();
  const { user } = useAuth();
  const metadata = useResource(
    useCallback((signal: AbortSignal) => api.assessment(assessmentId, signal), [assessmentId]),
  );
  const attempts = useResource(api.attempts);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const lock = useRef(false);
  const navigate = useNavigate();
  async function start() {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError(undefined);
    // Persist only a transport key (no token/password/answers). A refresh after a lost response retries the same create.
    const storageKey = `lt:start:${user!.userId}:${assessmentId}`;
    try {
      let key = sessionStorage.getItem(storageKey);
      if (!key) {
        key = crypto.randomUUID();
        sessionStorage.setItem(storageKey, key);
      }
      const attempt = await api.startAttempt(assessmentId, key);
      sessionStorage.removeItem(storageKey);
      navigate(
        attempt.result ? `/results/${attempt.result.resultId}` : `/attempts/${attempt.attemptId}`,
      );
    } catch (e) {
      setError(e);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  if (metadata.loading) return <Loading />;
  if (metadata.error) return <ErrorState error={metadata.error} retry={metadata.retry} />;
  const assessment = metadata.data!;
  const inProgress = attempts.data?.filter(
    (a) => a.assessmentId === assessmentId && a.status === 'IN_PROGRESS',
  );
  return (
    <>
      <PageHeading eyebrow={assessment.type} title={assessment.title}>
        Hai dạng câu hỏi: Một đáp án và Đúng / Sai.
      </PageHeading>
      <section className="panel stack">
        <h2>{assessment.questionCount} câu hỏi</h2>
        <p>
          {assessment.type === 'EXERCISE'
            ? 'Bài tập cuối bài không có đồng hồ. Hoàn thành bài tập để ghi nhận hoàn thành bài học, không yêu cầu ngưỡng điểm.'
            : `Thời gian làm bài: ${Math.ceil((assessment.timeLimitSeconds || 0) / 60)} phút. Đồng hồ theo máy chủ; tải lại trang hoặc đăng nhập lại không đặt lại thời hạn.`}
        </p>
        <p className="muted">
          Bạn có thể làm lại không giới hạn. Mỗi lần làm mới có kết quả riêng.
        </p>
        <FormError error={error} />
        <button
          className="button"
          disabled={busy || !assessment.ready}
          onClick={() => void start()}
        >
          {busy ? 'Đang mở bài…' : 'Bắt đầu lượt làm mới →'}
        </button>
      </section>
      {attempts.loading ? (
        <Loading text="Đang tìm lượt làm chưa hoàn tất…" />
      ) : attempts.error ? (
        <ErrorState error={attempts.error} retry={attempts.retry} />
      ) : (
        !!inProgress?.length && (
          <section className="panel stack">
            <h2>Tiếp tục lượt đang làm</h2>
            {inProgress.map((a) => (
              <Link className="lesson-row" to={`/attempts/${a.attemptId}`} key={a.attemptId}>
                <span>{new Date(a.startedAt).toLocaleString('vi-VN')}</span>
                <strong>Tiếp tục lượt này →</strong>
              </Link>
            ))}
          </section>
        )
      )}
    </>
  );
}
