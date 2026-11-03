import type { Schema } from '../shared/api/client';

export const demoId = (n: number) => `00000000-0000-4000-8000-${n.toString().padStart(12, '0')}`;
export const DEMO_EMAIL = 'hung.demo@example.com';
export const DEMO_PASSWORD = 'LearningDemo2026!';
export const courses: Schema<'Course'>[] = [
  { courseId: demoId(1), name: 'Tiếng Anh B1', published: true },
  { courseId: demoId(2), name: 'Tiếng Anh giao tiếp', published: true },
];
export const levels: Schema<'Level'>[] = [
  { levelId: demoId(11), courseId: demoId(1), name: 'B1' },
  { levelId: demoId(12), courseId: demoId(1), name: 'B2' },
  { levelId: demoId(21), courseId: demoId(2), name: 'A1' },
];
export const modules: Schema<'Module'>[] = [
  {
    moduleId: demoId(101),
    courseId: demoId(1),
    title: 'Daily Communication',
    orderNo: 1,
    published: true,
  },
  {
    moduleId: demoId(102),
    courseId: demoId(2),
    title: 'Everyday English',
    orderNo: 1,
    published: true,
  },
];
export const lessons: Schema<'Lesson'>[] = [
  {
    lessonId: demoId(201),
    moduleId: demoId(101),
    courseId: demoId(1),
    title: 'At the Restaurant',
    content: 'Bắt đầu với những từ bạn sẽ gặp trong một thực đơn.',
    orderNo: 1,
    published: true,
  },
  {
    lessonId: demoId(202),
    moduleId: demoId(101),
    courseId: demoId(1),
    title: 'Daily Greetings',
    content: 'Chào hỏi và giới thiệu bản thân.',
    orderNo: 2,
    published: true,
  },
  {
    lessonId: demoId(203),
    moduleId: demoId(102),
    courseId: demoId(2),
    title: 'A friendly conversation',
    content: 'Làm quen với một người bạn mới.',
    orderNo: 1,
    published: true,
  },
];
export const vocabulary: Schema<'Vocabulary'>[] = [
  {
    vocabularyId: demoId(301),
    lessonId: demoId(201),
    courseId: demoId(1),
    word: 'restaurant',
    meaning: 'nhà hàng',
    phonetic: '/ˈres.tər.ɒnt/',
    example: 'We had dinner at a restaurant.',
    published: true,
  },
  {
    vocabularyId: demoId(302),
    lessonId: demoId(201),
    courseId: demoId(1),
    word: 'reservation',
    meaning: 'sự đặt chỗ',
    phonetic: '/ˌrez.əˈveɪ.ʃən/',
    example: 'I made a reservation for two.',
    published: true,
  },
  {
    vocabularyId: demoId(303),
    lessonId: demoId(202),
    courseId: demoId(1),
    word: 'greeting',
    meaning: 'lời chào',
    phonetic: '/ˈɡriː.tɪŋ/',
    example: 'She gave us a warm greeting.',
    published: true,
  },
];
export const contents: Schema<'LearningContent'>[] = [
  {
    contentId: demoId(401),
    lessonId: demoId(201),
    type: 'GRAMMAR',
    title: 'Đưa ra lời đề nghị lịch sự',
    text: 'Could I have + danh từ, please?\nDùng khi bạn muốn gọi món hoặc nhờ nhân viên mang một thứ gì đó.',
    resourceUrl: null,
    published: true,
    availability: 'AVAILABLE',
  },
  {
    contentId: demoId(402),
    lessonId: demoId(201),
    type: 'LISTENING',
    title: 'Ordering dinner',
    text: 'Nghe đoạn hội thoại gọi món tại nhà hàng.',
    resourceUrl: null,
    published: true,
    availability: 'UNAVAILABLE',
  },
  {
    contentId: demoId(403),
    lessonId: demoId(201),
    type: 'READING',
    title: 'A family dinner',
    text: 'Last Friday, Minh and his family visited a small restaurant near their home. They ordered soup, grilled fish, and a fresh salad. The waiter was friendly and explained the daily specials. Everyone enjoyed the meal and planned to return the following weekend.',
    resourceUrl: null,
    published: true,
    availability: 'AVAILABLE',
  },
];
export const questions: Schema<'PublicQuestion'>[] = [
  {
    questionId: demoId(601),
    questionType: 'SINGLE_CHOICE',
    content: 'Which sentence is the most polite way to ask for the menu?',
    points: 1,
    options: [
      { optionId: demoId(701), text: 'Give me the menu.' },
      { optionId: demoId(702), text: 'Could I have the menu, please?' },
      { optionId: demoId(703), text: 'I want menu now.' },
      { optionId: demoId(704), text: 'Menu here.' },
    ],
  },
  {
    questionId: demoId(602),
    questionType: 'TRUE_FALSE',
    content: '“Reservation” means the bill you receive after a meal.',
    points: 1,
  },
];
export const assessments: Schema<'Assessment'>[] = [
  ...lessons.map((lesson, i): Schema<'Assessment'> => ({
    assessmentId: demoId(501 + i),
    type: 'EXERCISE',
    courseId: lesson.courseId,
    lessonId: lesson.lessonId,
    title: `Bài tập — ${lesson.title}`,
    timeLimitSeconds: null,
    ready: true,
    questionCount: questions.length,
  })),
  {
    assessmentId: demoId(510),
    type: 'QUIZ',
    courseId: demoId(1),
    lessonId: null,
    title: 'Quiz — Daily Communication',
    timeLimitSeconds: 1200,
    ready: true,
    questionCount: questions.length,
  },
  {
    assessmentId: demoId(511),
    type: 'TEST',
    courseId: demoId(1),
    lessonId: null,
    title: 'Test — Tiếng Anh B1',
    timeLimitSeconds: 1800,
    ready: true,
    questionCount: questions.length,
  },
];

export interface DemoStore {
  users: Record<string, { profile: Schema<'Profile'>; passwordHash: string }>;
  currentUserId: string | null;
  csrfToken: string;
  setups: Record<string, Schema<'Setup'>>;
  saved: Record<string, Schema<'SavedVocabulary'>[]>;
  attempts: Record<string, { userId: string; data: Schema<'Attempt'> }>;
  results: Record<string, { userId: string; data: Schema<'Result'> }>;
  idempotency: Record<string, string>;
  history: Record<string, Schema<'HistoryEntry'>[]>;
  completed: Record<string, string[]>;
}

export async function hashPassword(value: string) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
  return Array.from(new Uint8Array(digest), (x) => x.toString(16).padStart(2, '0')).join('');
}

export async function createDemoStore(): Promise<DemoStore> {
  return {
    users: {
      [demoId(900)]: {
        profile: {
          userId: demoId(900),
          fullName: 'Hưng',
          email: DEMO_EMAIL,
          role: 'LEARNER',
          status: 'ACTIVE',
        },
        passwordHash: await hashPassword(DEMO_PASSWORD),
      },
    },
    currentUserId: null,
    csrfToken: crypto.randomUUID(),
    setups: {
      [demoId(900)]: {
        courseId: demoId(1),
        levelId: demoId(11),
        updatedAt: new Date().toISOString(),
      },
    },
    saved: {},
    attempts: {},
    results: {},
    idempotency: {},
    history: {},
    completed: {},
  };
}
