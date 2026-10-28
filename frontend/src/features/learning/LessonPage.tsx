import { useCallback, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { api } from '../../shared/api/platform';
import { useResource } from '../../shared/hooks/useResource';
import { Empty, ErrorState, FormError, Loading } from '../../shared/ui/States';

export function LessonPage() {
  const { lessonId = '' } = useParams();
  const resource = useResource(
    useCallback((signal: AbortSignal) => api.lesson(lessonId, signal), [lessonId]),
  );
  const saved = useResource(api.vocabulary);
  const [wordIndex, setWordIndex] = useState(0);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const [audioError, setAudioError] = useState<Record<string, boolean>>({});
  if (resource.loading) return <Loading />;
  if (resource.error) return <ErrorState error={resource.error} retry={resource.retry} />;
  const data = resource.data!;
  const word = data.vocabulary[wordIndex % Math.max(data.vocabulary.length, 1)];
  const isSaved = saved.data?.some((v) => v.vocabularyId === word?.vocabularyId);
  async function save() {
    if (!word || busy || !saved.data) return;
    setBusy(true);
    setError(undefined);
    try {
      if (isSaved) await api.removeWord(word.vocabularyId);
      else await api.saveWord(word.vocabularyId);
      saved.retry();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="focus-page">
      <header className="focus-header">
        <Link to="/" className="brand">
          L / <span>LinguaTrack</span>
        </Link>
        <span>Bài {data.lesson.orderNo}</span>
        <Link to={`/courses/${data.lesson.courseId}`}>← Về khóa học</Link>
      </header>
      <main className="lesson-focus">
        <aside className="panel lesson-outline">
          <p className="eyebrow">BÀI {data.lesson.orderNo}</p>
          <h2>{data.lesson.title}</h2>
          <hr />
          <nav aria-label="Mục lục bài học">
            <a href="#vocabulary">1 Từ vựng</a>
            <a href="#grammar">2 Ngữ pháp</a>
            <a href="#listening">3 Nghe</a>
            <a href="#reading">4 Đọc</a>
            <a href="#exercise">5 Bài tập cuối bài</a>
          </nav>
          <small className="muted">Học nội dung, sau đó làm bài tập cuối bài để hoàn thành.</small>
        </aside>
        <div className="lesson-body stack">
          <header className="page-heading">
            <p className="eyebrow">HỌC TIẾNG ANH TRONG NGỮ CẢNH</p>
            <h1>{data.lesson.title}</h1>
            <p className="muted text-content">{data.lesson.content}</p>
          </header>
          <section id="vocabulary" className="panel word-study stack">
            <div className="inline-actions">
              <span className="badge">01 / TỪ VỰNG</span>
              {word && (
                <span className="muted">
                  {(wordIndex % data.vocabulary.length) + 1} / {data.vocabulary.length} từ
                </span>
              )}
            </div>
            {!word ? (
              <Empty title="Chưa có từ vựng trong bài này." />
            ) : (
              <>
                <h2>{word.word}</h2>
                {word.phonetic && <p className="purple">{word.phonetic}</p>}
                <strong>{word.meaning}</strong>
                {word.example && (
                  <div className="specimen">
                    <p className="eyebrow">TRONG MỘT CÂU</p>
                    <blockquote>{word.example}</blockquote>
                  </div>
                )}
                <FormError error={error} />
                {saved.error && <ErrorState error={saved.error} retry={saved.retry} />}
                <div className="inline-actions">
                  <button
                    className="button secondary"
                    disabled={busy || saved.loading || !saved.data}
                    onClick={() => void save()}
                  >
                    {busy ? 'Đang lưu…' : isSaved ? 'Bỏ lưu từ' : 'Lưu từ'}
                  </button>
                  <button
                    className="button"
                    disabled={data.vocabulary.length < 2}
                    onClick={() => setWordIndex((i) => i + 1)}
                  >
                    Từ tiếp theo →
                  </button>
                </div>
              </>
            )}
          </section>
          {(['GRAMMAR', 'LISTENING', 'READING'] as const).map((type, index) => (
            <section className="panel stack" id={type.toLowerCase()} key={type}>
              <p className="eyebrow">
                0{index + 2} /{' '}
                {type === 'GRAMMAR' ? 'NGỮ PHÁP' : type === 'LISTENING' ? 'NGHE' : 'ĐỌC'}
              </p>
              {data.contents.filter((c) => c.type === type).length ? (
                data.contents
                  .filter((c) => c.type === type)
                  .map((content) => (
                    <article key={content.contentId} className="stack">
                      <h2>{content.title}</h2>
                      {content.availability === 'UNAVAILABLE' ? (
                        <p className="notice">Tài nguyên hiện không khả dụng.</p>
                      ) : (
                        <>
                          {content.text && <p className="text-content">{content.text}</p>}
                          {type === 'LISTENING' &&
                            (content.resourceUrl && /^https?:\/\//.test(content.resourceUrl) ? (
                              audioError[content.contentId] ? (
                                <p className="notice" role="status">
                                  Chưa phát được tài nguyên nghe. Hãy thử lại sau.
                                </p>
                              ) : (
                                <audio
                                  controls
                                  preload="none"
                                  src={content.resourceUrl}
                                  onError={() =>
                                    setAudioError((s) => ({ ...s, [content.contentId]: true }))
                                  }
                                >
                                  Trình duyệt chưa hỗ trợ phát audio.
                                </audio>
                              )
                            ) : (
                              <p className="notice">Chưa có tài nguyên nghe khả dụng.</p>
                            ))}
                        </>
                      )}
                    </article>
                  ))
              ) : (
                <p className="muted">
                  Bài học chưa có nội dung{' '}
                  {type === 'GRAMMAR' ? 'ngữ pháp' : type === 'LISTENING' ? 'nghe' : 'đọc'}.
                </p>
              )}
            </section>
          ))}
          <section id="exercise" className="between exercise-cta">
            <p className="muted">Khi sẵn sàng, hãy làm bài tập cuối bài.</p>
            <Link className="button" to={`/assessments/${data.exerciseId}`}>
              Đến bài tập cuối bài →
            </Link>
          </section>
        </div>
      </main>
    </div>
  );
}
