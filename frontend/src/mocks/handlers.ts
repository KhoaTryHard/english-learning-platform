import { delay, http, HttpResponse } from 'msw';
import type { Schema } from '../shared/api/client';
import {
  assessments,
  contents,
  courses,
  demoId,
  hashPassword,
  lessons,
  levels,
  modules,
  questions,
  vocabulary,
} from './data';
import type { DemoStore } from './data';

// Development/test simulator only. It does not implement backend security, database or Kafka acceptance.
export function createHandlers(store: DemoStore, persist: () => void = () => undefined) {
  const error = (status: number, code: string, message: string) =>
    HttpResponse.json({ code, message, traceId: crypto.randomUUID(), errors: [] }, { status });
  const json = (data: unknown, status = 200) => {
    persist();
    return new HttpResponse(JSON.stringify(data), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  };
  const page = <T>(items: T[], url: URL) => {
    const n = Math.max(0, Number(url.searchParams.get('page') || 0));
    const size = Math.max(1, Math.min(100, Number(url.searchParams.get('size') || 20)));
    return json({
      items: items.slice(n * size, (n + 1) * size),
      page: n,
      size,
      totalElements: items.length,
      totalPages: Math.ceil(items.length / size),
    });
  };
  const finish = (attempt: Schema<'Attempt'>, userId: string, timeout: boolean) => {
    if (attempt.result) return attempt.result;
    const assessment = assessments.find((a) => a.assessmentId === attempt.assessmentId)!;
    const earnedPoints = attempt.answers.reduce(
      (n, a) =>
        n +
        ((a.questionType === 'SINGLE_CHOICE' && a.optionId === demoId(702)) ||
        (a.questionType === 'TRUE_FALSE' && a.value === false)
          ? 1
          : 0),
      0,
    );
    const completedAt = new Date().toISOString();
    const result: Schema<'Result'> = {
      resultId: crypto.randomUUID(),
      submissionId: attempt.attemptId,
      assessmentId: assessment.assessmentId,
      assessmentType: assessment.type,
      courseId: attempt.courseId,
      levelIdAtStart: attempt.levelIdAtStart,
      lessonId: assessment.lessonId,
      status: timeout ? 'TIMEOUT' : 'SUBMITTED',
      earnedPoints,
      maxPoints: questions.length,
      scorePercent: (earnedPoints / questions.length) * 100,
      completedAt,
      progressSync: 'EVENTUAL',
    };
    attempt.status = result.status;
    attempt.result = result;
    store.results[result.resultId] = { userId, data: result };
    (store.history[userId] ||= []).unshift({
      historyId: crypto.randomUUID(),
      assessmentId: result.assessmentId,
      assessmentType: result.assessmentType,
      submissionId: result.submissionId,
      resultId: result.resultId,
      courseId: result.courseId,
      levelIdAtStart: result.levelIdAtStart,
      lessonId: result.lessonId,
      earnedPoints: result.earnedPoints,
      maxPoints: result.maxPoints,
      scorePercent: result.scorePercent,
      completedAt,
    });
    // Mock assumes event delivered. This is never evidence of a Kafka transaction/consumer.
    if (assessment.type === 'EXERCISE' && assessment.lessonId) {
      const completed = (store.completed[userId] ||= []);
      if (!completed.includes(assessment.lessonId)) completed.push(assessment.lessonId);
    }
    persist();
    return result;
  };
  const readAttempt = (record: DemoStore['attempts'][string]) => {
    const a = record.data;
    a.serverNow = new Date().toISOString();
    if (a.status === 'IN_PROGRESS' && a.deadlineAt && Date.now() >= Date.parse(a.deadlineAt))
      finish(a, record.userId, true);
    return a;
  };
  const progress = (userId: string, courseId: string): Schema<'Progress'> => {
    const ownLessons = lessons.filter((l) => l.courseId === courseId);
    const completedLessons = ownLessons.filter((l) =>
      store.completed[userId]?.includes(l.lessonId),
    ).length;
    return {
      courseId,
      completedLessons,
      totalLessons: ownLessons.length,
      progressPercent: ownLessons.length ? (completedLessons / ownLessons.length) * 100 : null,
      generatedAt: new Date().toISOString(),
      syncState: 'EVENTUAL',
    };
  };

  return [
    http.all('*/api/v1/*', async ({ request }) => {
      await delay(80);
      const url = new URL(request.url);
      const path = url.pathname.replace('/api/v1', '');
      const method = request.method;
      const userId = store.currentUserId;
      const user = userId ? store.users[userId]?.profile : undefined;
      if (path === '/auth/csrf' && method === 'GET')
        return json({ csrfToken: store.csrfToken, headerName: 'X-CSRF-Token' });
      if (
        !['GET', 'HEAD', 'OPTIONS'].includes(method) &&
        request.headers.get('X-CSRF-Token') !== store.csrfToken
      )
        return error(
          403,
          'CSRF_INVALID',
          'Phiên xác thực yêu cầu đã thay đổi. Vui lòng tải lại trang.',
        );
      if (path === '/auth/register' && method === 'POST') {
        const data = (await request.json()) as Schema<'RegisterRequest'>;
        if ([...data.password].length < 15 || new TextEncoder().encode(data.password).length > 72)
          return error(400, 'VALIDATION_ERROR', 'Mật khẩu không hợp lệ.');
        if (Object.values(store.users).some((u) => u.profile.email === data.email))
          return error(409, 'REGISTRATION_CONFLICT', 'Không thể tạo tài khoản với thông tin này.');
        const profile: Schema<'Profile'> = {
          userId: crypto.randomUUID(),
          email: data.email,
          fullName: data.fullName?.trim() || null,
          role: 'LEARNER',
          status: 'ACTIVE',
        };
        store.users[profile.userId] = { profile, passwordHash: await hashPassword(data.password) };
        return json(profile, 201);
      }
      if (path === '/auth/login' && method === 'POST') {
        const data = (await request.json()) as Schema<'LoginRequest'>;
        const passwordHash = await hashPassword(data.password);
        const account = Object.values(store.users).find(
          (u) =>
            u.profile.email === data.email &&
            u.passwordHash === passwordHash &&
            u.profile.status === 'ACTIVE',
        );
        if (!account)
          return error(401, 'INVALID_CREDENTIALS', 'Email hoặc mật khẩu không chính xác.');
        store.currentUserId = account.profile.userId;
        store.csrfToken = crypto.randomUUID();
        return json({
          user: account.profile,
          expiresAt: new Date(Date.now() + 1800000).toISOString(),
        });
      }
      if (!user || user.status !== 'ACTIVE')
        return error(401, 'SESSION_INVALID', 'Phiên học đã hết hạn. Hãy đăng nhập lại.');
      const uid = user.userId;
      if (path === '/auth/logout' && method === 'POST') {
        store.currentUserId = null;
        store.csrfToken = crypto.randomUUID();
        persist();
        return new HttpResponse(null, { status: 204 });
      }
      if (path === '/me/profile') {
        if (method === 'PATCH') {
          const data = (await request.json()) as Schema<'ProfilePatch'>;
          user.fullName = data.fullName?.trim() || null;
        }
        return json(user);
      }
      if (path === '/me/learning-setup') {
        if (method === 'PUT') {
          const data = (await request.json()) as Schema<'SetupWrite'>;
          if (!levels.some((l) => l.levelId === data.levelId && l.courseId === data.courseId))
            return error(400, 'INVALID_LEVEL', 'Trình độ không thuộc khóa học này.');
          store.setups[uid] = { ...data, updatedAt: new Date().toISOString() };
        }
        return json(store.setups[uid] || { courseId: null, levelId: null, updatedAt: null });
      }
      if (path === '/courses') return page(courses, url);
      const courseMatch = path.match(/^\/courses\/([^/]+)(?:\/(levels|modules))?$/);
      if (courseMatch) {
        const c = courses.find((c) => c.courseId === courseMatch[1]);
        if (!c) return error(404, 'CONTENT_UNAVAILABLE', 'Khóa học không khả dụng.');
        if (courseMatch[2] === 'levels')
          return page(
            levels.filter((l) => l.courseId === c.courseId),
            url,
          );
        if (courseMatch[2] === 'modules')
          return page(
            modules.filter((m) => m.courseId === c.courseId),
            url,
          );
        return json(c);
      }
      const moduleMatch = path.match(/^\/modules\/([^/]+)\/lessons$/);
      if (moduleMatch)
        return page(
          lessons.filter((l) => l.moduleId === moduleMatch[1]),
          url,
        );
      const lessonMatch = path.match(/^\/lessons\/([^/]+)(\/exercise)?$/);
      if (lessonMatch) {
        const lesson = lessons.find((l) => l.lessonId === lessonMatch[1]);
        if (!lesson) return error(404, 'CONTENT_UNAVAILABLE', 'Bài học không khả dụng.');
        const exercise = assessments.find((a) => a.lessonId === lesson.lessonId)!;
        if (lessonMatch[2]) return json(exercise);
        return json({
          lesson,
          vocabulary: vocabulary.filter((v) => v.lessonId === lesson.lessonId),
          contents: contents.filter((c) => c.lessonId === lesson.lessonId),
          exerciseId: exercise.assessmentId,
        });
      }
      if (path === '/me/vocabulary') return page(store.saved[uid] || [], url);
      const wordMatch = path.match(/^\/me\/vocabulary\/([^/]+)$/);
      if (wordMatch) {
        const list = (store.saved[uid] ||= []);
        if (method === 'DELETE') {
          store.saved[uid] = list.filter((w) => w.vocabularyId !== wordMatch[1]);
          persist();
          return new HttpResponse(null, { status: 204 });
        }
        const word = vocabulary.find((w) => w.vocabularyId === wordMatch[1]);
        if (!word) return error(404, 'CONTENT_UNAVAILABLE', 'Từ không khả dụng.');
        let saved = list.find((s) => s.vocabularyId === word.vocabularyId);
        if (!saved) {
          const { word: text, meaning, example, phonetic } = word;
          saved = {
            vocabularyId: word.vocabularyId,
            addedAt: new Date().toISOString(),
            availability: 'AVAILABLE',
            content: { word: text, meaning, example, phonetic },
          };
          list.push(saved);
        }
        return json(saved);
      }
      if (path === '/assessments')
        return page(
          assessments.filter(
            (a) =>
              (!url.searchParams.get('courseId') ||
                a.courseId === url.searchParams.get('courseId')) &&
              (!url.searchParams.get('type') || a.type === url.searchParams.get('type')),
          ),
          url,
        );
      const assessmentMatch = path.match(/^\/assessments\/([^/]+)(\/attempts)?$/);
      if (assessmentMatch) {
        const assessment = assessments.find((a) => a.assessmentId === assessmentMatch[1]);
        if (!assessment) return error(404, 'NOT_FOUND', 'Bài đánh giá không khả dụng.');
        if (!assessmentMatch[2]) return json(assessment);
        const key = request.headers.get('Idempotency-Key');
        if (!key) return error(400, 'VALIDATION_ERROR', 'Thiếu khóa chống tạo trùng.');
        const scopedKey = `${uid}:${assessment.assessmentId}:${key}`;
        if (store.idempotency[scopedKey])
          return json(readAttempt(store.attempts[store.idempotency[scopedKey]]), 201);
        const setup = store.setups[uid];
        if (!setup?.levelId || setup.courseId !== assessment.courseId)
          return error(
            409,
            'LEARNING_SETUP_MISMATCH',
            'Hãy chọn Course/Level phù hợp trước khi bắt đầu.',
          );
        const now = new Date().toISOString();
        const attempt: Schema<'Attempt'> = {
          attemptId: crypto.randomUUID(),
          assessmentId: assessment.assessmentId,
          courseId: assessment.courseId,
          levelIdAtStart: setup.levelId,
          status: 'IN_PROGRESS',
          startedAt: now,
          deadlineAt:
            assessment.timeLimitSeconds === null
              ? null
              : new Date(Date.now() + assessment.timeLimitSeconds * 1000).toISOString(),
          serverNow: now,
          questions: structuredClone(questions),
          answers: [],
          result: null,
        };
        store.attempts[attempt.attemptId] = { userId: uid, data: attempt };
        store.idempotency[scopedKey] = attempt.attemptId;
        return json(attempt, 201);
      }
      if (path === '/me/attempts')
        return page(
          Object.values(store.attempts)
            .filter((a) => a.userId === uid)
            .map(readAttempt)
            .sort((a, b) => b.startedAt.localeCompare(a.startedAt)),
          url,
        );
      const attemptMatch = path.match(/^\/attempts\/([^/]+)(?:\/(answers|submit))?$/);
      if (attemptMatch) {
        const record = store.attempts[attemptMatch[1]];
        if (!record || record.userId !== uid)
          return error(404, 'NOT_FOUND', 'Không tìm thấy lượt làm.');
        const attempt = readAttempt(record);
        if (attemptMatch[2] === 'answers' && attempt.status === 'IN_PROGRESS') {
          const data = (await request.json()) as Schema<'AnswersWrite'>;
          attempt.answers = data.answers;
        }
        if (attemptMatch[2] === 'submit')
          return json(finish(attempt, uid, attempt.status === 'TIMEOUT'));
        return json(attempt);
      }
      const resultMatch = path.match(/^\/results\/([^/]+)$/);
      if (resultMatch) {
        const r = store.results[resultMatch[1]];
        return r?.userId === uid
          ? json(r.data)
          : error(404, 'NOT_FOUND', 'Không tìm thấy kết quả.');
      }
      if (path === '/me/history') return page(store.history[uid] || [], url);
      if (path === '/me/progress')
        return json(
          progress(uid, url.searchParams.get('courseId') || store.setups[uid]?.courseId || ''),
        );
      if (path === '/me/dashboard') {
        const setup = store.setups[uid] || { courseId: null, levelId: null, updatedAt: null };
        return json({
          setup,
          progress: setup.courseId ? progress(uid, setup.courseId) : null,
          recentHistory: (store.history[uid] || []).slice(0, 5),
          generatedAt: new Date().toISOString(),
          syncState: 'EVENTUAL',
        });
      }
      return error(404, 'NOT_FOUND', 'Endpoint không có trong chế độ demo.');
    }),
  ];
}
