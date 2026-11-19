import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { MarketStreamProvider } from '../stream/MarketStreamContext';
import { StreamIndicator } from './StreamIndicator';
import { OrderToasts } from './Toasts';

const navLinkClass = ({ isActive }: { isActive: boolean }) => (isActive ? 'nav-link active' : 'nav-link');

/** Shell of the logged-in part; it also owns the live stream, which closes on logout. */
export function Layout() {
  const { user, role, logout } = useAuth();
  const navigate = useNavigate();
  // an instrument page belongs to the markets section of the navigation
  const onInstrumentPage = useLocation().pathname.startsWith('/instruments/');

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
            <NavLink to="/markets" className={({ isActive }) => navLinkClass({ isActive: isActive || onInstrumentPage })}>
              Rynki
            </NavLink>
            <NavLink to="/portfolio" className={navLinkClass}>
              Portfel
            </NavLink>
            <NavLink to="/orders" className={navLinkClass}>
              Zlecenia
            </NavLink>
            <NavLink to="/transactions" className={navLinkClass}>
              Transakcje
            </NavLink>
            {role === 'ADMIN' && (
              <NavLink to="/admin" className={navLinkClass}>
                Admin
              </NavLink>
            )}
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
        <OrderToasts />
      </div>
    </MarketStreamProvider>
  );
}
