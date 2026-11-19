import { lazy, Suspense } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import { Layout } from './components/Layout';
import { Loading } from './components/Loading';
import { LoginPage } from './pages/LoginPage';
import { MarketsPage } from './pages/MarketsPage';
import { PortfolioPage } from './pages/PortfolioPage';
import { RegisterPage } from './pages/RegisterPage';
import { GuestOnly, RequireAuth } from './routes/RequireAuth';

// the chart library is large, so it is loaded only when an instrument is opened
const InstrumentPage = lazy(() => import('./pages/InstrumentPage').then((m) => ({ default: m.InstrumentPage })));

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<GuestOnly />}>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
          </Route>
          <Route element={<RequireAuth />}>
            <Route element={<Layout />}>
              <Route index element={<Navigate to="/markets" replace />} />
              <Route path="/markets" element={<MarketsPage />} />
              <Route path="/portfolio" element={<PortfolioPage />} />
              <Route
                path="/instruments/:id"
                element={
                  <Suspense fallback={<Loading />}>
                    <InstrumentPage />
                  </Suspense>
                }
              />
            </Route>
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
