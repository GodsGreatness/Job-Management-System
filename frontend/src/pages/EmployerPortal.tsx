import React, { useEffect, useState } from 'react';
import { Briefcase, PlusCircle, Users } from 'lucide-react';
import { api } from '../api/client';
import { formatSalary, StatusBadge } from '../components/StatusBadge';
import { useAuth } from '../context/AuthContext';
import type { Application, ApplicationStatus, EmploymentType, JobPosting, JobStatus } from '../types';

export const EmployerPortal: React.FC = () => {
  const { user } = useAuth();
  const [jobs, setJobs] = useState<JobPosting[]>([]);
  const [applications, setApplications] = useState<Application[]>([]);
  const [showModal, setShowModal] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const [form, setForm] = useState({
    title: '',
    company: user?.companyName || 'HyperScale Systems',
    department: 'Engineering',
    location: 'Remote',
    remote: true,
    employmentType: 'FULL_TIME' as EmploymentType,
    salaryMin: 90000,
    salaryMax: 130000,
    requiredSkills: 'Java, Spring Boot, PostgreSQL, Docker',
    description: '',
  });

  const loadData = async () => {
    const [j, a] = await Promise.all([api.myJobs(), api.listApplications()]);
    setJobs(j);
    setApplications(a);
  };

  useEffect(() => {
    loadData().catch((e) => setError(e.message));
  }, []);

  const handleCreateJob = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.createJob(form);
      setMessage(`Published "${form.title}" and enqueued analytics refresh.`);
      setShowModal(false);
      setForm({ ...form, title: '', description: '' });
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to create job');
    }
  };

  const toggleJobStatus = async (job: JobPosting, nextStatus: JobStatus) => {
    try {
      await api.updateJob(job.id, { status: nextStatus });
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update job');
    }
  };

  const advanceApplication = async (appId: number, status: ApplicationStatus) => {
    try {
      await api.updateApplicationStatus(appId, status);
      setMessage(`Updated application #${appId} -> ${status} (async email + notification queued)`);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update application');
    }
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Employer Portal</h2>
          <p>Publish engineering requisitions, inspect ATS-scored candidates, and manage hiring workflows.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowModal(true)}>
          <PlusCircle size={16} /> Post New Job
        </button>
      </div>

      {error && <div className="alert-error">{error}</div>}
      {message && <div className="alert-success">{message}</div>}

      <div className="grid-4">
        <div className="card">
          <div className="stat-label">My Job Postings</div>
          <div className="stat-value">{jobs.length}</div>
        </div>
        <div className="card">
          <div className="stat-label">Active Open Roles</div>
          <div className="stat-value">{jobs.filter((j) => j.status === 'OPEN').length}</div>
        </div>
        <div className="card">
          <div className="stat-label">Incoming Applicants</div>
          <div className="stat-value">{applications.length}</div>
        </div>
        <div className="card">
          <div className="stat-label">High-Match (&ge;75%)</div>
          <div className="stat-value">{applications.filter((a) => (a.atsMatchScore ?? 0) >= 75).length}</div>
        </div>
      </div>

      <div className="card" style={{ marginBottom: '1.5rem' }}>
        <div className="card-title">
          <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Briefcase size={17} /> Organization Job Postings
          </span>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Role</th>
                <th>Location</th>
                <th>Compensation</th>
                <th>Applicants</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {jobs.map((job) => (
                <tr key={job.id}>
                  <td>
                    <strong>{job.title}</strong>
                    <div style={{ fontSize: '0.78rem', color: 'var(--text-muted)' }}>{job.department}</div>
                  </td>
                  <td>{job.location} {job.remote ? '(Remote)' : ''}</td>
                  <td>{formatSalary(job.salaryMin, job.salaryMax)}</td>
                  <td><strong>{job.applicantCount}</strong></td>
                  <td><StatusBadge status={job.status} /></td>
                  <td>
                    <div style={{ display: 'flex', gap: '0.35rem' }}>
                      {job.status !== 'OPEN' && (
                        <button className="btn btn-success btn-sm" onClick={() => toggleJobStatus(job, 'OPEN')}>Reopen</button>
                      )}
                      {job.status === 'OPEN' && (
                        <button className="btn btn-secondary btn-sm" onClick={() => toggleJobStatus(job, 'PAUSED')}>Pause</button>
                      )}
                      {job.status !== 'CLOSED' && (
                        <button className="btn btn-danger btn-sm" onClick={() => toggleJobStatus(job, 'CLOSED')}>Close</button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div className="card">
        <div className="card-title">
          <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Users size={17} /> Incoming Applicants (ATS Ranked)
          </span>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Candidate</th>
                <th>Position</th>
                <th>ATS Score</th>
                <th>Matched Skills</th>
                <th>Status</th>
                <th>Pipeline Action</th>
              </tr>
            </thead>
            <tbody>
              {applications.map((app) => (
                <tr key={app.id}>
                  <td>
                    <strong>{app.candidateName}</strong>
                    <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>{app.candidateEmail}</div>
                  </td>
                  <td>{app.jobTitle}</td>
                  <td>
                    <strong style={{ color: (app.atsMatchScore ?? 0) >= 75 ? '#86efac' : '#93c5fd' }}>
                      {app.atsMatchScore != null ? `${app.atsMatchScore}%` : 'Scoring...'}
                    </strong>
                  </td>
                  <td>
                    <div className="chip-row" style={{ marginTop: 0 }}>
                      {app.matchedSkills.slice(0, 4).map((s) => (
                        <span key={s} className="chip chip-match">{s}</span>
                      ))}
                    </div>
                  </td>
                  <td><StatusBadge status={app.status} /></td>
                  <td>
                    <select
                      style={{ width: 150, padding: '0.35rem 0.5rem', fontSize: '0.8rem' }}
                      value={app.status}
                      onChange={(e) => advanceApplication(app.id, e.target.value as ApplicationStatus)}
                    >
                      <option value="SUBMITTED">SUBMITTED</option>
                      <option value="SCREENING">SCREENING</option>
                      <option value="INTERVIEW">INTERVIEW</option>
                      <option value="OFFER">OFFER</option>
                      <option value="HIRED">HIRED</option>
                      <option value="REJECTED">REJECTED</option>
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {showModal && (
        <div className="modal-backdrop">
          <div className="modal-card">
            <h3 style={{ marginBottom: '1rem' }}>Create New Job Posting</h3>
            <form onSubmit={handleCreateJob}>
              <div className="form-group">
                <label>Job Title</label>
                <input required value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Senior Java Systems Engineer" />
              </div>
              <div className="grid-2" style={{ gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                <div className="form-group">
                  <label>Company</label>
                  <input required value={form.company} onChange={(e) => setForm({ ...form, company: e.target.value })} />
                </div>
                <div className="form-group">
                  <label>Location</label>
                  <input required value={form.location} onChange={(e) => setForm({ ...form, location: e.target.value })} />
                </div>
              </div>
              <div className="grid-2" style={{ gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
                <div className="form-group">
                  <label>Min Salary ($)</label>
                  <input type="number" value={form.salaryMin} onChange={(e) => setForm({ ...form, salaryMin: Number(e.target.value) })} />
                </div>
                <div className="form-group">
                  <label>Max Salary ($)</label>
                  <input type="number" value={form.salaryMax} onChange={(e) => setForm({ ...form, salaryMax: Number(e.target.value) })} />
                </div>
              </div>
              <div className="form-group">
                <label>Required Skills (comma-separated, used by ResumeProcessingWorker)</label>
                <input required value={form.requiredSkills} onChange={(e) => setForm({ ...form, requiredSkills: e.target.value })} />
              </div>
              <div className="form-group">
                <label>Role Description</label>
                <textarea rows={4} required value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
              </div>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem' }}>
                <button type="button" className="btn btn-secondary" onClick={() => setShowModal(false)}>Cancel</button>
                <button type="submit" className="btn btn-primary">Publish Job</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
