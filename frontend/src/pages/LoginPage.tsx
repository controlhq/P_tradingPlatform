import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { fieldErrors, toProblem, type ProblemDetail } from '../api/problem';
import { useAuth } from '../auth/useAuth';
import { validateLogin, type FieldErrors } from '../auth/validation';
import { ErrorAlert } from '../components/ErrorAlert';
import { FormField } from '../components/FormField';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  // where the user wanted to go before being sent to the login page
  const from = (location.state as { from?: string } | null)?.from ?? '/';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const clientErrors = validateLogin(email, password);
    setErrors(clientErrors);
    setProblem(null);
    if (Object.keys(clientErrors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (error) {
      const serverProblem = toProblem(error);
      setProblem(serverProblem);
      setErrors(fieldErrors(serverProblem));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="centered auth-page">
      <div className="card auth-card">
        <h1>Logowanie</h1>
        <form className="form" onSubmit={handleSubmit} noValidate>
          <ErrorAlert problem={problem} />
          <FormField
            label="E-mail"
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={errors.email}
          />
          <FormField
            label="Hasło"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={errors.password}
          />
          <button className="btn btn-primary" type="submit" disabled={submitting}>
            {submitting ? 'Logowanie…' : 'Zaloguj się'}
          </button>
        </form>
        <p className="muted">
          Nie masz konta? <Link to="/register">Zarejestruj się</Link>
        </p>
      </div>
    </main>
  );
}
