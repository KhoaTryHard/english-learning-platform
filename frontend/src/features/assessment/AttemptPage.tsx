import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import type { Schema } from '../../shared/api/client';
import { api } from '../../shared/api/platform';
import { useResource } from '../../shared/hooks/useResource';
import { ErrorState, FormError, Loading } from '../../shared/ui/States';
import { AnswerQueue, formatTime, remainingSeconds } from './answerQueue';
import selector from '../../shared/assets/figma/exercise-imgSelector.svg';
import selectedSelector from '../../shared/assets/figma/exercise-imgSelector1.svg';
import arrowLeft from '../../shared/assets/figma/exercise-imgArrowLeft.svg';
import arrowRight from '../../shared/assets/figma/exercise-imgArrowRight.svg';

export function AttemptPage() {
  const { attemptId = '' } = useParams();
  const resource = useResource(
    useCallback((signal: AbortSignal) => api.attempt(attemptId, signal), [attemptId]),
  );
  if (resource.loading) return <Loading text="Đang khôi phục lượt làm từ máy chủ…" />;
  if (resource.error) return <ErrorState error={resource.error} retry={resource.retry} />;
  return <AttemptEditor key={attemptId} initial={resource.data!} />;
}

export function AttemptEditor({ initial }: { initial: Schema<'Attempt'> }) {
  const [attempt, setAttempt] = useState(initial);
  const [answers, setAnswers] = useState(initial.answers);
  const [index, setIndex] = useState(0);
  const [pending, setPending] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<unknown>();
  const [clock, setClock] = useState(() => {
    const now = performance.now();
    return { tick: now, receivedAt: now };
  });
  const submitLock = useRef(false);
  const timeoutHandled = useRef(false);
  const [queue] = useState(
    () => new AnswerQueue((snapshot) => api.answers(initial.attemptId, snapshot)),
  );
  const navigate = useNavigate();
  const seconds = remainingSeconds(
    attempt.deadlineAt,
    attempt.serverNow,
    clock.tick - clock.receivedAt,
  );
  const expired = seconds === 0;
  const terminal = attempt.status !== 'IN_PROGRESS';

  const accept = useCallback(
    (next: Schema<'Attempt'>) => {
      const now = performance.now();
      setClock({ tick: now, receivedAt: now });
      setAttempt(next);
      if (next.result) navigate(`/results/${next.result.resultId}`, { replace: true });
    },
    [navigate],
  );

  const synchronize = useCallback(async () => {
    try {
      await queue.flush();
    } catch {
      /* Server deadline still needs reconciliation after a failed save. */
    }
    try {
      accept(await api.attempt(initial.attemptId));
      setError(undefined);
    } catch (e) {
      setError(e);
    }
  }, [queue, initial.attemptId, accept]);

  useEffect(() => {
    if (initial.result) navigate(`/results/${initial.result.resultId}`, { replace: true });
  }, [initial.result, navigate]);
  useEffect(() => {
    if (attempt.deadlineAt === null || terminal) return;
    const timer = window.setInterval(
      () => setClock((previous) => ({ ...previous, tick: performance.now() })),
      250,
    );
    return () => window.clearInterval(timer);
  }, [attempt.deadlineAt, terminal]);
  useEffect(() => {
    if (!expired || terminal || timeoutHandled.current) return;
    timeoutHandled.current = true;
    void synchronize();
  }, [expired, terminal, synchronize]);
  useEffect(() => {
    if (!expired) timeoutHandled.current = false;
  }, [expired]);
  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === 'visible') void synchronize();
    };
    document.addEventListener('visibilitychange', refresh);
    return () => document.removeEventListener('visibilitychange', refresh);
  }, [synchronize]);
  useEffect(() => {
    const warn = (e: BeforeUnloadEvent) => {
      if (pending || error) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [pending, error]);

  async function persist(next: Schema<'Answer'>[]) {
    setPending((n) => n + 1);
    try {
      const response = await queue.enqueue(next);
      accept(response);
      setError(undefined);
    } catch (e) {
      setError(e);
    } finally {
      setPending((n) => n - 1);
    }
  }
  function choose(answer: Schema<'Answer'>) {
    if (submitting || expired || terminal) return;
    const next = [...answers.filter((a) => a.questionId !== answer.questionId), answer];
    setAnswers(next);
    void persist(next);
  }
  async function submit() {
    if (submitLock.current || terminal) return;
    submitLock.current = true;
    setSubmitting(true);
    setError(undefined);
    try {
      // Wait for every queued write, then ACK the latest complete set before submitting without a body.
      const saved = await queue.enqueue(answers);
      accept(saved);
      if (!saved.result) {
        const result = await api.submit(initial.attemptId);
        navigate(`/results/${result.resultId}`, { replace: true });
      }
    } catch (e) {
      setError(e);
    } finally {
      submitLock.current = false;
      setSubmitting(false);
    }
  }

  const question = attempt.questions[index];
  const answer = answers.find((a) => a.questionId === question?.questionId);
  if (!question)
    return (
      <ErrorState
        error={new Error('Lượt làm không có câu hỏi hợp lệ.')}
        retry={() => void synchronize()}
      />
    );
  return (
    <div className="focus-page">
      <header className="focus-header">
        <Link to="/" className="brand">
          L / <span>LinguaTrack</span>
        </Link>
        <strong>{attempt.deadlineAt === null ? 'Bài tập cuối bài' : 'Quiz / Test'}</strong>
        {seconds !== null ? (
          <span
            className={`badge timer ${seconds < 60 ? 'urgent' : ''}`}
            role="timer"
            aria-label="Thời gian còn lại"
          >
            Còn lại {formatTime(seconds)}
          </span>
        ) : (
          <span className="badge">Không giới hạn thời gian</span>
        )}
      </header>
      <main className={`assessment-focus ${attempt.deadlineAt === null ? 'exercise-layout' : ''}`}>
        <aside className="panel question-navigation">
          <h2>Câu hỏi</h2>
          <nav aria-label="Chọn câu hỏi" className="question-grid">
            {attempt.questions.map((q, i) => (
              <button
                className={`${i === index ? 'current' : answers.some((a) => a.questionId === q.questionId) ? 'answered' : ''}`}
                key={q.questionId}
                aria-current={i === index ? 'step' : undefined}
                aria-label={`Câu ${i + 1}`}
                onClick={() => setIndex(i)}
              >
                {i + 1}
              </button>
            ))}
          </nav>
          <p className="muted">
            {answers.length} đã trả lời · {attempt.questions.length - answers.length} còn lại
          </p>
          <small className="muted">
            Số tím: câu đang xem
            <br />
            Số xanh: đã chọn đáp án
          </small>
          <p role="status" className="save-status">
            {pending
              ? 'Đang lưu đáp án…'
              : error
                ? 'Đáp án mới chưa lưu.'
                : 'Đã tải/lưu đáp án từ máy chủ.'}
          </p>
          <Link to={`/assessments/${attempt.assessmentId}`}>Về thông tin bài</Link>
        </aside>
        <div className="question-workspace stack">
          <section className="panel question-card stack">
            <span className="badge">
              CÂU {index + 1} / {attempt.questions.length}
            </span>
            <p className="muted">
              {question.questionType === 'SINGLE_CHOICE'
                ? 'Chọn một đáp án.'
                : 'Chọn Đúng hoặc Sai.'}
            </p>
            <h1>{question.content}</h1>
            <fieldset className="answer-options" disabled={submitting || expired || terminal}>
              <legend className="sr-only">Đáp án câu {index + 1}</legend>
              {question.questionType === 'SINGLE_CHOICE'
                ? question.options.map((option, i) => {
                    const selected =
                      answer?.questionType === 'SINGLE_CHOICE' &&
                      answer.optionId === option.optionId;
                    return (
                      <label
                        className={`answer-option ${selected ? 'selected' : ''}`}
                        key={option.optionId}
                      >
                        <input
                          type="radio"
                          name={question.questionId}
                          value={option.optionId}
                          checked={selected}
                          onChange={() =>
                            choose({
                              questionId: question.questionId,
                              questionType: 'SINGLE_CHOICE',
                              optionId: option.optionId,
                            })
                          }
                        />
                        <img src={selected ? selectedSelector : selector} alt="" />
                        <span>{String.fromCharCode(65 + i)}</span>
                        <span>{option.text}</span>
                      </label>
                    );
                  })
                : [true, false].map((value) => {
                    const selected =
                      answer?.questionType === 'TRUE_FALSE' && answer.value === value;
                    return (
                      <label
                        className={`answer-option ${selected ? 'selected' : ''}`}
                        key={String(value)}
                      >
                        <input
                          type="radio"
                          name={question.questionId}
                          value={String(value)}
                          checked={selected}
                          onChange={() =>
                            choose({
                              questionId: question.questionId,
                              questionType: 'TRUE_FALSE',
                              value,
                            })
                          }
                        />
                        <img src={selected ? selectedSelector : selector} alt="" />
                        <span>{value ? 'Đúng' : 'Sai'}</span>
                      </label>
                    );
                  })}
            </fieldset>
          </section>
          <FormError error={error} />
          {!!error && (
            <div className="inline-actions">
              <button
                className="button secondary"
                disabled={submitting || pending > 0 || expired}
                onClick={() => void persist(answers)}
              >
                Lưu lại đáp án
              </button>
              <button className="button secondary" onClick={() => void synchronize()}>
                Kiểm tra trạng thái máy chủ
              </button>
            </div>
          )}
          <div className="inline-actions">
            <button
              className="button secondary"
              disabled={index === 0}
              onClick={() => setIndex((i) => i - 1)}
            >
              <img src={arrowLeft} alt="" /> Câu trước
            </button>
            <button
              className="button secondary"
              disabled={index === attempt.questions.length - 1}
              onClick={() => setIndex((i) => i + 1)}
            >
              Câu tiếp theo <img src={arrowRight} alt="" />
            </button>
            <button
              className="button"
              disabled={submitting || terminal || expired}
              onClick={() => void submit()}
            >
              {submitting ? 'Đang nộp bài…' : 'Nộp bài'}
            </button>
          </div>
          <p className="muted">
            {expired
              ? 'Đã hết thời gian trên đồng hồ. Đang đối chiếu kết quả với máy chủ; bài được chấm theo đáp án đã lưu.'
              : attempt.deadlineAt === null
                ? 'Có thể làm lại không giới hạn. Mỗi lượt hợp lệ được lưu kết quả riêng.'
                : 'Hết giờ, bài được chấm theo các đáp án đã lưu. Tải lại trang không đặt lại đồng hồ.'}
          </p>
        </div>
      </main>
    </div>
  );
}
