import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { Loading } from '../components/Loading';

/** Lets only logged-in users through; others go to /login and come back here afterwards. */
export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <Loading />;
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }
  return <Outlet />;
}

/**
 * Login and registration make no sense for a logged-in user. After a successful login this also
 * performs the redirect, so it honours the page the user originally asked for.
 */
export function GuestOnly() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <Loading />;
  }
  if (status === 'authenticated') {
    const from = (location.state as { from?: string } | null)?.from ?? '/';
    return <Navigate to={from} replace />;
  }
  return <Outlet />;
}
