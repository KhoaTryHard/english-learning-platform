import { AnswerQueue, remainingSeconds } from './answerQueue';
import type { Schema } from '../../shared/api/client';

it('serializes full answer-set writes and freezes each queued snapshot', async () => {
  const pending: ((value: Schema<'Attempt'>) => void)[] = [];
  const calls: Schema<'Answer'>[][] = [];
  const queue = new AnswerQueue((answers) => {
    calls.push(answers);
    return new Promise((resolve) => pending.push(resolve));
  });
  const first: Schema<'Answer'>[] = [
    { questionId: 'q1', questionType: 'TRUE_FALSE', value: false },
  ];
  const p1 = queue.enqueue(first);
  first[0] = { questionId: 'q1', questionType: 'TRUE_FALSE', value: true };
  const p2 = queue.enqueue([
    ...first,
    { questionId: 'q2', questionType: 'TRUE_FALSE', value: false },
  ]);
  await vi.waitFor(() => expect(calls).toHaveLength(1));
  expect(calls[0][0]).toHaveProperty('value', false);
  pending[0]({} as Schema<'Attempt'>);
  await p1;
  await vi.waitFor(() => expect(calls).toHaveLength(2));
  expect(calls[1]).toHaveLength(2);
  pending[1]({} as Schema<'Attempt'>);
  await p2;
  await queue.flush();
});

it('allows a later explicit full replacement after a failed write', async () => {
  const save = vi
    .fn()
    .mockRejectedValueOnce(new Error('network'))
    .mockResolvedValueOnce({ status: 'IN_PROGRESS' });
  const queue = new AnswerQueue(save);
  await expect(queue.enqueue([])).rejects.toThrow('network');
  await expect(queue.enqueue([])).resolves.toMatchObject({ status: 'IN_PROGRESS' });
});

it('uses server time plus monotonic elapsed time, with no Exercise timer', () => {
  expect(remainingSeconds(null, '2026-10-27T00:00:00Z', 500000)).toBeNull();
  expect(remainingSeconds('2026-10-27T00:01:00Z', '2026-10-27T00:00:00Z', 10000)).toBe(50);
  expect(remainingSeconds('2026-10-27T00:01:00Z', '2026-10-27T00:00:00Z', 70000)).toBe(0);
});
