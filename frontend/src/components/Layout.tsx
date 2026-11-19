import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { MarketStreamProvider } from '../stream/MarketStreamContext';
import { StreamIndicator } from './StreamIndicator';

const navLinkClass = ({ isActive }: { isActive: boolean }) => (isActive ? 'nav-link active' : 'nav-link');

/** Shell of the logged-in part; it also owns the live stream, which closes on logout. */
export function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <MarketStreamProvider>
      <div className="app">
        <header className="app-header">
          <span className="brand">Trading Simulator</span>
          <nav className="app-nav">
            <NavLink to="/" end className={navLinkClass}>
              Pulpit
            </NavLink>
          </nav>
          <div className="app-user">
            <StreamIndicator />
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
    </MarketStreamProvider>
  );
}
