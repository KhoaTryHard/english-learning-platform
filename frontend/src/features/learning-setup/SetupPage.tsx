import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api } from '../../shared/api/platform';
import { useResource } from '../../shared/hooks/useResource';
import { Empty, ErrorState, FormError, Loading, PageHeading } from '../../shared/ui/States';
import { useLearning } from './LearningProvider';

export function SetupPage() {
  const { setup, courses, loading, error, refresh } = useLearning();
  const [selectedCourse, setSelectedCourse] = useState<string>();
  const [selectedLevel, setSelectedLevel] = useState<string>();
  const courseId = selectedCourse ?? setup?.courseId ?? '';
  const levelId = selectedLevel ?? (courseId === setup?.courseId ? setup?.levelId : '') ?? '';
  const loadLevels = useCallback(
    (signal: AbortSignal) => (courseId ? api.levels(courseId, signal) : Promise.resolve([])),
    [courseId],
  );
  const levels = useResource(loadLevels);
  const [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState<unknown>();
  const navigate = useNavigate();
  async function save() {
    if (!courseId || !levels.data?.some((l) => l.levelId === levelId) || busy) return;
    setBusy(true);
    setSaveError(undefined);
    try {
      await api.updateSetup({ courseId, levelId });
      refresh();
      navigate(`/courses/${courseId}`);
    } catch (e) {
      setSaveError(e);
    } finally {
      setBusy(false);
    }
  }
  if (loading) return <Loading />;
  if (error) return <ErrorState error={error} retry={refresh} />;
  return (
    <>
      <PageHeading eyebrow="BẮT ĐẦU HÀNH TRÌNH" title="Bạn muốn học từ đâu?">
        Chọn khóa học, sau đó chọn trình độ phù hợp với bạn.
      </PageHeading>
      {!courses?.length ? (
        <Empty title="Chưa có khóa học khả dụng.">
          <p>Hãy quay lại khi nội dung được công bố.</p>
        </Empty>
      ) : (
        <>
          <fieldset className="choice-grid">
            <legend className="sr-only">Chọn khóa học</legend>
            {courses.map((course, index) => (
              <label
                key={course.courseId}
                className={`course-choice ${index % 2 ? 'sand' : ''} ${courseId === course.courseId ? 'selected' : ''}`}
              >
                <input
                  type="radio"
                  name="course"
                  value={course.courseId}
                  checked={courseId === course.courseId}
                  onChange={() => {
                    setSelectedCourse(course.courseId);
                    setSelectedLevel('');
                  }}
                />
                <h2>{course.name}</h2>
                <p>Từ vựng · Ngữ pháp · Nghe · Đọc</p>
                <span className={`button ${courseId === course.courseId ? '' : 'secondary'}`}>
                  {courseId === course.courseId ? 'Đã chọn' : 'Chọn khóa học này'}
                </span>
              </label>
            ))}
          </fieldset>
          <section className="panel stack">
            <h2>
              Chọn trình độ ·{' '}
              {courses.find((c) => c.courseId === courseId)?.name || 'Chọn khóa học trước'}
            </h2>
            {levels.loading ? (
              <Loading />
            ) : levels.error ? (
              <ErrorState error={levels.error} retry={levels.retry} />
            ) : !levels.data?.length ? (
              <p className="muted">Chưa có trình độ khả dụng cho khóa học này.</p>
            ) : (
              <fieldset className="choice-grid">
                <legend className="sr-only">Chọn trình độ</legend>
                {levels.data.map((level) => (
                  <label
                    className={`level-choice ${levelId === level.levelId ? 'selected' : ''}`}
                    key={level.levelId}
                  >
                    <input
                      type="radio"
                      name="level"
                      value={level.levelId}
                      checked={levelId === level.levelId}
                      onChange={() => setSelectedLevel(level.levelId)}
                    />
                    <strong>{level.name}</strong>
                  </label>
                ))}
              </fieldset>
            )}
            <FormError error={saveError} />
            <button
              className="button"
              disabled={busy || levels.loading || !levels.data?.some((l) => l.levelId === levelId)}
              onClick={() => void save()}
            >
              {busy ? 'Đang lưu…' : 'Bắt đầu học →'}
            </button>
            <p className="muted">
              Bạn có thể đổi lựa chọn sau. Lịch sử học và từ đã lưu vẫn được giữ.
            </p>
          </section>
        </>
      )}
    </>
  );
}
