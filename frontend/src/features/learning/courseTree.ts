import { api } from '../../shared/api/platform';

export async function loadCourseTree(courseId: string, signal?: AbortSignal) {
  const [course, modules] = await Promise.all([
    api.course(courseId, signal),
    api.modules(courseId, signal),
  ]);
  const tree = await Promise.all(
    modules
      .sort((a, b) => a.orderNo - b.orderNo)
      .map(async (module) => ({
        ...module,
        lessons: (await api.lessons(module.moduleId, signal)).sort((a, b) => a.orderNo - b.orderNo),
      })),
  );
  return { course, modules: tree };
}
