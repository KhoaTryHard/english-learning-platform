import { useCallback } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useLearning } from '../learning-setup/LearningProvider';
import { useResource } from '../../shared/hooks/useResource';
import { Empty, ErrorState, Loading, PageHeading } from '../../shared/ui/States';
import { loadCourseTree } from './courseTree';
import book from '../../shared/assets/figma/courses-imgBookOpen.svg';
import arrow from '../../shared/assets/figma/courses-imgArrowRight.svg';
import info from '../../shared/assets/figma/courses-imgInfo.svg';
import play from '../../shared/assets/figma/course-imgPlay.svg';
import chevron from '../../shared/assets/figma/course-imgChevronRight.svg';

export function CoursesPage() {
  const { courses, setup, loading, error, refresh } = useLearning();
  if (loading) return <Loading />;
  if (error) return <ErrorState error={error} retry={refresh} />;
  return (
    <>
      <div className="heading-actions">
        <PageHeading title="Khóa học của tôi">
          Tiếp tục khóa hiện tại hoặc khám phá nội dung được công bố.
        </PageHeading>
        <Link className="button secondary" to="/setup">
          Đổi khóa học / trình độ
        </Link>
      </div>
      {!courses?.length ? (
        <Empty title="Chưa có khóa học khả dụng." />
      ) : (
        <div className="course-grid">
          {courses.map((course) => (
            <article key={course.courseId} className="panel course-card">
              <div className="course-card-heading">
                <span className="icon-tile">
                  <img src={book} alt="" />
                </span>
                <h2>{course.name}</h2>
                {setup?.courseId === course.courseId && (
                  <span className="badge">Khóa hiện tại</span>
                )}
              </div>
              <p className="muted">Từ vựng · Ngữ pháp · Nghe · Đọc · Bài tập</p>
              <Link className="button secondary" to={`/courses/${course.courseId}`}>
                <img src={arrow} alt="" /> Xem khóa học
              </Link>
            </article>
          ))}
        </div>
      )}
      <p className="notice">
        <img src={info} alt="" />
        Khi thay đổi Course hoặc Level, lịch sử học và từ đã lưu của bạn vẫn được giữ.
      </p>
    </>
  );
}

export function CoursePage() {
  const { courseId = '' } = useParams();
  const resource = useResource(
    useCallback((signal: AbortSignal) => loadCourseTree(courseId, signal), [courseId]),
  );
  if (resource.loading) return <Loading />;
  if (resource.error) return <ErrorState error={resource.error} retry={resource.retry} />;
  const data = resource.data!;
  const first = data.modules.flatMap((m) => m.lessons)[0];
  return (
    <>
      <section className="course-hero">
        <span className="badge">KHÔNG GIAN HỌC</span>
        <h1>{data.course.name}</h1>
        <p>Học theo bài. Luyện tập sau mỗi chặng.</p>
        <div className="inline-actions">
          <span>{data.modules.length} Module</span>
          <span>{data.modules.reduce((n, m) => n + m.lessons.length, 0)} bài học đang công bố</span>
        </div>
        {first && (
          <Link className="button" to={`/lessons/${first.lessonId}`}>
            Bắt đầu học <img src={play} alt="" />
          </Link>
        )}
      </section>
      <h2>Nội dung khóa học</h2>
      {!data.modules.length ? (
        <Empty title="Khóa học chưa có module khả dụng." />
      ) : (
        data.modules.map((module) => (
          <section className="panel module-panel" key={module.moduleId}>
            <h2>
              Module {module.orderNo} — {module.title}
            </h2>
            {!module.lessons.length ? (
              <p className="muted">Chưa có bài học khả dụng.</p>
            ) : (
              module.lessons.map((lesson) => (
                <Link
                  className="lesson-row"
                  to={`/lessons/${lesson.lessonId}`}
                  key={lesson.lessonId}
                >
                  <span className="lesson-number">{lesson.orderNo}</span>
                  <span className="lesson-row-copy">
                    <strong>{lesson.title}</strong>
                    <small>Từ vựng · Ngữ pháp · Nghe · Đọc · Bài tập</small>
                  </span>
                  <span className="badge neutral">Mở bài học</span>
                  <img src={chevron} alt="" />
                </Link>
              ))
            )}
          </section>
        ))
      )}
      <p className="muted">
        Mở bài học chưa tính hoàn thành. Hoàn thành bài tập cuối bài để ghi nhận tiến độ.
      </p>
    </>
  );
}
