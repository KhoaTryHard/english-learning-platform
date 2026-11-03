import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../../../features/auth/AuthProvider';
import { useLearning } from '../../../features/learning-setup/LearningProvider';
import { FormError } from '../../../shared/ui/States';

const links = [
  ['/', 'Trang chủ'],
  ['/courses', 'Khóa học'],
  ['/vocabulary', 'Từ vựng của tôi'],
  ['/progress', 'Tiến độ học tập'],
  ['/history', 'Lịch sử học tập'],
  ['/assessments', 'Quiz / Test'],
];

export function AppLayout() {
  const { user, logout } = useAuth();
  const { course, error: setupError } = useLearning();
  const [menu, setMenu] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<unknown>();
  async function signOut() {
    setBusy(true);
    setError(undefined);
    try {
      await logout();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Đến nội dung chính
      </a>
      <div className="mobile-header">
        <Link to="/" className="brand">
          L / <span>LinguaTrack</span>
        </Link>
        <button
          aria-expanded={menu}
          aria-controls="learner-navigation"
          onClick={() => setMenu(!menu)}
          className="text-button"
        >
          {menu ? 'Đóng' : 'Menu'}
        </button>
      </div>
      <aside className={`sidebar ${menu ? 'open' : ''}`}>
        <Link to="/" className="brand desktop-brand">
          L / <span>LinguaTrack</span>
        </Link>
        <p className="sidebar-label">KHÔNG GIAN HỌC</p>
        <nav id="learner-navigation" aria-label="Điều hướng chính">
          {links.map(([path, label]) => (
            <NavLink to={path} key={path} end={path === '/'} onClick={() => setMenu(false)}>
              {label}
            </NavLink>
          ))}
        </nav>
        <NavLink className="profile-nav" to="/profile" onClick={() => setMenu(false)}>
          Hồ sơ và tài khoản
        </NavLink>
        <p className="sidebar-footer">
          Từng bài học.
          <br />
          Thêm một bước tiến.
        </p>
        <button className="text-button logout" disabled={busy} onClick={() => void signOut()}>
          {busy ? 'Đang đăng xuất…' : 'Đăng xuất'}
        </button>
        <FormError error={error} />
      </aside>
      <div className="workspace">
        <header className="context-bar">
          <span>
            KHÓA ĐANG HỌC /{' '}
            {course?.name || (setupError ? 'Chưa tải được khóa học' : 'Chưa chọn khóa học')}
          </span>
          <Link to="/profile">{user?.fullName || user?.email} · Hồ sơ</Link>
        </header>
        <main id="main-content" className="page-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
