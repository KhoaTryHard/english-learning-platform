import { ApiFailure, request } from '../../shared/api/client';
import type { Schema } from '../../shared/api/client';
import { api } from '../../shared/api/platform';

const key = 'linguatrack:admin-hint:v1';
// Only a navigation hint, never a credential or authorization decision. Admin has
// no /me endpoint in public v0.1, so validate the cookie at an Admin-only endpoint.
export function rememberSession(user: Schema<'Profile'> | null) {
  try {
    if (user?.role === 'ADMIN') sessionStorage.setItem(key, user.userId);
    else sessionStorage.removeItem(key);
  } catch {
    /* Storage may be unavailable; login still works for this render. */
  }
}
export async function restoreSession(signal: AbortSignal) {
  try {
    return await api.profile(signal);
  } catch (error) {
    if (!(error instanceof ApiFailure) || error.status !== 403) throw error;
    let id: string | null = null;
    try {
      id = sessionStorage.getItem(key);
    } catch {
      /* Require login again. */
    }
    if (!id || !/^[0-9a-f-]{36}$/i.test(id)) throw error;
    const user = await request<Schema<'Profile'>>(`/admin/users/${encodeURIComponent(id)}`, {
      signal,
    });
    if (user.role !== 'ADMIN' || user.status !== 'ACTIVE') throw error;
    return user;
  }
}
