import React, { useEffect, useState } from 'react';
import { Activity, AlertTriangle, Cpu, Play, RefreshCw, RotateCcw, Zap } from 'lucide-react';
import { api } from '../api/client';
import { StatusBadge } from '../components/StatusBadge';
import type { BackgroundJob, BackgroundJobStatus, BackgroundJobType, QueueMetrics, SystemAnalytics } from '../types';

export const AdminDashboard: React.FC = () => {
  const [analytics, setAnalytics] = useState<SystemAnalytics | null>(null);
  const [metrics, setMetrics] = useState<QueueMetrics | null>(null);
  const [jobs, setJobs] = useState<BackgroundJob[]>([]);
  const [statusFilter, setStatusFilter] = useState<string>('ALL');
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [jobType, setJobType] = useState<BackgroundJobType>('SEND_EMAIL');
  const [priority, setPriority] = useState(8);
  const [simulateFailure, setSimulateFailure] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    const [a, m, j] = await Promise.all([
      api.getAnalytics(true),
      api.getQueueMetrics(),
      api.listQueueJobs(statusFilter === 'ALL' ? undefined : (statusFilter as BackgroundJobStatus), 50),
    ]);
    setAnalytics(a);
    setMetrics(m);
    setJobs(j);
  };

  useEffect(() => {
    loadData().catch((e) => setError(e.message));
  }, [statusFilter]);

  useEffect(() => {
    if (!autoRefresh) return;
    const id = setInterval(() => {
      loadData().catch(() => {});
    }, 3000);
    return () => clearInterval(id);
  }, [autoRefresh, statusFilter]);

  const handleTriggerJob = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const samplePayload =
      jobType === 'SEND_EMAIL'
        ? JSON.stringify({ to: 'ops-audit@jms.dev', subject: 'Manual Worker Dispatch Test', template: 'OPS_CHECK' })
        : jobType === 'REFRESH_ANALYTICS'
        ? JSON.stringify({ reason: 'admin_manual_trigger' })
        : JSON.stringify({ note: 'Manual dispatch test' });

    try {
      const created = await api.triggerJob({
        type: jobType,
        priority,
        payload: samplePayload,
        simulateFailure,
      });
      setFeedback(
        `Enqueued job #${created.id} (${created.type}, P${created.priority})${
          simulateFailure ? ' with failure simulation -> watch exponential backoff into DLQ!' : ''
        }`
      );
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to enqueue job');
    }
  };

  const handleReplay = async (jobId: number) => {
    setError(null);
    try {
      await api.replayJob(jobId);
      setFeedback(`Replayed DEAD_LETTER job #${jobId} back into priority queue.`);
      await loadData();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Replay failed');
    }
  };

  const handleFlush = async () => {
    await api.flushQueue();
    await loadData();
  };

  return (
    <div>
      <div className="page-header">
        <div>
          <h2>Admin Operations & Background Queue Monitor</h2>
          <p>Live observability for the concurrent Java WorkerPoolEngine, retry/DLQ pipelines, and hiring funnel metrics.</p>
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: '0.35rem', fontSize: '0.82rem', color: 'var(--text-muted)' }}>
            <input type="checkbox" style={{ width: 'auto' }} checked={autoRefresh} onChange={(e) => setAutoRefresh(e.target.checked)} />
            Auto-Poll (3s)
          </label>
          <button className="btn btn-secondary btn-sm" onClick={handleFlush}>
            <Zap size={14} /> Flush Dispatcher
          </button>
          <button className="btn btn-primary btn-sm" onClick={() => loadData()}>
            <RefreshCw size={14} /> Refresh
          </button>
        </div>
      </div>

      {error && <div className="alert-error">{error}</div>}
      {feedback && <div className="alert-success">{feedback}</div>}

      {/* Worker Pool Telemetry */}
      {metrics && (
        <div className="grid-4">
          <div className="card">
            <div className="stat-label">Worker Pool Active</div>
            <div className="stat-value">
              {metrics.activeWorkers} / {metrics.workerPoolSize}
            </div>
            <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>Avg exec: {metrics.averageExecutionMs} ms</div>
          </div>
          <div className="card">
            <div className="stat-label">Completed Jobs</div>
            <div className="stat-value" style={{ color: '#86efac' }}>{metrics.completedCount}</div>
            <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>Queued: {metrics.queuedCount} · Processing: {metrics.processingCount}</div>
          </div>
          <div className="card">
            <div className="stat-label">Retrying (Backoff)</div>
            <div className="stat-value" style={{ color: '#fcd34d' }}>{metrics.retryingCount}</div>
            <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>Exponential backoff active</div>
          </div>
          <div className="card">
            <div className="stat-label">Dead-Letter Queue (DLQ)</div>
            <div className="stat-value" style={{ color: metrics.deadLetterCount > 0 ? '#fca5a5' : 'var(--text)' }}>
              {metrics.deadLetterCount}
            </div>
            <div style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>Replayable from table below</div>
          </div>
        </div>
      )}

      <div className="grid-2" style={{ marginBottom: '1.5rem' }}>
        {/* Live Job Enqueue / Chaos Simulator */}
        <div className="card">
          <div className="card-title">
            <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <Cpu size={17} /> Dispatch Background Worker / Failure Simulator
            </span>
          </div>
          <form onSubmit={handleTriggerJob}>
            <div className="grid-2" style={{ gridTemplateColumns: '2fr 1fr', gap: '0.75rem' }}>
              <div className="form-group">
                <label>Worker Type</label>
                <select value={jobType} onChange={(e) => setJobType(e.target.value as BackgroundJobType)}>
                  <option value="SEND_EMAIL">EmailWorker (SEND_EMAIL)</option>
                  <option value="REFRESH_ANALYTICS">AnalyticsWorker (REFRESH_ANALYTICS)</option>
                </select>
              </div>
              <div className="form-group">
                <label>Priority (1–10)</label>
                <input type="number" min={1} max={10} value={priority} onChange={(e) => setPriority(Number(e.target.value))} />
              </div>
            </div>
            <div className="form-group">
              <label style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', cursor: 'pointer' }}>
                <input
                  type="checkbox"
                  style={{ width: 'auto' }}
                  checked={simulateFailure}
                  onChange={(e) => setSimulateFailure(e.target.checked)}
                />
                <AlertTriangle size={14} color="#fcd34d" /> Simulate transient failure (tests exponential backoff &rarr; Dead-Letter Queue)
              </label>
            </div>
            <button type="submit" className="btn btn-primary">
              <Play size={14} /> Enqueue Background Job
            </button>
          </form>
        </div>

        {/* Hiring Funnel Snapshot */}
        {analytics && (
          <div className="card">
            <div className="card-title">
              <span style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
                <Activity size={17} /> Platform Hiring Funnel Analytics
              </span>
              <span style={{ fontSize: '0.76rem', color: 'var(--text-muted)' }}>
                {analytics.totalUsers} Users · {analytics.openJobs}/{analytics.totalJobs} Jobs Open
              </span>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.6rem' }}>
              {Object.entries(analytics.applicationsByStatus).map(([stageName, count]) => {
                const pct = analytics.totalApplications > 0 ? Math.round((count / analytics.totalApplications) * 100) : 0;
                return (
                  <div key={stageName}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.8rem', marginBottom: '0.2rem' }}>
                      <span>{stageName}</span>
                      <strong>{count} ({pct}%)</strong>
                    </div>
                    <div className="progress-track">
                      <div className="progress-fill" style={{ width: `${pct}%`, background: 'var(--primary)' }} />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        )}
      </div>

      {/* Background Job Ledger */}
      <div className="card">
        <div className="card-title">
          <span>Background Job Queue Ledger</span>
          <select
            style={{ width: 180, padding: '0.35rem 0.6rem', fontSize: '0.82rem' }}
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="ALL">All Statuses</option>
            <option value="QUEUED">QUEUED</option>
            <option value="PROCESSING">PROCESSING</option>
            <option value="RETRYING">RETRYING</option>
            <option value="COMPLETED">COMPLETED</option>
            <option value="DEAD_LETTER">DEAD_LETTER (DLQ)</option>
          </select>
        </div>

        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Worker</th>
                <th>Priority</th>
                <th>Status</th>
                <th>Attempts</th>
                <th>Latency</th>
                <th>Result / Error</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {jobs.map((job) => (
                <tr key={job.id}>
                  <td>#{job.id}</td>
                  <td><code>{job.type}</code></td>
                  <td><span className="badge badge-purple">P{job.priority}</span></td>
                  <td><StatusBadge status={job.status} /></td>
                  <td>{job.attempts} / {job.maxAttempts}</td>
                  <td>{job.executionDurationMs != null ? `${job.executionDurationMs} ms` : '—'}</td>
                  <td style={{ maxWidth: 340, fontSize: '0.8rem', color: job.lastError ? '#fca5a5' : 'var(--text-muted)' }}>
                    {job.lastError || job.resultSummary || 'Scheduled'}
                  </td>
                  <td>
                    {job.status === 'DEAD_LETTER' && (
                      <button className="btn btn-success btn-sm" onClick={() => handleReplay(job.id)}>
                        <RotateCcw size={13} /> Replay DLQ
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
