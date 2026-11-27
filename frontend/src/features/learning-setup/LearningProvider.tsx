import { createContext, useContext } from 'react';
import type { ReactNode } from 'react';
import { api } from '../../shared/api/platform';
import { useResource } from '../../shared/hooks/useResource';
import type { Schema } from '../../shared/api/client';

interface LearningContextValue {
  setup?: Schema<'Setup'>;
  courses?: Schema<'Course'>[];
  course?: Schema<'Course'>;
  loading: boolean;
  error?: unknown;
  refresh: () => void;
}
const Context = createContext<LearningContextValue | null>(null);
export function LearningProvider({ children }: { children: ReactNode }) {
  const setup = useResource(api.setup);
  const courses = useResource(api.courses);
  const refresh = () => {
    setup.retry();
    courses.retry();
  };
  return (
    <Context
      value={{
        setup: setup.data,
        courses: courses.data,
        course: courses.data?.find((c) => c.courseId === setup.data?.courseId),
        loading: setup.loading || courses.loading,
        error: setup.error || courses.error,
        refresh,
      }}
    >
      {children}
    </Context>
  );
}
export function useLearning() {
  const value = useContext(Context);
  if (!value) throw new Error('LearningProvider missing');
  return value;
}
