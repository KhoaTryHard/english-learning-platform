import { useState } from 'react';
import type { FormEvent } from 'react';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { api } from '../../shared/api/platform';
import { FormError } from '../../shared/ui/States';
import { useAuth } from './AuthProvider';
import { validateRegistration } from './validation';

export function AuthPage({ register = false }: { register?: boolean }) {
  const { user, setUser } = useAuth();
  const [error, setError] = useState<unknown>();
  const [busy, setBusy] = useState(false);
  const location = useLocation();
  const navigate = useNavigate();
  const from = location.state?.from;
  const returnTo =
    typeof from === 'string' &&
    from.startsWith('/') &&
    !from.startsWith('//') &&
    !/^\/(login|register)/.test(from)
      ? from
      : '/';
  if (user) return <Navigate to={returnTo} replace />;
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    const form = new FormData(event.currentTarget);
    const email = String(form.get('email')).trim().toLowerCase();
    const password = String(form.get('password'));
    const fullName = String(form.get('fullName') || '').trim();
    const validation = register
      ? validateRegistration(email, password, String(form.get('confirmation')), fullName)
      : undefined;
    if (validation) {
      setError(new Error(validation));
      return;
    }
    setBusy(true);
    setError(undefined);
    try {
      if (register) {
        await api.register({ email, password, fullName: fullName || null });
        navigate('/login', { replace: true, state: { registered: true } });
      } else {
        const result = await api.login({ email, password });
        setUser(result.user);
        navigate(returnTo, { replace: true });
      }
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <main className={`auth-page ${register ? 'registration' : ''}`}>
      <aside className="brand-story">
        <Link className="brand auth-brand" to="/login">
          L / <span>LinguaTrack</span>
        </Link>
        <p className="eyebrow">HỌC TIẾNG ANH, TỪNG BƯỚC MỘT</p>
        <h2>
          Từ một câu mới.
          <br />
          Đến một bước
          <br />
          tự tin hơn.
        </h2>
        <p>Học theo bài, luyện tập sau mỗi chặng và nhìn lại những gì bạn đã làm được.</p>
        <div className="specimen">
          <p className="eyebrow">A LITTLE PROGRESS, EVERY DAY.</p>
          <blockquote>
            “Could I have
            <br />
            the menu, please?”
          </blockquote>
          <p>
            Một câu học hôm nay.
            <br />
            Một tình huống dùng ngày mai.
          </p>
        </div>
      </aside>
      <section className="auth-area">
        <div className="auth-form-wrap">
          <h1>{register ? 'Bắt đầu từ hôm nay.' : 'Chào mừng trở lại.'}</h1>
          <p className="muted">
            {register
              ? 'Tạo tài khoản để học và theo dõi tiến độ của bạn.'
              : 'Đăng nhập để tiếp tục học tiếng Anh.'}
          </p>
          {!register && location.state?.registered && (
            <p className="notice success" role="status">
              Tài khoản đã được tạo. Bạn có thể đăng nhập ngay.
            </p>
          )}
          <form onSubmit={submit} aria-busy={busy}>
            {register && (
              <label>
                Họ tên (không bắt buộc)
                <input name="fullName" autoComplete="name" placeholder="Tên của bạn" />
              </label>
            )}
            <label>
              Email
              <input
                name="email"
                type="email"
                required
                maxLength={254}
                autoComplete="email"
                placeholder="ban@example.com"
              />
            </label>
            <label>
              Mật khẩu
              <input
                name="password"
                type="password"
                required
                autoComplete={register ? 'new-password' : 'current-password'}
                placeholder={register ? 'Tối thiểu 15 ký tự' : 'Nhập mật khẩu'}
                aria-describedby={register ? 'password-help' : undefined}
              />
            </label>
            {register && (
              <>
                <small id="password-help">
                  Ít nhất 15 ký tự Unicode, tối đa 72 byte UTF-8. Mật khẩu được giữ nguyên khoảng
                  trắng.
                </small>
                <label>
                  Xác nhận mật khẩu
                  <input
                    name="confirmation"
                    type="password"
                    required
                    autoComplete="new-password"
                    placeholder="Nhập lại mật khẩu"
                  />
                </label>
              </>
            )}
            <FormError error={error} />
            <button className="button" type="submit" disabled={busy}>
              {busy ? 'Đang xử lý…' : register ? 'Tạo tài khoản →' : 'Đăng nhập →'}
            </button>
          </form>
          <Link to={register ? '/login' : '/register'}>
            {register ? 'Đã có tài khoản? Đăng nhập' : 'Chưa có tài khoản? Đăng ký'}
          </Link>
        </div>
      </section>
    </main>
  );
}
