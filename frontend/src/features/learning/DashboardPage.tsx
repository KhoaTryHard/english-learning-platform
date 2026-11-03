import { useCallback } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { useLearning } from '../learning-setup/LearningProvider';
import { api } from '../../shared/api/platform';
import { useResource } from '../../shared/hooks/useResource';
import { Empty, ErrorState, Loading, PageHeading } from '../../shared/ui/States';
import { loadCourseTree } from './courseTree';

export function DashboardPage() {
  const { user } = useAuth();
  const { setup, course, loading, error, refresh } = useLearning();
  const dashboard = useResource(api.dashboard);
  const courseId = setup?.courseId;
  const content = useResource(
    useCallback(
      (signal: AbortSignal) =>
        courseId ? loadCourseTree(courseId, signal) : Promise.resolve(null),
      [courseId],
    ),
  );
  if (loading) return <Loading />;
  if (error) return <ErrorState error={error} retry={refresh} />;
  if (!setup?.courseId)
    return (
      <>
        <PageHeading title={`Chào ${user?.fullName || 'bạn'}, mình bắt đầu nhé.`}>
          Một bài học rõ ràng, một bước tiến trong tiếng Anh.
        </PageHeading>
        <Empty title="Chọn khóa học đầu tiên của bạn.">
          <Link className="button" to="/setup">
            Chọn khóa học và trình độ →
          </Link>
        </Empty>
      </>
    );
  const first = content.data?.modules.flatMap((m) => m.lessons)[0];
  const progress = dashboard.data?.progress;
  return (
    <>
      <PageHeading
        eyebrow="HÔM NAY, HỌC THÊM MỘT CHÚT"
        title={`Chào ${user?.fullName || 'bạn'}, mình học tiếp nhé.`}
      >
        Một bài học rõ ràng, một bước tiến trong tiếng Anh.
      </PageHeading>
      <div className="learning-focus">
        <section className="continue-card stack">
          <span className="badge">BÀI HỌC TRONG KHÓA</span>
          <h2>{first?.title || course?.name}</h2>
          <p>Học từ vựng, mẫu câu và luyện tập theo từng bài.</p>
          <small>{course?.name}</small>
          {content.loading ? (
            <Loading />
          ) : content.error ? (
            <ErrorState error={content.error} retry={content.retry} />
          ) : first ? (
            <Link className="button" to={`/lessons/${first.lessonId}`}>
              Mở bài học →
            </Link>
          ) : (
            <Link className="button" to={`/courses/${setup.courseId}`}>
              Xem khóa học →
            </Link>
          )}
          <hr />
          <small>Từ vựng · Ngữ pháp · Nghe · Đọc · Bài tập</small>
        </section>
        <aside className="specimen stack">
          <p className="eyebrow">MỖI NGÀY, MỘT BƯỚC TIẾN</p>
          <blockquote>
            Một câu nhỏ.
            <br />
            Một bước tự tin hơn.
          </blockquote>
          <p className="muted">Học theo nhịp của bạn, thực hành sau mỗi bài học.</p>
          <hr />
          <Link to="/assessments">Luyện tập Quiz / Test →</Link>
        </aside>
      </div>
      {dashboard.loading ? (
        <Loading text="Đang tải tiến độ…" />
      ) : dashboard.error ? (
        <ErrorState error={dashboard.error} retry={dashboard.retry} />
      ) : (
        <section className="panel stack">
          <div className="between">
            <strong>{course?.name}</strong>
            <span className="purple">
              {progress?.progressPercent == null
                ? 'Chưa có tỷ lệ tiến độ'
                : `${progress.completedLessons}/${progress.totalLessons} bài học · ${progress.progressPercent}%`}
            </span>
          </div>
          {progress?.progressPercent != null && (
            <progress value={progress.progressPercent} max={100} aria-label="Tiến độ khóa học" />
          )}
          <small className="muted">
            Tiến độ có thể cần thêm thời gian đồng bộ sau khi nộp bài.
          </small>
        </section>
      )}
      <div className="learning-focus">
        <section className="panel stack">
          <h2>Trong khóa học của bạn</h2>
          {content.data?.modules
            .flatMap((m) => m.lessons)
            .slice(0, 3)
            .map((lesson) => (
              <Link className="lesson-row" key={lesson.lessonId} to={`/lessons/${lesson.lessonId}`}>
                <span className="lesson-number">{lesson.orderNo}</span>
                <strong>{lesson.title}</strong>
                <span className="purple">Mở bài →</span>
              </Link>
            ))}
        </section>
        <section className="panel stack">
          <h2>Nhìn lại bước tiến</h2>
          {dashboard.data?.recentHistory.length ? (
            dashboard.data.recentHistory.map((item) => (
              <Link key={item.submissionId} to={`/results/${item.resultId}`}>
                <strong>
                  {item.assessmentType} · {item.scorePercent}%
                </strong>
                <p className="muted">{new Date(item.completedAt).toLocaleDateString('vi-VN')}</p>
              </Link>
            ))
          ) : (
            <p className="muted">
              {dashboard.error ? 'Lịch sử chưa tải được.' : 'Chưa có hoạt động học được ghi nhận.'}
            </p>
          )}
          <Link to="/history">Xem lịch sử học tập →</Link>
        </section>
      </div>
    </>
  );
}
