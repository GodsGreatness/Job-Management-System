import React, { useEffect, useState } from 'react';
import { Calendar, CheckSquare, Filter } from 'lucide-react';
import { api } from '../api/client';
import { StatusBadge } from '../components/StatusBadge';
import type { Application, ApplicationStatus, Interview, SystemAnalytics } from '../types';

export const RecruiterDashboard: React.FC = () => {
  const [applications, setApplications] = useState<Application[]>([]);
  const [interviews, setInterviews] = useState<Interview[]>([]);
  const [analytics, setAnalytics] = useState<SystemAnalytics | null>(null);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [scheduleTarget, setScheduleTarget] = useState<Application | null>(null);
  const [stage, setStage] = useState('Technical Architecture Round');
  const [durationMinutes, setDurationMinutes] = useState(60);
  const [scheduledDate, setScheduledDate] = useState(() => {
    const d = new Date(Date.now() + 86400 * 1000 * 2);
    return d.toISOString().slice(0, 16);
  });
  const [feedback, setFeedback] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    const [a, i, s] = await Promise.all([
      api.listApplications(),
      api.listInterviews(),
      api.getAnalytics(true),
    ]);
    setApplications(a);
    setInterviews(i);
    setAnalytics(s);
  };

  useEffect(() => {
    loadData().catch((e) => setError(e.message));
  }, []);

  const filteredApps =
    statusFilter === 'ALL' ? applications : applications.filter((a) => a.status === statusFilter);

  const handleSchedule = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!scheduleTarget) return;
    setError(null);
    try {
      await api.scheduleInterview({
        applicationId: scheduleTarget.id,
        scheduledAt: new Date(scheduledDate).toISOString(),
        durationMinutes,
        stage,
      });
      setFeedback(`Scheduled "${stage}" for ${scheduleTarget.candidateName}. Email & notification workers triggered.`);
      setScheduleTarget(null);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Scheduling failed');
    }
  };

  const updateStage = async (appId: number, status: ApplicationStatus) => {
    try {
      await api.updateApplicationStatus(appId, status);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Status update failed');
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Recruiter Dashboard</h2>
          <p>Cross-organization ATS pipeline triage, interview scheduling, and hiring conversion analytics.</p>
        </div>
        <button className="btn btn-secondary" onClick={() => loadData()}>
          Sync Pipeline
        </button>
      </div>

      {error && <div className="alert-error">{error}</div>}
      {feedback && <div className="alert-success">{feedback}</div>}

      {analytics && (
        <div className="grid-4">
          <div className="card">
            <div className="stat-label">Total Applications</div>
            <div className="stat-value">{analytics.totalApplications}</div>
          </div>
          <div className="card">
            <div className="stat-label">Average ATS Score</div>
            <div className="stat-value">{analytics.averageAtsScore}%</div>
          </div>
          <div className="card">
            <div className="stat-label">Scheduled Interviews</div>
            <div className="stat-value">{analytics.scheduledInterviews}</div>
          </div>
          <div className="card">
            <div className="stat-label">Open Requisitions</div>
            <div className="stat-value">{analytics.openJobs}</div>
          </div>
        </div>
      )}

      {/* Candidate Pipeline Table */}
      <div className="card" style={{ marginBottom: '1.5rem' }}>
        <div className="card-title">
          <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Filter size={17} /> Global Applicant Tracking Pipeline
          </span>
          <select
            style={{ width: 180, padding: '0.4rem 0.6rem', fontSize: '0.84rem' }}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="ALL">All Stages ({applications.length})</option>
            <option value="SUBMITTED">SUBMITTED</option>
            <option value="SCREENING">SCREENING</option>
            <option value="INTERVIEW">INTERVIEW</option>
            <option value="OFFER">OFFER</option>
            <option value="HIRED">HIRED</option>
            <option value="REJECTED">REJECTED</option>
          </select>
        </div>

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Candidate</th>
                <th>Role / Company</th>
                <th>ATS Score</th>
                <th>Stage</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {filteredApps.map((app) => (
                <tr key={app.id}>
                  <td>
                    <strong>{app.candidateName}</strong>
                    <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>{app.candidateEmail}</div>
                  </td>
                  <td>
                    {app.jobTitle}
                    <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>{app.company}</div>
                  </td>
                  <td>
                    <strong>{app.atsMatchScore != null ? `${app.atsMatchScore}%` : 'Pending'}</strong>
                  </td>
                  <td><StatusBadge status={app.status} /></td>
                  <td>
                    <div style={{ display: 'flex', gap: '0.4rem', flexWrap: 'wrap' }}>
                      <button className="btn btn-primary btn-sm" onClick={() => setScheduleTarget(app)}>
                        <Calendar size={13} /> Schedule
                      </button>
                      <select
                        style={{ width: 135, padding: '0.3rem 0.45rem', fontSize: '0.78rem' }}
                        value={app.status}
                        onChange={(e) => updateStage(app.id, e.target.value as ApplicationStatus)}
                      >
                        <option value="SUBMITTED">SUBMITTED</option>
                        <option value="SCREENING">SCREENING</option>
                        <option value="INTERVIEW">INTERVIEW</option>
                        <option value="OFFER">OFFER</option>
                        <option value="HIRED">HIRED</option>
                        <option value="REJECTED">REJECTED</option>
                      </select>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* Scheduled Interviews */}
      <div className="card">
        <div className="card-title">
          <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <CheckSquare size={17} /> Scheduled Interviews
          </span>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Candidate</th>
                <th>Position</th>
                <th>Stage</th>
                <th>Scheduled Time</th>
                <th>Status</th>
                <th>Complete</th>
              </tr>
            </thead>
            <tbody>
              {interviews.map((iv) => (
                <tr key={iv.id}>
                  <td><strong>{iv.candidateName}</strong></td>
                  <td>{iv.jobTitle}</td>
                  <td>{iv.stage}</td>
                  <td>{new Date(iv.scheduledAt).toLocaleString()}</td>
                  <td><StatusBadge status={iv.status} /></td>
                  <td>
                    {iv.status === 'SCHEDULED' && (
                      <button
                        className="btn btn-success btn-sm"
                        onClick={async () => {
                          await api.updateInterview(iv.id, { status: 'COMPLETED', rating: 5, feedback: 'Strong engineering fundamentals' });
                          await loadData();
                        }}
                      >
                        Mark Completed
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {scheduleTarget && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <h3 style={{ marginBottom: '0.3rem' }}>Schedule Interview</h3>
            <p style={{ fontSize: '0.84rem', color: 'var(--text-muted)', marginBottom: '1rem' }}>
              {scheduleTarget.candidateName} — {scheduleTarget.jobTitle}
            </p>
            <form onSubmit={handleSchedule}>
              <div className="form-group">
                <label>Interview Stage</label>
                <input required value={stage} onChange={(e) => setStage(e.target.value)} />
              </div>
              <div className="form-group">
                <label>Date & Time</label>
                <input type="datetime-local" required value={scheduledDate} onChange={(e) => setScheduledDate(e.target.value)} />
              </div>
              <div className="form-group">
                <label>Duration (minutes)</label>
                <input type="number" min={15} max={240} value={durationMinutes} onChange={(e) => setDurationMinutes(Number(e.target.value))} />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setScheduleTarget(null)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Confirm Schedule</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
