import type { Schema } from '../../shared/api/client';

// Full answer-set replacement must arrive in order; a slow older write cannot erase a newer answer.
export class AnswerQueue {
  private tail: Promise<unknown> = Promise.resolve();
  constructor(private save: (answers: Schema<'Answer'>[]) => Promise<Schema<'Attempt'>>) {}
  enqueue(answers: Schema<'Answer'>[]): Promise<Schema<'Attempt'>> {
    const snapshot = structuredClone(answers);
    const task = this.tail.catch(() => undefined).then(() => this.save(snapshot));
    this.tail = task;
    return task;
  }
  async flush() {
    await this.tail;
  }
}

export function remainingSeconds(deadlineAt: string | null, serverNow: string, elapsedMs: number) {
  if (deadlineAt === null) return null;
  return Math.max(
    0,
    Math.ceil((Date.parse(deadlineAt) - Date.parse(serverNow) - elapsedMs) / 1000),
  );
}

export function formatTime(seconds: number) {
  return `${Math.floor(seconds / 60)
    .toString()
    .padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}`;
}
