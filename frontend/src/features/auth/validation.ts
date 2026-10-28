export function passwordError(password: string): string | undefined {
  if ([...password].length < 15) return 'Mật khẩu cần ít nhất 15 ký tự Unicode.';
  if (new TextEncoder().encode(password).length > 72)
    return 'Mật khẩu không được vượt quá 72 byte UTF-8.';
}

export function validateRegistration(
  email: string,
  password: string,
  confirmation: string,
  fullName: string,
): string | undefined {
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) || email.trim().length > 254)
    return 'Vui lòng nhập email hợp lệ, tối đa 254 ký tự.';
  if ([...fullName.trim()].length > 100) return 'Họ tên không được vượt quá 100 ký tự.';
  return (
    passwordError(password) ||
    (password !== confirmation ? 'Mật khẩu xác nhận chưa khớp.' : undefined)
  );
}
