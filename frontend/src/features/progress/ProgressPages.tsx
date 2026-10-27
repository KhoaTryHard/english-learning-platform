import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../shared/api/platform';
import { useLearning } from '../learning-setup/LearningProvider';
import { useResource } from '../../shared/hooks/useResource';
import { Empty, ErrorState, Loading, PageHeading } from '../../shared/ui/States';

export function ProgressPage() {
  const { setup, course, loading, error, refresh } = useLearning();
  const courseId = setup?.courseId;
  const resource = useResource(
    useCallback(
      (signal: AbortSignal) => (courseId ? api.progress(courseId, signal) : Promise.resolve(null)),
      [courseId],
    ),
  );
  if (loading || resource.loading) return <Loading />;
  if (error || resource.error)
    return (
      <ErrorState
        error={error || resource.error}
        retry={() => {
          refresh();
          resource.retry();
        }}
      />
    );
  return (
    <>
      <PageHeading title="Tiến độ học tập">
        Theo dõi mức độ hoàn thành của khóa {course?.name || 'hiện tại'}.
      </PageHeading>
      {!resource.data ? (
        <Empty title="Chưa có khóa học hiện tại.">
          <Link to="/setup">Chọn khóa học và trình độ →</Link>
        </Empty>
      ) : (
        <section className="panel stack">
          <div className="between">
            <div>
              <h2>{course?.name}</h2>
              <p className="muted">
                {resource.data.completedLessons}/{resource.data.totalLessons} bài học hoàn thành
              </p>
            </div>
            <strong className="score">
              {resource.data.progressPercent == null ? '—' : `${resource.data.progressPercent}%`}
            </strong>
          </div>
          {resource.data.progressPercent !== null && (
            <progress
              value={resource.data.progressPercent}
              max={100}
              aria-label="Tiến độ khóa học"
            />
          )}
          <p className="muted">
            {resource.data.totalLessons === 0
              ? 'Chưa có bài học để tính tỷ lệ.'
              : 'Tiến độ = số bài hoàn thành / tổng số bài của khóa × 100%.'}
          </p>
          <small>
            Chỉ hoàn thành bài tập cuối bài mới tính hoàn thành Lesson; làm lại không cộng trùng. Dữ
            liệu có thể đang đồng bộ.
          </small>
          <Link to="/history">Xem lịch sử học tập →</Link>
        </section>
      )}
    </>
  );
}

export function HistoryPage() {
  const history = useResource(api.history);
  const { courses } = useLearning();
  const [courseId, setCourseId] = useState('');
  const [search, setSearch] = useState('');
  if (history.loading) return <Loading />;
  if (history.error) return <ErrorState error={history.error} retry={history.retry} />;
  const items = history.data!.filter(
    (item) =>
      (!courseId || item.courseId === courseId) &&
      `${item.assessmentType} ${item.assessmentId} ${courses?.find((c) => c.courseId === item.courseId)?.name || item.courseId}`
        .toLowerCase()
        .includes(search.toLowerCase()),
  );
  return (
    <>
      <PageHeading title="Lịch sử học tập">
        Toàn bộ hoạt động được giữ lại, kể cả sau khi đổi Course hoặc Level.
      </PageHeading>
      <div className="history-filters">
        <label>
          <span className="sr-only">Tìm lịch sử</span>
          <input
            placeholder="Tìm theo khóa học hoặc loại bài…"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </label>
        <label>
          <span className="sr-only">Lọc khóa học</span>
          <select value={courseId} onChange={(e) => setCourseId(e.target.value)}>
            <option value="">Tất cả khóa học</option>
            {[...new Set(history.data!.map((h) => h.courseId))].map((id) => (
              <option value={id} key={id}>
                {courses?.find((c) => c.courseId === id)?.name || id}
              </option>
            ))}
          </select>
        </label>
      </div>
      {!items.length ? (
        <Empty title="Chưa có lịch sử phù hợp." />
      ) : (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>KHÓA HỌC</th>
                <th>HOẠT ĐỘNG</th>
                <th>NGÀY</th>
                <th>KẾT QUẢ</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.historyId}>
                  <td>
                    {courses?.find((c) => c.courseId === item.courseId)?.name || item.courseId}
                  </td>
                  <td>{item.assessmentType}</td>
                  <td>{new Date(item.completedAt).toLocaleString('vi-VN')}</td>
                  <td>
                    <Link to={`/results/${item.resultId}`}>
                      {item.earnedPoints}/{item.maxPoints} · {item.scorePercent}% →
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      <p className="notice">
        Khóa và trình độ của kết quả được giữ theo thời điểm làm bài. Kết quả có thể cần thêm thời
        gian xuất hiện sau khi nộp.
      </p>
    </>
  );
}
