import type { ProblemDetail } from '../api/problem';

/** Shows the title and detail of a ProblemDetail; renders nothing when there is no error. */
export function ErrorAlert({ problem }: { problem: ProblemDetail | null }) {
  if (!problem) {
    return null;
  }
  return (
    <div className="alert-error" role="alert">
      <strong>{problem.title ?? 'Błąd'}</strong>
      {problem.detail && <span>{problem.detail}</span>}
    </div>
  );
}
