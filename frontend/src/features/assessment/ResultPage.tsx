import { useCallback } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../shared/api/platform';
import { useResource } from '../../shared/hooks/useResource';
import { ErrorState, Loading, PageHeading } from '../../shared/ui/States';
import check from '../../shared/assets/figma/result-imgCircleCheck.svg';
import clock from '../../shared/assets/figma/result-imgClock.svg';
import retake from '../../shared/assets/figma/result-imgRotateCcw.svg';
import arrow from '../../shared/assets/figma/result-imgArrowRight.svg';

export function ResultPage() {
  const { resultId = '' } = useParams();
  const result = useResource(
    useCallback((signal: AbortSignal) => api.result(resultId, signal), [resultId]),
  );
  if (result.loading) return <Loading />;
  if (result.error) return <ErrorState error={result.error} retry={result.retry} />;
  const data = result.data!;
  return (
    <>
      <PageHeading
        title={`Kết quả ${data.assessmentType === 'EXERCISE' ? 'bài tập cuối bài' : data.assessmentType}`}
      >
        Hoàn thành {new Date(data.completedAt).toLocaleString('vi-VN')}
      </PageHeading>
      <div className="result-grid">
        <section className="panel stack">
          <span className="badge success">
            {data.status === 'TIMEOUT' ? 'ĐÃ HẾT GIỜ' : 'ĐÃ HOÀN THÀNH'}
          </span>
          <strong className="score">
            {data.earnedPoints}/{data.maxPoints}
          </strong>
          <p className="muted">Điểm số do máy chủ chấm</p>
        </section>
        <section className="panel stack">
          <img className="result-icon" src={check} alt="" />
          <strong className="score">{data.scorePercent}%</strong>
          <p className="muted">Tỷ lệ điểm đạt được</p>
        </section>
        <section className="panel stack">
          <img className="result-icon" src={clock} alt="" />
          <strong>Đã lưu kết quả</strong>
          <p className="muted">{new Date(data.completedAt).toLocaleDateString('vi-VN')}</p>
          <small>Các kết quả trước được giữ nguyên.</small>
        </section>
      </div>
      <div className="inline-actions">
        <Link className="button secondary" to={`/assessments/${data.assessmentId}`}>
          <img src={retake} alt="" />
          Làm lại
        </Link>
        <Link
          className="button"
          to={data.lessonId ? `/lessons/${data.lessonId}` : `/courses/${data.courseId}`}
        >
          Tiếp tục học
          <img src={arrow} alt="" />
        </Link>
      </div>
      <section className="panel stack">
        <h2>
          {data.assessmentType === 'EXERCISE'
            ? 'Bài tập đã được ghi nhận.'
            : 'Thêm một bước luyện tập.'}
        </h2>
        <p>
          {data.assessmentType === 'EXERCISE'
            ? 'Hoàn thành bài tập cuối bài ghi nhận hoàn thành bài học, kể cả khi điểm bằng 0. Tiến độ có thể cần thêm thời gian đồng bộ; làm lại không cộng trùng bài học.'
            : 'Quiz/Test lưu kết quả riêng. Hoàn thành bài tập cuối bài để ghi nhận hoàn thành Lesson.'}
        </p>
        <Link to="/progress">Xem tiến độ học tập →</Link>
      </section>
    </>
  );
}
