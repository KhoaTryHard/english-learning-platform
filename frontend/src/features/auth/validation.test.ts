import { passwordError, validateRegistration } from './validation';

describe('DD-01 password constraints', () => {
  it('counts Unicode code points and UTF-8 bytes separately', () => {
    expect(passwordError('a'.repeat(14))).toContain('15');
    expect(passwordError('a'.repeat(15))).toBeUndefined();
    expect(passwordError('😀'.repeat(14))).toContain('15');
    expect(passwordError('😀'.repeat(18))).toBeUndefined();
    expect(passwordError('😀'.repeat(19))).toContain('72 byte');
    expect(passwordError('a'.repeat(73))).toContain('72 byte');
  });
  it('preserves whitespace in passwords and accepts an optional name', () => {
    expect(
      validateRegistration(' learner@example.com ', ' '.repeat(15), ' '.repeat(15), ''),
    ).toBeUndefined();
    expect(validateRegistration('a@example.com', 'LearningDemo2026!', 'different', '')).toContain(
      'khớp',
    );
    expect(
      validateRegistration('not-email', 'LearningDemo2026!', 'LearningDemo2026!', ''),
    ).toContain('email');
  });
});
