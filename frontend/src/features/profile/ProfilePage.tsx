import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { useLearning } from '../learning-setup/LearningProvider';
import { api } from '../../shared/api/platform';
import { FormError, PageHeading } from '../../shared/ui/States';
import avatar from '../../shared/assets/figma/profile-imgAvatar.svg';

export function ProfilePage() {
  const { user, setUser } = useAuth();
  const { course } = useLearning();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  const [success, setSuccess] = useState(false);
  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const fullName = String(new FormData(event.currentTarget).get('fullName') || '').trim();
    if ([...fullName].length > 100) {
      setError(new Error('Họ tên không được vượt quá 100 ký tự.'));
      return;
    }
    setBusy(true);
    setError(undefined);
    setSuccess(false);
    try {
      setUser(await api.updateProfile(fullName || null));
      setSuccess(true);
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeading title="Hồ sơ học viên">
        Quản lý thông tin cơ bản và thiết lập học hiện tại.
      </PageHeading>
      <div className="profile-grid">
        <section className="panel stack">
          <div className="profile-avatar">
            <img src={avatar} alt="" />
          </div>
          <h2>{user?.fullName || 'Học viên'}</h2>
          <p className="muted">{user?.email}</p>
          <span className="badge">Learner</span>
        </section>
        <section className="panel stack">
          <h2>Thông tin cơ bản</h2>
          <form onSubmit={save} className="stack">
            <label>
              Họ tên
              <input name="fullName" defaultValue={user?.fullName || ''} autoComplete="name" />
            </label>
            <label>
              Email
              <input value={user?.email || ''} readOnly />
            </label>
            <div className="read-only-fields">
              <span>Vai trò: {user?.role}</span>
              <span>Trạng thái: {user?.status}</span>
            </div>
            <FormError error={error} />
            {success && (
              <p role="status" className="notice success">
                Đã cập nhật hồ sơ.
              </p>
            )}
            <button type="submit" disabled={busy} className="button">
              {busy ? 'Đang lưu…' : 'Lưu thay đổi'}
            </button>
          </form>
        </section>
      </div>
      <section className="panel stack">
        <h2>Khóa học & trình độ hiện tại</h2>
        <p>{course?.name || 'Chưa chọn khóa học'}</p>
        <Link className="button secondary" to="/setup">
          Cập nhật lựa chọn
        </Link>
        <p className="muted">Thay đổi Course hoặc Level không xóa Learning History cũ.</p>
      </section>
    </>
  );
}
