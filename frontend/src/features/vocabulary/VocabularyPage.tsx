import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../../shared/api/platform';
import { useResource } from '../../shared/hooks/useResource';
import { Empty, ErrorState, FormError, Loading, PageHeading } from '../../shared/ui/States';

export function VocabularyPage() {
  const list = useResource(api.vocabulary);
  const [search, setSearch] = useState('');
  const [busy, setBusy] = useState<string>();
  const [error, setError] = useState<unknown>();
  async function remove(id: string) {
    setBusy(id);
    setError(undefined);
    try {
      await api.removeWord(id);
      list.retry();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(undefined);
    }
  }
  if (list.loading) return <Loading />;
  if (list.error) return <ErrorState error={list.error} retry={list.retry} />;
  const items = list.data!.filter((v) =>
    `${v.content?.word || ''} ${v.content?.meaning || ''}`
      .toLocaleLowerCase('vi')
      .includes(search.toLocaleLowerCase('vi')),
  );
  return (
    <>
      <PageHeading eyebrow="SỔ TỪ VỰNG CỦA BẠN" title="Gặp lại những từ đã học.">
        Những từ bạn đã lưu từ các bài học, ở cùng một nơi.
      </PageHeading>
      <label className="search-field">
        <span className="sr-only">Tìm từ đã lưu</span>
        <input
          placeholder="Tìm từ hoặc nghĩa tiếng Việt…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </label>
      <FormError error={error} />
      <div className="learning-focus">
        <section className="panel vocabulary-list">
          {!items.length ? (
            <Empty title={search ? 'Không tìm thấy từ phù hợp.' : 'Bạn chưa lưu từ nào.'}>
              <Link to="/courses">Mở bài học và chọn “Lưu từ” →</Link>
            </Empty>
          ) : (
            items.map((word) => (
              <article className="saved-word" key={word.vocabularyId}>
                <div className="between">
                  <h2>{word.content?.word || 'Từ vựng không khả dụng'}</h2>
                  <button
                    className="text-button"
                    disabled={!!busy}
                    onClick={() => void remove(word.vocabularyId)}
                  >
                    {busy === word.vocabularyId ? 'Đang bỏ lưu…' : 'Bỏ lưu'}
                  </button>
                </div>
                {word.availability === 'UNAVAILABLE' || !word.content ? (
                  <p className="notice">Nguồn từ hiện không khả dụng. Bạn vẫn có thể bỏ lưu.</p>
                ) : (
                  <>
                    <p className="purple">
                      {word.content.phonetic} · {word.content.meaning}
                    </p>
                    {word.content.example && <p>{word.content.example}</p>}
                  </>
                )}
              </article>
            ))
          )}
        </section>
        <aside className="specimen stack">
          <p className="eyebrow">TỪ ĐƯỢC LƯU</p>
          <strong className="notebook-count">{list.data!.length}</strong>
          <h2>
            Một nơi để tìm lại
            <br />
            những từ bạn muốn nhớ.
          </h2>
          <hr />
          <p className="muted">
            Muốn lưu thêm?
            <br />
            Mở bài học và chọn “Lưu từ”.
          </p>
          <Link className="button secondary" to="/courses">
            Về khóa học
          </Link>
        </aside>
      </div>
    </>
  );
}
