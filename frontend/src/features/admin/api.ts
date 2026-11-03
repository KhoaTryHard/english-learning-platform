import { allPages, mutate, request } from '../../shared/api/client';
import type { Schema } from '../../shared/api/client';

export type AdminKind =
  'users' | 'courses' | 'modules' | 'lessons' | 'vocabulary' | 'learning-contents';
export type AdminRecord =
  | Schema<'Profile'>
  | Schema<'Course'>
  | Schema<'Module'>
  | Schema<'Lesson'>
  | Schema<'Vocabulary'>
  | Schema<'LearningContent'>;
export type Draft = Record<string, string>;
const segment = encodeURIComponent;

export function recordId(row: AdminRecord) {
  if ('userId' in row) return row.userId;
  if ('moduleId' in row && !('lessonId' in row)) return row.moduleId;
  if ('vocabularyId' in row) return row.vocabularyId;
  if ('contentId' in row) return row.contentId;
  if ('lessonId' in row) return row.lessonId;
  return row.courseId;
}

export function recordTitle(row: AdminRecord) {
  if ('email' in row) return row.fullName || row.email;
  if ('word' in row) return row.word;
  return 'name' in row ? row.name : row.title;
}

export function listAdmin(
  kind: AdminKind,
  filter: string,
  signal?: AbortSignal,
): Promise<AdminRecord[]> {
  const key =
    kind === 'users'
      ? 'q'
      : kind === 'modules'
        ? 'courseId'
        : kind === 'lessons'
          ? 'moduleId'
          : 'lessonId';
  const query = filter && kind !== 'courses' ? `?${key}=${segment(filter)}` : '';
  return allPages<AdminRecord>(`/admin/${kind}${query}`, signal);
}

export function readAdmin(kind: AdminKind, id: string, signal?: AbortSignal) {
  return request<AdminRecord>(`/admin/${kind}/${segment(id)}`, { signal });
}

export function writeAdmin(kind: AdminKind, id: string | undefined, d: Draft) {
  // Build a whitelist per operation. Never submit readonly IDs, role, email or parent changes.
  const published = { published: d.published === 'true' };
  let body:
    | Schema<'AdminUserPatch'>
    | Schema<'CourseCreate'>
    | Schema<'CoursePatch'>
    | Schema<'ModuleCreate'>
    | Schema<'ModulePatch'>
    | Schema<'LessonCreate'>
    | Schema<'LessonPatch'>
    | Schema<'VocabularyPatch'>
    | Schema<'LearningContentCreate'>
    | Schema<'LearningContentPatch'>;
  switch (kind) {
    case 'users':
      if (!id) throw new Error('Tài khoản Learner được tạo qua đăng ký.');
      if (d.status !== 'ACTIVE' && d.status !== 'BLOCKED')
        throw new Error('Trạng thái tài khoản không hợp lệ.');
      body = { fullName: d.fullName.trim() || null, status: d.status };
      break;
    case 'courses':
      body = id ? { name: d.name.trim(), ...published } : { name: d.name.trim() };
      break;
    case 'modules':
      body = id
        ? { title: d.title.trim(), orderNo: Number(d.orderNo), ...published }
        : { courseId: d.courseId, title: d.title.trim(), orderNo: Number(d.orderNo) };
      break;
    case 'lessons':
      body = id
        ? {
            title: d.title.trim(),
            content: d.content.trim(),
            orderNo: Number(d.orderNo),
            ...published,
          }
        : {
            moduleId: d.moduleId,
            title: d.title.trim(),
            content: d.content.trim(),
            orderNo: Number(d.orderNo),
          };
      break;
    case 'vocabulary':
      if (!id) throw new Error('Từ vựng mới được thêm qua Import Excel.');
      body = {
        word: d.word.trim(),
        meaning: d.meaning.trim(),
        example: d.example.trim() || null,
        phonetic: d.phonetic.trim() || null,
        ...published,
      };
      break;
    case 'learning-contents': {
      const type = d.type;
      if (type !== 'GRAMMAR' && type !== 'LISTENING' && type !== 'READING')
        throw new Error('Loại học liệu không hợp lệ.');
      const fields = {
        title: d.title.trim(),
        text: type === 'LISTENING' ? null : d.text.trim(),
        resourceUrl: type === 'LISTENING' ? d.resourceUrl.trim() : null,
      };
      body = id ? { ...fields, ...published } : { lessonId: d.lessonId, type, ...fields };
      break;
    }
  }
  return mutate<AdminRecord>(
    `/admin/${kind}${id ? `/${segment(id)}` : ''}`,
    id ? 'PATCH' : 'POST',
    body,
  );
}

export type ImportKind = 'vocabulary' | 'lessons';
export function uploadImport(kind: ImportKind, file: File, targetId: string, orderNo: string) {
  const data = new FormData();
  data.set('file', file);
  data.set(kind === 'vocabulary' ? 'lessonId' : 'moduleId', targetId);
  if (kind === 'lessons') data.set('orderNo', orderNo);
  // Browser supplies the multipart boundary; a write is never retried automatically.
  return mutate<Schema<'ImportOutcome'>>(`/admin/imports/${kind}`, 'POST', data);
}

export async function downloadTemplate(kind: ImportKind) {
  const blob = await request<Blob>(`/admin/import-templates/${kind}`, { responseType: 'blob' });
  const url = URL.createObjectURL(blob);
  try {
    const link = document.createElement('a');
    link.href = url;
    link.download = kind === 'vocabulary' ? 'VocabularyTemplate.xlsx' : 'LessonTemplate.docx';
    link.click();
  } finally {
    // Give the browser time to begin reading the URL before releasing it.
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }
}
