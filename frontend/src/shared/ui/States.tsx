import type { ReactNode } from 'react';
import { ApiFailure } from '../api/client';

export function Loading({ text = 'Đang tải nội dung…' }: { text?: string }) {
  return (
    <div className="state loading" role="status">
      <span className="spinner" />
      {text}
    </div>
  );
}

export function ErrorState({ error, retry }: { error: unknown; retry?: () => void }) {
  const e = error instanceof Error ? error : new Error('Chưa tải được nội dung.');
  const status = error instanceof ApiFailure ? error.status : 0;
  const title =
    status === 403
      ? 'Bạn chưa có quyền truy cập.'
      : status === 404
        ? 'Nội dung không còn khả dụng.'
        : status === 503
          ? 'Dịch vụ đang tạm gián đoạn.'
          : 'Chưa thực hiện được yêu cầu.';
  return (
    <div className="state error-state" role="alert">
      <h2>{title}</h2>
      <p>{e.message}</p>
      {error instanceof ApiFailure && error.traceId && <small>Mã tra cứu: {error.traceId}</small>}
      {retry && (
        <button className="button secondary" onClick={retry}>
          Thử lại
        </button>
      )}
    </div>
  );
}

export function Empty({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="state empty-state">
      <h2>{title}</h2>
      {children}
    </div>
  );
}

export function FormError({ error }: { error: unknown }) {
  if (!error) return null;
  return (
    <div className="form-error" role="alert">
      <p>{error instanceof Error ? error.message : 'Vui lòng thử lại.'}</p>
      {error instanceof ApiFailure && error.fields.length > 0 && (
        <ul>
          {error.fields.map((f, i) => (
            <li key={i}>
              {f.field}: {f.message}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function PageHeading({
  eyebrow,
  title,
  children,
}: {
  eyebrow?: string;
  title: string;
  children?: ReactNode;
}) {
  return (
    <header className="page-heading">
      {eyebrow && <p className="eyebrow">{eyebrow}</p>}
      <h1>{title}</h1>
      {children && <p className="muted">{children}</p>}
    </header>
  );
}
