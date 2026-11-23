import { useCallback, useState } from 'react';
import { allPages, request, type Schema } from '../../shared/api/client';
import { useResource } from '../../shared/hooks/useResource';
import { Empty, ErrorState, Loading, PageHeading } from '../../shared/ui/States';

export function StatisticsPage() {
  const [courseId, setCourseId] = useState('');
  const [levelId, setLevelId] = useState('');
  const [filter, setFilter] = useState({ courseId: '', levelId: '' });
  const courses = useResource(
    useCallback((signal: AbortSignal) => allPages<Schema<'Course'>>('/admin/courses', signal), []),
  );
  const resource = useResource(
    useCallback(
      (signal: AbortSignal) => {
        const query = new URLSearchParams();
        if (filter.courseId) query.set('courseId', filter.courseId);
        if (filter.levelId) query.set('levelId', filter.levelId);
        return request<Schema<'Statistics'>>(`/admin/statistics${query.size ? `?${query}` : ''}`, {
          signal,
        });
      },
      [filter],
    ),
  );
  const percent = (value: number | null) => (value === null ? 'Chưa đủ dữ liệu' : `${value}%`);
  return (
    <>
      <PageHeading title="Thống kê">
        Sáu nhóm chỉ số học tập. Dữ liệu có thể cần thời gian đồng bộ.
      </PageHeading>
      <form
        className="panel stack"
        onSubmit={(event) => {
          event.preventDefault();
          setFilter({ courseId: courseId.trim(), levelId: levelId.trim() });
        }}
      >
        <label>
          Khóa học{' '}
          <select
            disabled={courses.loading || !!courses.error}
            value={courseId}
            onChange={(event) => {
              setCourseId(event.target.value);
              setLevelId('');
            }}
          >
            <option value="">Tất cả khóa học</option>
            {courses.data?.map((c) => (
              <option key={c.courseId} value={c.courseId}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Mã trình độ (tùy chọn){' '}
          <input
            disabled={!courseId}
            value={levelId}
            onChange={(event) => setLevelId(event.target.value)}
            pattern="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
            placeholder="Để trống để xem mọi trình độ"
            aria-describedby="statistics-level-help"
          />
        </label>
        <small id="statistics-level-help">
          Chọn khóa học trước; mã trình độ phải thuộc khóa đã chọn.
        </small>
        <button className="button" type="submit">
          Áp dụng bộ lọc
        </button>
        <button
          className="button secondary"
          type="button"
          disabled={resource.loading}
          onClick={resource.retry}
        >
          Làm mới thống kê
        </button>
      </form>
      {courses.error ? <ErrorState error={courses.error} retry={courses.retry} /> : null}
      {resource.loading ? (
        <Loading />
      ) : resource.error ? (
        <ErrorState error={resource.error} retry={resource.retry} />
      ) : (
        resource.data && (
          <>
            <p>
              Tổng người học toàn hệ thống: <strong>{resource.data.totalLearners}</strong>
            </p>
            <p className="muted">
              Cập nhật: {new Date(resource.data.generatedAt).toLocaleString('vi-VN')} ·{' '}
              {resource.data.syncState === 'EVENTUAL' ? 'Đồng bộ dần' : resource.data.syncState}
            </p>
            {resource.data.courses.length === 0 ? (
              <Empty title="Chưa có thống kê phù hợp." />
            ) : (
              resource.data.courses.map((row) => (
                <section className="panel stack" key={`${row.courseId}-${row.levelId}`}>
                  <h2>
                    {courses.data?.find((c) => c.courseId === row.courseId)?.name ||
                      `Khóa học ${row.courseId}`}
                  </h2>
                  <p>Trình độ: {row.levelId || 'Tất cả'}</p>
                  <p>Người học hiện tại: {row.currentLearnerCount}</p>
                  <p>Tiến độ trung bình: {percent(row.averageProgressPercent)}</p>
                  <p>Lượt hoàn thành bài học lần đầu: {row.completedLessonCount}</p>
                  <p>
                    Lượt làm Exercise: {row.attemptCounts.EXERCISE} · Quiz: {row.attemptCounts.QUIZ}{' '}
                    · Test: {row.attemptCounts.TEST}
                  </p>
                  <p>Điểm Quiz/Test trung bình: {percent(row.averageQuizTestScorePercent)}</p>
                </section>
              ))
            )}
          </>
        )
      )}
      <p className="notice">
        Tổng người học không thay đổi theo bộ lọc. Tiến độ trung bình gồm người chưa bắt đầu và toàn
        bộ bài học của khóa. Lượt làm gồm làm lại và hết giờ; điểm trung bình dùng mọi kết quả
        Quiz/Test. Hoàn thành bài học chỉ tính lần đầu, giữ khóa/trình độ lúc hoàn thành.
      </p>
    </>
  );
}
