import React, { useEffect, useState } from 'react';
import { Briefcase, Cpu, LogOut, ShieldCheck, UserCheck, Users } from 'lucide-react';
import { AuthProvider, useAuth } from './context/AuthContext';
import { AdminDashboard } from './pages/AdminDashboard';
import { CandidatePortal } from './pages/CandidatePortal';
import { EmployerPortal } from './pages/EmployerPortal';
import { LoginPage } from './pages/LoginPage';
import { RecruiterDashboard } from './pages/RecruiterDashboard';

type ActiveView = 'candidate' | 'employer' | 'recruiter' | 'admin';

const DashboardShell: React.FC = () => {
  const { user, loading, logout } = useAuth();
  const [view, setView] = useState<ActiveView>('candidate');

  useEffect(() => {
    if (!user) return;
    if (user.role === 'ADMIN') setView('admin');
    else if (user.role === 'RECRUITER') setView('recruiter');
    else if (user.role === 'EMPLOYER') setView('employer');
    else setView('candidate');
  }, [user]);

  if (loading) {
    return (
      <div className="auth-page">
        <p style={{ color: 'var(--text-muted)' }}>Loading Job Management System...</p>
      </div>
    );
  }

  if (!user) {
    return <LoginPage />;
  }

  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="brand">
          <div className="brand-icon">
            <Cpu size={19} />
          </div>
          <div>
            <h1>JMS Platform</h1>
            <span>Java ATS + Worker Pool</span>
          </div>
        </div>

        <nav className="nav-list">
          {user.role === 'CANDIDATE' && (
            <button className={`nav-item ${view === 'candidate' ? 'active' : ''}`} onClick={() => setView('candidate')}>
              <UserCheck size={17} /> Candidate Portal
            </button>
          )}

          {(user.role === 'EMPLOYER' || user.role === 'ADMIN') && (
            <button className={`nav-item ${view === 'employer' ? 'active' : ''}`} onClick={() => setView('employer')}>
              <Briefcase size={17} /> Employer Portal
            </button>
          )}

          {(user.role === 'RECRUITER' || user.role === 'ADMIN') && (
            <button className={`nav-item ${view === 'recruiter' ? 'active' : ''}`} onClick={() => setView('recruiter')}>
              <Users size={17} /> Recruiter Dashboard
            </button>
          )}

          {(user.role === 'ADMIN' || user.role === 'RECRUITER') && (
            <button className={`nav-item ${view === 'admin' ? 'active' : ''}`} onClick={() => setView('admin')}>
              <ShieldCheck size={17} /> Admin & Queue Ops
            </button>
          )}
        </nav>

        <div style={{ borderTop: '1px solid var(--border)', paddingTop: '1rem' }}>
          <div style={{ fontSize: '0.86rem', fontWeight: 600 }}>{user.fullName}</div>
          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)', marginBottom: '0.5rem' }}>
            {user.email} · <span className="badge badge-blue">{user.role}</span>
          </div>
          <button className="btn btn-secondary btn-sm" style={{ width: '100%', justifyContent: 'center' }} onClick={logout}>
            <LogOut size={14} /> Sign Out
          </button>
        </div>
      </aside>

      <main className="main-content">
        {view === 'candidate' && <CandidatePortal />}
        {view === 'employer' && <EmployerPortal />}
        {view === 'recruiter' && <RecruiterDashboard />}
        {view === 'admin' && <AdminDashboard />}
      </main>
    </div>
  );
};

export const App: React.FC = () => (
  <AuthProvider>
    <DashboardShell />
  </AuthProvider>
);

export default App;
