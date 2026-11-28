import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { demoStore, server } from '../../../tests/setup';
import { courses, demoId, lessons } from '../../mocks/data';
import { AdminAssessmentsPage } from './AssessmentsPage';
import type { Schema } from '../../shared/api/client';

// public v0.1 QuestionWrite.points accepts every finite number > 0.
// Copy into src/features/admin/ for execution on the reviewed PR47 snapshot.
it('can edit a server question with contract-valid points below 0.01 without changing its score', async () => {
  demoStore.currentUserId = demoId(901);
  const question: Schema<'TrueFalseQuestion'> = {
    questionId: demoId(860), questionType: 'TRUE_FALSE', content: 'Fractional question',
    points: 0.001, correctAnswer: false,
  };
  const detail: Schema<'AssessmentDetail'> = {
    assessmentId: demoId(850), type: 'EXERCISE', courseId: courses[0].courseId,
    lessonId: lessons[0].lessonId, title: 'Fractional points fixture',
    timeLimitSeconds: null, ready: false, questionCount: 1, questions: [question],
  };
  let updated: Schema<'TrueFalseWrite'> | undefined;
  server.use(
    http.get('*/api/v1/admin/assessments', () => HttpResponse.json({
      items: [detail], page: 0, size: 100, totalElements: 1, totalPages: 1,
    })),
    http.get('*/api/v1/admin/assessments/:id', () => HttpResponse.json(detail)),
    http.put('*/api/v1/admin/assessments/:id/questions/:qid', async ({ request }) => {
      updated = await request.json() as Schema<'TrueFalseWrite'>;
      return HttpResponse.json({ ...updated, questionId: question.questionId });
    }),
  );
  const user = userEvent.setup();
  render(<AdminAssessmentsPage />);
  await user.click(await screen.findByRole('button', { name: 'Xem / sửa Fractional points fixture' }));
  await user.click(await screen.findByRole('button', { name: 'Sửa câu hỏi Fractional question' }));
  expect(screen.getByLabelText('Điểm')).toHaveValue(0.001);
  await user.clear(screen.getByLabelText('Nội dung câu hỏi'));
  await user.type(screen.getByLabelText('Nội dung câu hỏi'), 'Updated fractional question');
  await user.click(screen.getByRole('button', { name: 'Lưu câu hỏi' }));
  await waitFor(() => expect(updated).toEqual({
    questionType: 'TRUE_FALSE', content: 'Updated fractional question', points: 0.001,
    correctAnswer: false,
  }));
});
