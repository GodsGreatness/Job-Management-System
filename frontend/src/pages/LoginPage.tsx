import React, { useState } from 'react';
import { Briefcase, Cpu, ShieldCheck, UserCheck, Users } from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import type { Role } from '../types';

const DEMO_ACCOUNTS: Array<{ label: string; email: string; role: Role; icon: React.ReactNode }> = [
  { label: 'Candidate', email: 'candidate@jms.dev', role: 'CANDIDATE', icon: <UserCheck size={15} /> },
  { label: 'Employer', email: 'employer@jms.dev', role: 'EMPLOYER', icon: <Briefcase size={15} /> },
  { label: 'Recruiter', email: 'recruiter@jms.dev', role: 'RECRUITER', icon: <Users size={15} /> },
  { label: 'Admin + Queue', email: 'admin@jms.dev', role: 'ADMIN', icon: <ShieldCheck size={15} /> },
];

export const LoginPage: React.FC = () => {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [email, setEmail] = useState('admin@jms.dev');
  const [password, setPassword] = useState('password123');
  const [fullName, setFullName] = useState('');
  const [role, setRole] = useState<Role>('CANDIDATE');
  const [companyName, setCompanyName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      if (mode === 'login') {
        await login(email, password);
      } else {
        await register({ email, password, fullName, role, companyName: companyName || undefined });
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Authentication failed');
    } finally {
      setBusy(false);
    }
  };

  const quickLogin = async (demoEmail: string) => {
    setError(null);
    setBusy(true);
    try {
      await login(demoEmail, 'password123');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Demo login failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-box">
        <div className="brand" style={{ padding: 0, marginBottom: '1.25rem' }}>
          <div className="brand-icon">
            <Cpu size={20} />
          </div>
          <div>
            <h1>Job Management System</h1>
            <span>Full-Stack ATS + Async Worker Queue Engine</span>
          </div>
        </div>

        <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '1.25rem' }}>
          <button
            type="button"
            className={`btn ${mode === 'login' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ flex: 1, justifyContent: 'center' }}
            onClick={() => setMode('login')}
          >
            Sign In
          </button>
          <button
            type="button"
            className={`btn ${mode === 'register' ? 'btn-primary' : 'btn-secondary'}`}
            style={{ flex: 1, justifyContent: 'center' }}
            onClick={() => setMode('register')}
          >
            Create Account
          </button>
        </div>

        {error && <div className="alert-error">{error}</div>}

        <form onSubmit={handleSubmit}>
          {mode === 'register' && (
            <>
              <div className="form-group">
                <label>Full Name</label>
                <input value={fullName} onChange={(e) => setFullName(e.target.value)} required placeholder="Alex Mensah" />
              </div>
              <div className="form-group">
                <label>Portal Role</label>
                <select value={role} onChange={(e) => setRole(e.target.value as Role)}>
                  <option value="CANDIDATE">Candidate</option>
                  <option value="EMPLOYER">Employer</option>
                  <option value="RECRUITER">Recruiter</option>
                </select>
              </div>
              {role !== 'CANDIDATE' && (
                <div className="form-group">
                  <label>Company / Organization</label>
                  <input value={companyName} onChange={(e) => setCompanyName(e.target.value)} placeholder="HyperScale Systems" />
                </div>
              )}
            </>
          )}

          <div className="form-group">
            <label>Email</label>
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          </div>

          <div className="form-group">
            <label>Password</label>
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={6} />
          </div>

          <button type="submit" className="btn btn-primary" disabled={busy} style={{ width: '100%', justifyContent: 'center', marginTop: '0.5rem' }}>
            {busy ? 'Authenticating...' : mode === 'login' ? 'Sign In' : 'Create Account'}
          </button>
        </form>

        <div style={{ marginTop: '1.5rem', paddingTop: '1.1rem', borderTop: '1px solid var(--border)' }}>
          <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginBottom: '0.65rem' }}>
            Instant Demo Portals (password: <code>password123</code>)
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.5rem' }}>
            {DEMO_ACCOUNTS.map((acc) => (
              <button
                key={acc.email}
                type="button"
                className="btn btn-secondary btn-sm"
                style={{ justifyContent: 'flex-start' }}
                disabled={busy}
                onClick={() => quickLogin(acc.email)}
              >
                {acc.icon} {acc.label}
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
