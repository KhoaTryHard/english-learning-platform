import { useCallback, useEffect, useState } from 'react';

export function useResource<T>(load: (signal: AbortSignal) => Promise<T>) {
  const [state, setState] = useState<{
    load: typeof load;
    version: number;
    data?: T;
    error?: unknown;
  }>();
  const [version, setVersion] = useState(0);
  const retry = useCallback(() => setVersion((v) => v + 1), []);
  useEffect(() => {
    const controller = new AbortController();
    load(controller.signal).then(
      (data) => {
        if (!controller.signal.aborted) setState({ load, version, data });
      },
      (error) => {
        if (!controller.signal.aborted) setState({ load, version, error });
      },
    );
    return () => controller.abort();
  }, [load, version]);
  const loading = state?.load !== load || state?.version !== version;
  return {
    data: loading ? undefined : state?.data,
    error: loading ? undefined : state?.error,
    loading,
    retry,
  };
}
