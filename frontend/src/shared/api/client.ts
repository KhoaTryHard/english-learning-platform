import axios from 'axios';
import type { AxiosRequestConfig } from 'axios';
import type { components } from './schema';

export type Schema<K extends keyof components['schemas']> = components['schemas'][K];

export class ApiFailure extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public fields: Schema<'FieldError'>[] = [],
    public traceId?: string,
  ) {
    super(message);
  }
}

const http = axios.create({ baseURL: '/api/v1', withCredentials: true, timeout: 15000 });
let csrf: string | undefined;
let csrfRequest: Promise<string> | undefined;

export function resetCsrf() {
  csrf = undefined;
  csrfRequest = undefined;
}

export async function getCsrf(): Promise<string> {
  if (csrf) return csrf;
  if (!csrfRequest) {
    csrfRequest = request<Schema<'CsrfResponse'>>('/auth/csrf')
      .then((data) => {
        csrf = data.csrfToken;
        return csrf;
      })
      .finally(() => {
        csrfRequest = undefined;
      });
  }
  return csrfRequest;
}

export async function request<T>(path: string, config: AxiosRequestConfig = {}): Promise<T> {
  try {
    return (await http.request<T>({ url: path, ...config })).data;
  } catch (cause) {
    if (axios.isCancel(cause)) throw cause;
    if (axios.isAxiosError<Schema<'Error'>>(cause)) {
      const status = cause.response?.status || 0;
      const body = cause.response?.data;
      if (status === 401 && !path.startsWith('/auth/')) {
        resetCsrf();
        window.dispatchEvent(new Event('linguatrack:session-expired'));
      }
      throw new ApiFailure(
        status,
        body?.code || 'NETWORK_ERROR',
        body?.message ||
          (status
            ? 'Yêu cầu chưa thực hiện được.'
            : 'Không kết nối được máy chủ. Hãy kiểm tra kết nối và thử lại.'),
        body?.errors || [],
        body?.traceId,
      );
    }
    throw cause;
  }
}

// Never automatically retry a write: a lost response may follow a committed mutation.
export async function mutate<T>(
  path: string,
  method: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  data?: unknown,
  headers?: Record<string, string>,
): Promise<T> {
  const token = await getCsrf();
  return request<T>(path, { method, data, headers: { 'X-CSRF-Token': token, ...headers } });
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// Lists use the actual page response; never silently truncate the first 20/100 records.
export async function allPages<T>(path: string, signal?: AbortSignal): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0; ; page++) {
    const separator = path.includes('?') ? '&' : '?';
    const data = await request<Page<T>>(`${path}${separator}page=${page}&size=100`, { signal });
    items.push(...data.items);
    if (page + 1 >= data.totalPages) return items;
  }
}
