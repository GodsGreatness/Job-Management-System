import React, { useEffect, useState } from 'react';
import { Bell, Briefcase, CheckCircle2, MapPin, Search, Send } from 'lucide-react';
import { api } from '../api/client';
import { formatSalary, StatusBadge } from '../components/StatusBadge';
import type { Application, Interview, JobPosting, NotificationItem } from '../types';

export const CandidatePortal: React.FC = () => {
  const [jobs, setJobs] = useState<JobPosting[]>([]);
  const [applications, setApplications] = useState<Application[]>([]);
  const [interviews, setInterviews] = useState<Interview[]>([]);
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [keyword, setKeyword] = useState('');
  const [remoteOnly, setRemoteOnly] = useState(false);
  const [applyingJob, setApplyingJob] = useState<JobPosting | null>(null);
  const [resumeText, setResumeText] = useState(
    'Computer Engineering student skilled in Java, Spring Boot, PostgreSQL, Redis, Docker, C++, React, TypeScript, and concurrent backend systems.'
  );
  const [coverLetter, setCoverLetter] = useState('');
  const [feedback, setFeedback] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    const [j, a, i, n] = await Promise.all([
      api.searchJobs({ status: 'OPEN', remote: remoteOnly ? true : undefined, keyword: keyword || undefined }),
      api.listApplications(),
      api.listInterviews(),
      api.listNotifications(),
    ]);
    setJobs(j);
    setApplications(a);
    setInterviews(i);
    setNotifications(n);
  };

  useEffect(() => {
    loadData().catch((e) => setError(e.message));
  }, [remoteOnly]);

  const appliedJobIds = new Set(applications.map((a) => a.jobId));

  const submitApplication = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!applyingJob) return;
    setError(null);
    try {
      await api.apply({ jobId: applyingJob.id, resumeText, coverLetter });
      setFeedback(`Applied to "${applyingJob.title}". ResumeProcessingWorker is computing your ATS match score in the background!`);
      setApplyingJob(null);
      setCoverLetter('');
      await loadData();
      // Poll briefly to pick up async ATS score computed by worker pool
      setTimeout(() => loadData().catch(() => {}), 1500);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to apply');
    }
  };

  const withdraw = async (appId: number) => {
    try {
      await api.updateApplicationStatus(appId, 'WITHDRAWN');
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to withdraw');
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Candidate Portal</h2>
          <p>Discover open engineering roles, apply with async ATS skill scoring, and track your interview pipeline.</p>
        </div>
        <button className="btn btn-secondary" onClick={() => loadData()}>
          Refresh Status
        </button>
      </div>

      {error && <div className="alert-error">{error}</div>}
      {feedback && <div className="alert-success">{feedback}</div>}

      <div className="grid-4">
        <div className="card">
          <div className="stat-label">Open Positions</div>
          <div className="stat-value">{jobs.length}</div>
        </div>
        <div className="card">
          <div className="stat-label">My Applications</div>
          <div className="stat-value">{applications.length}</div>
        </div>
        <div className="card">
          <div className="stat-label">Scheduled Interviews</div>
          <div className="stat-value">{interviews.filter((i) => i.status === 'SCHEDULED').length}</div>
        </div>
        <div className="card">
          <div className="stat-label">Unread Alerts</div>
          <div className="stat-value">{notifications.filter((n) => !n.read).length}</div>
        </div>
      </div>

      {/* Job Board Search */}
      <div className="card" style={{ marginBottom: '1.5rem' }}>
        <div className="card-title">
          <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Briefcase size={18} /> Open Job Board
          </span>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
            <input
              style={{ width: 260 }}
              placeholder="Search title, company, skills..."
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
            />
            <button className="btn btn-primary btn-sm" onClick={() => loadData()}>
              <Search size={14} /> Search
            </button>
            <label style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
              <input type="checkbox" style={{ width: 'auto' }} checked={remoteOnly} onChange={(e) => setRemoteOnly(e.target.checked)} />
              Remote Only
            </label>
          </div>
        </div>

        <div className="grid-2">
          {jobs.map((job) => {
            const alreadyApplied = appliedJobIds.has(job.id);
            return (
              <div key={job.id} className="card" style={{ background: 'var(--bg-elevated)' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '0.5rem' }}>
                  <div>
                    <h3 style={{ fontSize: '1rem', fontWeight: 700 }}>{job.title}</h3>
                    <div style={{ fontSize: '0.84rem', color: 'var(--text-muted)', marginTop: '0.15rem' }}>
                      {job.company} · <MapPin size={12} style={{ display: 'inline' }} /> {job.location} {job.remote ? '(Remote)' : ''}
                    </div>
                  </div>
                  <StatusBadge status={job.employmentType} />
                </div>
                <p style={{ fontSize: '0.85rem', color: 'var(--text-muted)', margin: '0.7rem 0' }}>{job.description}</p>
                <div className="chip-row">
                  {job.requiredSkills.map((s) => (
                    <span key={s} className="chip">{s}</span>
                  ))}
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '1rem' }}>
                  <span style={{ fontWeight: 600, fontSize: '0.88rem', color: '#86efac' }}>
                    {formatSalary(job.salaryMin, job.salaryMax)}
                  </span>
                  {alreadyApplied ? (
                    <span className="badge badge-green">
                      <CheckCircle2 size={12} style={{ marginRight: 4 }} /> Applied
                    </span>
                  ) : (
                    <button className="btn btn-primary btn-sm" onClick={() => setApplyingJob(job)}>
                      <Send size={13} /> Apply Now
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* My Applications + Notifications */}
      <div className="grid-2">
        <div className="card">
          <div className="card-title">My Applications & ATS Match Scores</div>
          {applications.length === 0 ? (
            <p style={{ color: 'var(--text-muted)', fontSize: '0.88rem' }}>No applications submitted yet.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.85rem' }}>
              {applications.map((app) => (
                <div key={app.id} className="card" style={{ background: 'var(--bg-elevated)', padding: '1rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <strong>{app.jobTitle}</strong>
                      <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>{app.company}</div>
                    </div>
                    <StatusBadge status={app.status} />
                  </div>

                  <div style={{ marginTop: '0.7rem' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.78rem', marginBottom: '0.25rem' }}>
                      <span style={{ color: 'var(--text-muted)' }}>Async ATS Match Score</span>
                      <strong>{app.atsMatchScore != null ? `${app.atsMatchScore}%` : 'Processing in worker pool...'}</strong>
                    </div>
                    <div className="progress-track">
                      <div
                        className="progress-fill"
                        style={{
                          width: `${app.atsMatchScore ?? 15}%`,
                          background: (app.atsMatchScore ?? 0) >= 75 ? 'var(--success)' : 'var(--primary)',
                        }}
                      />
                    </div>
                  </div>

                  {(app.matchedSkills.length > 0 || app.missingSkills.length > 0) && (
                    <div className="chip-row">
                      {app.matchedSkills.map((s) => (
                        <span key={`m-${s}`} className="chip chip-match">✓ {s}</span>
                      ))}
                      {app.missingSkills.map((s) => (
                        <span key={`x-${s}`} className="chip chip-missing">✕ {s}</span>
                      ))}
                    </div>
                  )}

                  {app.status !== 'WITHDRAWN' && app.status !== 'HIRED' && (
                    <div style={{ marginTop: '0.75rem', textAlign: 'right' }}>
                      <button className="btn btn-danger btn-sm" onClick={() => withdraw(app.id)}>
                        Withdraw
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="card">
          <div className="card-title">
            <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Bell size={17} /> Notifications & Interviews
            </span>
            {notifications.some((n) => !n.read) && (
              <button
                className="btn btn-secondary btn-sm"
                onClick={async () => {
                  await api.markAllNotificationsRead();
                  await loadData();
                }}
              >
                Mark all read
              </button>
            )}
          </div>

          {interviews.length > 0 && (
            <div style={{ marginBottom: '1rem' }}>
              <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)', marginBottom: '0.4rem' }}>UPCOMING INTERVIEWS</div>
              {interviews.map((iv) => (
                <div key={iv.id} className="card" style={{ background: 'var(--bg-elevated)', padding: '0.75rem', marginBottom: '0.5rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <strong>{iv.jobTitle} — {iv.stage}</strong>
                    <StatusBadge status={iv.status} />
                  </div>
                  <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>
                    {new Date(iv.scheduledAt).toLocaleString()} ({iv.durationMinutes} min) · Host: {iv.interviewerName}
                  </div>
                </div>
              ))}
            </div>
          )}

          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.55rem' }}>
            {notifications.length === 0 ? (
              <p style={{ color: 'var(--text-muted)', fontSize: '0.86rem' }}>No notifications yet.</p>
            ) : (
              notifications.slice(0, 8).map((n) => (
                <div
                  key={n.id}
                  style={{
                    padding: '0.65rem 0.8rem',
                    borderRadius: 8,
                    background: n.read ? 'transparent' : 'rgba(79, 124, 255, 0.1)',
                    border: '1px solid var(--border)',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.84rem' }}>
                    <strong>{n.title}</strong>
                    <span style={{ fontSize: '0.72rem', color: 'var(--text-muted)' }}>{n.category}</span>
                  </div>
                  <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)', marginTop: '0.2rem' }}>{n.message}</p>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Apply Modal */}
      {applyingJob && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <h3 style={{ marginBottom: '0.3rem' }}>Apply to {applyingJob.title}</h3>
            <p style={{ fontSize: '0.84rem', color: 'var(--text-muted)', marginBottom: '1rem' }}>
              {applyingJob.company} · Required skills: {applyingJob.requiredSkills.join(', ')}
            </p>
            <form onSubmit={submitApplication}>
              <div className="form-group">
                <label>Resume Text / Skills Summary (parsed asynchronously by ResumeProcessingWorker)</label>
                <textarea rows={5} value={resumeText} onChange={(e) => setResumeText(e.target.value)} required />
              </div>
              <div className="form-group">
                <label>Cover Letter (optional)</label>
                <textarea rows={3} value={coverLetter} onChange={(e) => setCoverLetter(e.target.value)} placeholder="Why are you excited about this role?" />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setApplyingJob(null)}>
                  Cancel
                </button>
                <button type="submit" className="btn btn-primary">
                  Submit Application
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
