import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { fieldErrors, toProblem, type ProblemDetail } from '../api/problem';
import { useAuth } from '../auth/useAuth';
import { validateRegister, type FieldErrors } from '../auth/validation';
import { ErrorAlert } from '../components/ErrorAlert';
import { FormField } from '../components/FormField';

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [problem, setProblem] = useState<ProblemDetail | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const clientErrors = validateRegister(email, password);
    setErrors(clientErrors);
    setProblem(null);
    if (Object.keys(clientErrors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      // registration logs the new user in right away
      await register(email.trim(), password);
      navigate('/', { replace: true });
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
        <h1>Rejestracja</h1>
        <form className="form" onSubmit={handleSubmit} noValidate>
          <ErrorAlert problem={problem} />
          <FormField
            label="E-mail"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={errors.email}
          />
          <FormField
            label="Hasło"
            type="password"
            autoComplete="new-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={errors.password}
          />
          <p className="muted hint">8–128 znaków, co najmniej jedna litera i jedna cyfra.</p>
          <button className="btn btn-primary" type="submit" disabled={submitting}>
            {submitting ? 'Tworzenie konta…' : 'Utwórz konto'}
          </button>
        </form>
        <p className="muted">
          Masz już konto? <Link to="/login">Zaloguj się</Link>
        </p>
      </div>
    </main>
  );
}
