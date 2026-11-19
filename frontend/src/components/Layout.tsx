import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

const navLinkClass = ({ isActive }: { isActive: boolean }) => (isActive ? 'nav-link active' : 'nav-link');

export function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="app">
      <header className="app-header">
        <span className="brand">Trading Simulator</span>
        <nav className="app-nav">
          <NavLink to="/" end className={navLinkClass}>
            Pulpit
          </NavLink>
        </nav>
        <div className="app-user">
          <span className="muted">{user?.email}</span>
          <button className="btn" type="button" onClick={handleLogout}>
            Wyloguj
          </button>
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
