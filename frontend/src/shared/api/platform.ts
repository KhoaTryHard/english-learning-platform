import { allPages, mutate, request, resetCsrf } from './client';
import type { Schema } from './client';

const id = encodeURIComponent;
export const api = {
  profile: (signal?: AbortSignal) => request<Schema<'Profile'>>('/me/profile', { signal }),
  login: async (data: Schema<'LoginRequest'>) => {
    const result = await mutate<Schema<'LoginResponse'>>('/auth/login', 'POST', data);
    resetCsrf();
    return result;
  },
  register: (data: Schema<'RegisterRequest'>) =>
    mutate<Schema<'Profile'>>('/auth/register', 'POST', data),
  logout: async () => {
    await mutate<void>('/auth/logout', 'POST');
    resetCsrf();
  },
  updateProfile: (fullName: string | null) =>
    mutate<Schema<'Profile'>>('/me/profile', 'PATCH', { fullName }),
  setup: (signal?: AbortSignal) => request<Schema<'Setup'>>('/me/learning-setup', { signal }),
  updateSetup: (data: Schema<'SetupWrite'>) =>
    mutate<Schema<'Setup'>>('/me/learning-setup', 'PUT', data),
  courses: (signal?: AbortSignal) => allPages<Schema<'Course'>>('/courses', signal),
  course: (courseId: string, signal?: AbortSignal) =>
    request<Schema<'Course'>>(`/courses/${id(courseId)}`, { signal }),
  levels: (courseId: string, signal?: AbortSignal) =>
    allPages<Schema<'Level'>>(`/courses/${id(courseId)}/levels`, signal),
  modules: (courseId: string, signal?: AbortSignal) =>
    allPages<Schema<'Module'>>(`/courses/${id(courseId)}/modules`, signal),
  lessons: (moduleId: string, signal?: AbortSignal) =>
    allPages<Schema<'Lesson'>>(`/modules/${id(moduleId)}/lessons`, signal),
  lesson: (lessonId: string, signal?: AbortSignal) =>
    request<Schema<'LessonLearning'>>(`/lessons/${id(lessonId)}`, { signal }),
  vocabulary: (signal?: AbortSignal) =>
    allPages<Schema<'SavedVocabulary'>>('/me/vocabulary', signal),
  saveWord: (vocabularyId: string) =>
    mutate<Schema<'SavedVocabulary'>>(`/me/vocabulary/${id(vocabularyId)}`, 'PUT'),
  removeWord: (vocabularyId: string) =>
    mutate<void>(`/me/vocabulary/${id(vocabularyId)}`, 'DELETE'),
  dashboard: (signal?: AbortSignal) => request<Schema<'Dashboard'>>('/me/dashboard', { signal }),
  progress: (courseId: string, signal?: AbortSignal) =>
    request<Schema<'Progress'>>(`/me/progress?courseId=${id(courseId)}`, { signal }),
  history: (signal?: AbortSignal) => allPages<Schema<'HistoryEntry'>>('/me/history', signal),
  assessments: (courseId?: string, signal?: AbortSignal) =>
    allPages<Schema<'Assessment'>>(
      `/assessments${courseId ? `?courseId=${id(courseId)}` : ''}`,
      signal,
    ),
  assessment: (assessmentId: string, signal?: AbortSignal) =>
    request<Schema<'Assessment'>>(`/assessments/${id(assessmentId)}`, { signal }),
  exercise: (lessonId: string, signal?: AbortSignal) =>
    request<Schema<'Assessment'>>(`/lessons/${id(lessonId)}/exercise`, { signal }),
  attempts: (signal?: AbortSignal) => allPages<Schema<'Attempt'>>('/me/attempts', signal),
  startAttempt: (assessmentId: string, key: string) =>
    mutate<Schema<'Attempt'>>(`/assessments/${id(assessmentId)}/attempts`, 'POST', undefined, {
      'Idempotency-Key': key,
    }),
  attempt: (attemptId: string, signal?: AbortSignal) =>
    request<Schema<'Attempt'>>(`/attempts/${id(attemptId)}`, { signal }),
  answers: (attemptId: string, answers: Schema<'Answer'>[]) =>
    mutate<Schema<'Attempt'>>(`/attempts/${id(attemptId)}/answers`, 'PUT', { answers }),
  submit: (attemptId: string) =>
    mutate<Schema<'Result'>>(`/attempts/${id(attemptId)}/submit`, 'POST'),
  result: (resultId: string, signal?: AbortSignal) =>
    request<Schema<'Result'>>(`/results/${id(resultId)}`, { signal }),
};
