// Same rules as the backend DTOs, so most mistakes are caught before a request is sent.
// The server still validates; its field errors are shown the same way.

export type FieldErrors = Partial<Record<'email' | 'password', string>>;

const EMAIL_MAX = 254;
const PASSWORD_MIN = 8;
const PASSWORD_MAX = 128;
// RegisterRequest: the domain must contain a dot (DB check ck_users_email_format)
const EMAIL_PATTERN = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
// RegisterRequest: at least one letter (any alphabet) and one digit (BR13)
const PASSWORD_PATTERN = /^(?=.*\p{L})(?=.*\d)/su;

/** LoginRequest: both fields required, length limits only. */
export function validateLogin(email: string, password: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!email.trim()) {
    errors.email = 'Podaj adres e-mail.';
  } else if (email.length > EMAIL_MAX) {
    errors.email = `Najwyżej ${EMAIL_MAX} znaki.`;
  }
  if (!password.trim()) {
    errors.password = 'Podaj hasło.';
  } else if (password.length > PASSWORD_MAX) {
    errors.password = `Najwyżej ${PASSWORD_MAX} znaków.`;
  }
  return errors;
}

/** RegisterRequest: valid e-mail, password 8-128 characters with a letter and a digit. */
export function validateRegister(email: string, password: string): FieldErrors {
  const errors: FieldErrors = {};
  const trimmed = email.trim();
  if (!trimmed) {
    errors.email = 'Podaj adres e-mail.';
  } else if (trimmed.length > EMAIL_MAX) {
    errors.email = `Najwyżej ${EMAIL_MAX} znaki.`;
  } else if (!EMAIL_PATTERN.test(trimmed)) {
    errors.email = 'Nieprawidłowy adres e-mail.';
  }
  if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) {
    errors.password = `Hasło musi mieć od ${PASSWORD_MIN} do ${PASSWORD_MAX} znaków.`;
  } else if (!PASSWORD_PATTERN.test(password)) {
    errors.password = 'Hasło musi zawierać co najmniej jedną literę i jedną cyfrę.';
  }
  return errors;
}
