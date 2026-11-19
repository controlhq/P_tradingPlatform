import { isAxiosError } from 'axios';

export interface FieldViolation {
  field: string;
  message: string;
}

/** RFC 9457 body returned by the backend for every error (see GlobalExceptionHandler). */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: FieldViolation[];
}

function isProblemDetail(data: unknown): data is ProblemDetail {
  return typeof data === 'object' && data !== null && ('detail' in data || 'title' in data);
}

/** Turns anything thrown by an API call into a ProblemDetail the UI can show. */
export function toProblem(error: unknown): ProblemDetail {
  if (isAxiosError(error)) {
    const data: unknown = error.response?.data;
    if (isProblemDetail(data)) {
      return data;
    }
    if (error.response) {
      return {
        status: error.response.status,
        title: 'Błąd serwera',
        detail: `Nieoczekiwana odpowiedź serwera (HTTP ${error.response.status}).`,
      };
    }
    return { title: 'Brak połączenia', detail: 'Nie można połączyć się z serwerem.' };
  }
  return { title: 'Błąd', detail: error instanceof Error ? error.message : 'Nieoczekiwany błąd.' };
}

/** First message per field from a 400 response, keyed by field name. */
export function fieldErrors(problem: ProblemDetail | null): Record<string, string> {
  const result: Record<string, string> = {};
  for (const { field, message } of problem?.errors ?? []) {
    result[field] ??= message;
  }
  return result;
}
