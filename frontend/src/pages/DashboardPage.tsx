import { useAuth } from '../auth/useAuth';

export function DashboardPage() {
  const { user } = useAuth();

  return (
    <section>
      <h1>Pulpit</h1>
      <p className="muted">
        Zalogowano jako <strong>{user?.email}</strong>
        {user?.role === 'ADMIN' && ' (administrator)'}.
      </p>
    </section>
  );
}
