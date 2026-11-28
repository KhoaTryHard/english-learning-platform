import { useState } from 'react';
import { Link, NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { FormError } from '../../shared/ui/States';

export const adminLinks = [
  ['/admin/users', 'Người dùng'],
  ['/admin/courses', 'Khóa học'],
  ['/admin/modules', 'Module'],
  ['/admin/lessons', 'Bài học'],
  ['/admin/vocabulary', 'Từ vựng'],
  ['/admin/learning-contents', 'Học liệu'],
  ['/admin/import', 'Import dữ liệu'],
  ['/admin/assessments', 'Bài luyện tập và kiểm tra'],
  ['/admin/statistics', 'Thống kê'],
];

export function AdminLayout() {
  const { user, logout } = useAuth();
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
    <div className="app-shell admin-shell">
      <a className="skip-link" href="#main-content">
        Đến nội dung chính
      </a>
      <div className="mobile-header">
        <Link className="brand" to="/admin">
          L / <span>LinguaTrack</span>
        </Link>
        <button
          className="text-button"
          aria-expanded={menu}
          aria-controls="admin-navigation"
          onClick={() => setMenu(!menu)}
        >
          {menu ? 'Đóng' : 'Menu'}
        </button>
      </div>
      <aside className={`sidebar ${menu ? 'open' : ''}`}>
        <Link className="brand desktop-brand" to="/admin">
          L / <span>LinguaTrack</span>
        </Link>
        <p className="sidebar-label">KHÔNG GIAN QUẢN TRỊ</p>
        <nav id="admin-navigation" aria-label="Điều hướng quản trị">
          <NavLink to="/admin" end onClick={() => setMenu(false)}>
            Tổng quan
          </NavLink>
          {adminLinks.map(([to, label]) => (
            <NavLink key={to} to={to} onClick={() => setMenu(false)}>
              {label}
            </NavLink>
          ))}
        </nav>
        <p className="sidebar-footer">
          Nội dung rõ ràng.
          <br />
          Trải nghiệm học tốt hơn.
        </p>
        <button className="text-button logout" disabled={busy} onClick={() => void signOut()}>
          {busy ? 'Đang đăng xuất…' : 'Đăng xuất'}
        </button>
        <FormError error={error} />
      </aside>
      <div className="workspace">
        <header className="context-bar">
          <span>QUẢN TRỊ / LINGUATRACK</span>
          <span>{user?.fullName || user?.email}</span>
        </header>
        <main className="page-content" id="main-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}

export function AdminHome() {
  return (
    <>
      <header className="page-heading">
        <p className="eyebrow">KHÔNG GIAN QUẢN TRỊ</p>
        <h1>Chăm chút từng bài học.</h1>
        <p className="muted">
          Quản lý người dùng, tổ chức nội dung và chuẩn bị học liệu cho Learner.
        </p>
      </header>
      <div className="admin-home-grid">
        {adminLinks.map(([to, label], index) => (
          <Link className="panel admin-home-card" key={to} to={to}>
            <span className="eyebrow">0{index + 1}</span>
            <h2>{label}</h2>
            <span className="purple">Mở quản lý →</span>
          </Link>
        ))}
      </div>
    </>
  );
}
